package com.freshquiz.game;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import com.freshquiz.events.GameChangedEvent;
import com.freshquiz.game.GameRepository.GameRow;
import com.freshquiz.game.model.GameStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Revela a resposta automaticamente quando o tempo da pergunta acaba.
 * Os timers vivem em memória: um reinício do servidor os perde, e {@link GameService#getState}
 * reagenda ao encontrar uma pergunta aberta.
 */
@Component
public class RevealScheduler {

	private static final Logger log = LoggerFactory.getLogger(RevealScheduler.class);

	private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
	private final TaskScheduler scheduler;
	private final GameRepository repository;
	private final JdbcClient jdbc;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;

	public RevealScheduler(TaskScheduler scheduler, GameRepository repository, JdbcClient jdbc,
			TransactionTemplate transaction, ApplicationEventPublisher events) {
		this.scheduler = scheduler;
		this.repository = repository;
		this.jdbc = jdbc;
		this.transaction = transaction;
		this.events = events;
	}

	public void schedule(String gameId, String code, Instant deadlineAt) {
		ScheduledFuture<?> timer = scheduler.schedule(() -> {
			timers.remove(gameId);
			try {
				revealCurrentQuestion(code);
				events.publishEvent(new GameChangedEvent(code));
			}
			catch (RuntimeException error) {
				// A partida pode ter mudado entre o agendamento e a execução.
				log.debug("Reveal da sala {} ignorado", code, error);
			}
		}, deadlineAt);
		ScheduledFuture<?> previous = timers.put(gameId, timer);
		if (previous != null) previous.cancel(false);
	}

	/** Agenda só se ainda não houver timer para a partida (usado ao ler o estado). */
	public void ensureScheduled(String gameId, String code, Instant deadlineAt) {
		if (!timers.containsKey(gameId)) schedule(gameId, code, deadlineAt);
	}

	public void cancel(String gameId) {
		ScheduledFuture<?> previous = timers.remove(gameId);
		if (previous != null) previous.cancel(false);
	}

	public void cancelPending(String code) {
		repository.getGame(code).ifPresent(game -> cancel(game.id()));
	}

	public void revealCurrentQuestion(String code) {
		GameRow game = repository.getGame(code).orElse(null);
		if (game == null || game.status() != GameStatus.QUESTION) return;
		cancel(game.id());
		transaction.executeWithoutResult(status -> {
			int updated = jdbc.sql("""
					UPDATE games
					SET status = 'reveal'
					WHERE id = :gameId AND status = 'question'
					""")
					.param("gameId", game.id())
					.update();
			if (updated != 1) return;
			jdbc.sql("""
					UPDATE game_players gp
					SET score = gp.score + COALESCE((
					  SELECT SUM(a.points)
					  FROM answers a
					  WHERE a.game_id = :gameId
					    AND a.player_id = gp.player_id
					    AND a.question_id = (
					      SELECT id
					      FROM questions
					      WHERE quiz_id = :quizId
					        AND position = :position
					    )
					), 0)
					WHERE gp.game_id = :gameId
					""")
					.param("gameId", game.id())
					.param("quizId", game.quizId())
					.param("position", game.currentQuestionPosition())
					.update();
		});
	}
}
