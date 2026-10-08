package com.freshquiz.game;

import static com.freshquiz.game.GameRepository.toTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.freshquiz.game.GameRepository.ChoiceRow;
import com.freshquiz.game.GameRepository.GameRow;
import com.freshquiz.game.GameRepository.PlayerRow;
import com.freshquiz.game.GameRepository.QuestionRow;
import com.freshquiz.game.model.Choice;
import com.freshquiz.game.model.CurrentQuestion;
import com.freshquiz.game.model.GameSession;
import com.freshquiz.game.model.GameState;
import com.freshquiz.game.model.GameStatus;
import com.freshquiz.game.model.PlayerSession;
import com.freshquiz.game.model.Role;
import com.freshquiz.game.model.Viewer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameService {

	private final GameRepository repository;
	private final RevealScheduler reveals;
	private final JdbcClient jdbc;

	public GameService(GameRepository repository, RevealScheduler reveals, JdbcClient jdbc) {
		this.repository = repository;
		this.reveals = reveals;
		this.jdbc = jdbc;
	}

	public GameSession createGame(String nickname) {
		String quizId = repository.firstQuizId()
				.orElseThrow(() -> new GameException("Nenhum quiz disponível."));
		String cleanNickname = normalizeNickname(nickname);

		for (int attempt = 0; attempt < 5; attempt++) {
			String code = GameRepository.createCode();
			String hostToken = GameRepository.createToken();
			try {
				jdbc.sql("""
						INSERT INTO games (id, quiz_id, code, host_token, host_nickname, status)
						VALUES (:id, :quizId, :code, :hostToken, :hostNickname, 'lobby')
						""")
						.param("id", GameRepository.createToken())
						.param("quizId", quizId)
						.param("code", code)
						.param("hostToken", hostToken)
						.param("hostNickname", cleanNickname)
						.update();
				return new GameSession(code, hostToken);
			}
			catch (DuplicateKeyException collision) {
				// Código já usado por outra sala: tenta outro.
			}
		}
		throw new GameException("Não foi possível gerar o código da sala.");
	}

	private static String normalizeNickname(String nickname) {
		String clean = nickname == null ? "" : nickname.strip();
		if (clean.length() > 24) clean = clean.substring(0, 24);
		if (clean.length() < 2) throw new GameException("Informe um apelido válido.");
		return clean;
	}

	public boolean verifyHost(String code, String token) {
		return token != null && repository.getGame(code)
				.map(game -> game.hostToken().equals(token))
				.orElse(false);
	}

	@Transactional
	public PlayerSession joinGame(String code, String nickname) {
		GameRow game = requireGame(code);
		if (game.status() != GameStatus.LOBBY) throw new GameException("A partida já começou.");
		String cleanNickname = normalizeNickname(nickname);

		String playerToken = GameRepository.createToken();
		String playerId = GameRepository.createToken();
		jdbc.sql("""
				INSERT INTO players (id, nickname, player_token)
				VALUES (:id, :nickname, :playerToken)
				""")
				.param("id", playerId)
				.param("nickname", cleanNickname)
				.param("playerToken", playerToken)
				.update();
		jdbc.sql("INSERT INTO game_players (game_id, player_id) VALUES (:gameId, :playerId)")
				.param("gameId", game.id())
				.param("playerId", playerId)
				.update();

		return new PlayerSession(game.code(), playerId, playerToken, cleanNickname);
	}

	public boolean verifyPlayer(String code, String playerToken) {
		return findPlayer(code, playerToken).isPresent();
	}

	public Optional<PlayerRow> findPlayer(String code, String playerToken) {
		if (playerToken == null) return Optional.empty();
		return repository.getGame(code)
				.flatMap(game -> repository.getPlayerByToken(game.id(), playerToken));
	}

	public Optional<String> hostNickname(String code) {
		return repository.getGame(code).map(GameRow::hostNickname);
	}

	public Optional<GameState> getState(String code, Viewer viewer) {
		Optional<GameRow> found = repository.getGame(code);
		if (found.isEmpty()) return Optional.empty();
		GameRow game = found.get();

		if (game.status() == GameStatus.QUESTION && game.questionDeadlineAt() != null) {
			reveals.ensureScheduled(game.id(), game.code(), game.questionDeadlineAt());
		}

		CurrentQuestion currentQuestion = null;
		if (game.currentQuestionPosition() > 0) {
			QuestionRow question = repository.getQuestion(game.quizId(), game.currentQuestionPosition())
					.orElse(null);
			if (question != null) {
				List<ChoiceRow> choices = repository.getQuestionChoices(question.id());
				boolean isRevealed = game.status() == GameStatus.REVEAL || game.status() == GameStatus.FINISHED;
				Map<String, Integer> answerCounts = isRevealed
						? repository.getAnswerCounts(game.id(), question.id())
						: maybeAnswerCounts(game, question.id(), viewer);
				String correctChoiceId = isRevealed
						? choices.stream().filter(ChoiceRow::isCorrect).map(ChoiceRow::id).findFirst().orElse(null)
						: null;
				currentQuestion = new CurrentQuestion(
						question.id(),
						question.position(),
						question.prompt(),
						question.durationSeconds(),
						choices.stream().map(c -> new Choice(c.id(), c.label(), c.position())).toList(),
						correctChoiceId,
						answerCounts);
			}
		}

		return Optional.of(new GameState(
				game.code(),
				game.status(),
				game.hostNickname(),
				currentQuestion,
				game.currentQuestionPosition(),
				repository.countQuestions(game.quizId()),
				game.questionDeadlineAt(),
				repository.listPlayers(game)));
	}

	/** O host vê a votação ao vivo; o jogador só depois de responder. */
	private Map<String, Integer> maybeAnswerCounts(GameRow game, String questionId, Viewer viewer) {
		if (viewer.role() == Role.HOST) return repository.getAnswerCounts(game.id(), questionId);
		if (viewer.playerToken() == null) return null;
		Optional<PlayerRow> player = repository.getPlayerByToken(game.id(), viewer.playerToken());
		if (player.isEmpty() || !repository.hasAnsweredQuestion(game.id(), questionId, player.get().id())) {
			return null;
		}
		return repository.getAnswerCounts(game.id(), questionId);
	}

	public void startGame(String code) {
		GameRow game = repository.getGame(code)
				.filter(g -> g.status() == GameStatus.LOBBY)
				.orElseThrow(() -> new GameException("A partida não pode começar."));
		QuestionRow question = repository.getQuestion(game.quizId(), 1)
				.orElseThrow(() -> new GameException("O quiz não possui perguntas."));

		Instant startedAt = Instant.now();
		Instant deadlineAt = startedAt.plusSeconds(question.durationSeconds());
		int updated = jdbc.sql("""
				UPDATE games
				SET status = 'question', current_question_position = 1,
				  question_started_at = :startedAt, question_deadline_at = :deadlineAt
				WHERE id = :gameId AND status = 'lobby'
				""")
				.param("startedAt", toTimestamp(startedAt))
				.param("deadlineAt", toTimestamp(deadlineAt))
				.param("gameId", game.id())
				.update();
		if (updated != 1) throw new GameException("A partida não pode começar.");
		reveals.schedule(game.id(), game.code(), deadlineAt);
	}

	public int submitAnswer(String code, String playerToken, String choiceId) {
		GameRow game = repository.getGame(code)
				.filter(g -> g.status() == GameStatus.QUESTION)
				.orElseThrow(() -> new GameException("A pergunta não está aberta."));
		if (game.questionStartedAt() == null || game.questionDeadlineAt() == null) {
			throw new GameException("A pergunta não possui prazo válido.");
		}
		Instant now = Instant.now();
		if (Scoring.isDeadlineExpired(game.questionDeadlineAt(), now)) {
			throw new GameException("O tempo acabou.");
		}

		QuestionRow question = repository.getQuestion(game.quizId(), game.currentQuestionPosition())
				.orElseThrow(() -> new GameException("Pergunta não encontrada."));
		PlayerRow player = repository.getPlayerByToken(game.id(), playerToken)
				.orElseThrow(() -> new GameException("Jogador não encontrado."));
		ChoiceRow choice = repository.getQuestionChoices(question.id()).stream()
				.filter(c -> c.id().equals(choiceId))
				.findFirst()
				.orElseThrow(() -> new GameException("Alternativa inválida."));

		int points = choice.isCorrect()
				? Scoring.calculatePoints(question.durationSeconds(), game.questionStartedAt(), now)
				: 0;
		int inserted = jdbc.sql("""
				INSERT INTO answers (game_id, question_id, player_id, choice_id, is_correct, points, answered_at)
				VALUES (:gameId, :questionId, :playerId, :choiceId, :isCorrect, :points, :answeredAt)
				ON CONFLICT (game_id, question_id, player_id) DO NOTHING
				""")
				.param("gameId", game.id())
				.param("questionId", question.id())
				.param("playerId", player.id())
				.param("choiceId", choice.id())
				.param("isCorrect", choice.isCorrect())
				.param("points", points)
				.param("answeredAt", toTimestamp(now))
				.update();
		if (inserted != 1) throw new GameException("Você já respondeu esta pergunta.");

		return points;
	}

	/** Revela antes do prazo, somando os pontos de quem já respondeu. */
	public void skipQuestion(String code) {
		GameRow game = requireGame(code);
		if (game.status() != GameStatus.QUESTION) {
			throw new GameException("Não há pergunta em andamento para pular.");
		}
		reveals.revealCurrentQuestion(code);
		jdbc.sql("UPDATE games SET question_deadline_at = NULL WHERE id = :gameId AND status = 'reveal'")
				.param("gameId", game.id())
				.update();
	}

	public void advanceGame(String code) {
		GameRow game = requireGame(code);
		if (game.status() == GameStatus.QUESTION) {
			throw new GameException("Aguarde o tempo da pergunta terminar.");
		}
		if (game.status() != GameStatus.REVEAL) throw new GameException("A partida não pode avançar.");

		int nextPosition = game.currentQuestionPosition() + 1;
		Optional<QuestionRow> nextQuestion = repository.getQuestion(game.quizId(), nextPosition);
		if (nextQuestion.isEmpty()) {
			reveals.cancel(game.id());
			jdbc.sql("UPDATE games SET status = 'finished', question_deadline_at = NULL WHERE id = :gameId")
					.param("gameId", game.id())
					.update();
			return;
		}

		Instant startedAt = Instant.now();
		Instant deadlineAt = startedAt.plusSeconds(nextQuestion.get().durationSeconds());
		int updated = jdbc.sql("""
				UPDATE games
				SET status = 'question', current_question_position = :position,
				  question_started_at = :startedAt, question_deadline_at = :deadlineAt
				WHERE id = :gameId AND status = 'reveal'
				""")
				.param("position", nextPosition)
				.param("startedAt", toTimestamp(startedAt))
				.param("deadlineAt", toTimestamp(deadlineAt))
				.param("gameId", game.id())
				.update();
		if (updated != 1) throw new GameException("A partida não pode avançar.");
		reveals.schedule(game.id(), game.code(), deadlineAt);
	}

	public void finishGame(String code) {
		GameRow game = requireGame(code);
		if (game.status() != GameStatus.QUESTION && game.status() != GameStatus.REVEAL) {
			throw new GameException("O quiz não está em andamento.");
		}
		reveals.cancel(game.id());
		jdbc.sql("""
				UPDATE games
				SET status = 'finished', question_deadline_at = NULL
				WHERE id = :gameId AND status IN ('question', 'reveal')
				""")
				.param("gameId", game.id())
				.update();
	}

	@Transactional
	public void restartGame(String code) {
		GameRow game = requireGame(code);
		if (game.status() != GameStatus.FINISHED) {
			throw new GameException("A partida ainda não terminou.");
		}
		reveals.cancel(game.id());
		jdbc.sql("DELETE FROM answers WHERE game_id = :gameId").param("gameId", game.id()).update();
		jdbc.sql("UPDATE game_players SET score = 0 WHERE game_id = :gameId").param("gameId", game.id()).update();
		int updated = jdbc.sql("""
				UPDATE games
				SET status = 'lobby', current_question_position = 0,
				  question_started_at = NULL, question_deadline_at = NULL
				WHERE id = :gameId AND status = 'finished'
				""")
				.param("gameId", game.id())
				.update();
		if (updated != 1) throw new GameException("A partida não pode reiniciar.");
	}

	private GameRow requireGame(String code) {
		return repository.getGame(code).orElseThrow(() -> new GameException("Sala não encontrada."));
	}
}
