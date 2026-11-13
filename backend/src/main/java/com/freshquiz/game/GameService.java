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
import com.freshquiz.model.Game;
import com.freshquiz.model.GamePlayer;
import com.freshquiz.model.Player;
import com.freshquiz.model.Quiz;
import com.freshquiz.quiz.QuizJpaRepository;
import com.freshquiz.quiz.QuizRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameService {

	private final GameRepository repository;
	private final QuizRepository quizzes;
	private final RevealScheduler reveals;
	private final JdbcClient jdbc;
	private final GameJpaRepository gameJpaRepository;
	private final PlayerJpaRepository playerJpaRepository;
	private final GamePlayerJpaRepository gamePlayerJpaRepository;
	private final QuizJpaRepository quizJpaRepository;

	public GameService(GameRepository repository, QuizRepository quizzes, RevealScheduler reveals,
			JdbcClient jdbc, GameJpaRepository gameJpaRepository, PlayerJpaRepository playerJpaRepository,
			GamePlayerJpaRepository gamePlayerJpaRepository, QuizJpaRepository quizJpaRepository) {
		this.repository = repository;
		this.quizzes = quizzes;
		this.reveals = reveals;
		this.jdbc = jdbc;
		this.gameJpaRepository = gameJpaRepository;
		this.playerJpaRepository = playerJpaRepository;
		this.gamePlayerJpaRepository = gamePlayerJpaRepository;
		this.quizJpaRepository = quizJpaRepository;
	}

	public GameSession createGame(String nickname) {
		return createGame(nickname, null);
	}

	public GameSession createGame(String nickname, String requestedQuizId) {
		String quizId = resolveQuizId(requestedQuizId);
		String cleanNickname = normalizeNickname(nickname);

		for (int attempt = 0; attempt < 5; attempt++) {
			String code = GameRepository.createCode();
			String hostToken = GameRepository.createToken();
			try {
				Quiz quiz = quizJpaRepository.findById(quizId)
						.orElseThrow(() -> new GameException("Quiz não encontrado."));
				Game game = new Game(GameRepository.createToken(), quiz, code, hostToken, cleanNickname);
				gameJpaRepository.save(game);
				return new GameSession(code, hostToken);
			}
			catch (DataIntegrityViolationException collision) {
				// Código já usado por outra sala: tenta outro.
			}
		}
		throw new GameException("Não foi possível gerar o código da sala.");
	}

	private String resolveQuizId(String requestedQuizId) {
		if (requestedQuizId == null || requestedQuizId.isBlank()) {
			return repository.firstQuizId()
					.orElseThrow(() -> new GameException("Nenhum quiz disponível."));
		}
		String quizId = requestedQuizId.strip();
		if (!quizzes.exists(quizId)) throw new GameException("Quiz não encontrado.");
		return quizId;
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
		Game game = gameJpaRepository.findByCode(code)
				.orElseThrow(() -> new GameException("Sala não encontrada."));
		String cleanNickname = normalizeNickname(nickname);

		// Se o jogador com o mesmo apelido já estiver na sala, reutiliza a sessão dele
		Optional<GamePlayer> existing = gamePlayerJpaRepository
				.findByGameIdAndPlayerNicknameIgnoreCase(game.getId(), cleanNickname);
		if (existing.isPresent()) {
			Player existingPlayer = existing.get().getPlayer();
			String newToken = GameRepository.createToken();
			existingPlayer.setPlayerToken(newToken);
			playerJpaRepository.save(existingPlayer);
			return new PlayerSession(game.getCode(), existingPlayer.getId(), newToken, existingPlayer.getNickname());
		}

		if (game.getStatus() != GameStatus.LOBBY) throw new GameException("A partida já começou.");

		String playerToken = GameRepository.createToken();
		String playerId = GameRepository.createToken();
		Player player = new Player(playerId, cleanNickname, playerToken, Instant.now());
		playerJpaRepository.save(player);

		GamePlayer gamePlayer = new GamePlayer(game, player);
		gamePlayerJpaRepository.save(gamePlayer);

		return new PlayerSession(game.getCode(), playerId, playerToken, cleanNickname);
	}

	@Transactional
	public void removePlayerIfLobby(String code, String playerToken) {
		if (playerToken == null) return;
		Game game = gameJpaRepository.findByCode(code).orElse(null);
		if (game == null || game.getStatus() != GameStatus.LOBBY) return;
		Player player = playerJpaRepository.findByPlayerToken(playerToken).orElse(null);
		if (player == null) return;
		gamePlayerJpaRepository.findByGameIdAndPlayerId(game.getId(), player.getId())
				.ifPresent(gamePlayerJpaRepository::delete);
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
		Game game = gameJpaRepository.findByCode(code)
				.filter(g -> g.getStatus() == GameStatus.LOBBY)
				.orElseThrow(() -> new GameException("A partida não pode começar."));
		QuestionRow question = repository.getQuestion(game.getQuiz().getId(), 1)
				.orElseThrow(() -> new GameException("O quiz não possui perguntas."));

		game.start();
		Instant deadlineAt = game.getQuestionStartedAt().plusSeconds(question.durationSeconds());
		game.setQuestionDeadlineAt(deadlineAt);
		gameJpaRepository.save(game);
		reveals.schedule(game.getId(), game.getCode(), deadlineAt);
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
		Game game = gameJpaRepository.findByCode(code)
				.orElseThrow(() -> new GameException("Sala não encontrada."));
		if (game.getStatus() != GameStatus.QUESTION) {
			throw new GameException("Não há pergunta em andamento para pular.");
		}
		reveals.revealCurrentQuestion(code);
		game.skipQuestion();
		game.setQuestionDeadlineAt(null);
		gameJpaRepository.save(game);
	}

	public void advanceGame(String code) {
		Game game = gameJpaRepository.findByCode(code)
				.orElseThrow(() -> new GameException("Sala não encontrada."));
		if (game.getStatus() == GameStatus.QUESTION) {
			throw new GameException("Aguarde o tempo da pergunta terminar.");
		}
		if (game.getStatus() != GameStatus.REVEAL) throw new GameException("A partida não pode avançar.");

		int nextPosition = game.getCurrentQuestionPosition() + 1;
		Optional<QuestionRow> nextQuestion = repository.getQuestion(game.getQuiz().getId(), nextPosition);
		if (nextQuestion.isEmpty()) {
			reveals.cancel(game.getId());
			game.finish();
			game.setQuestionDeadlineAt(null);
			gameJpaRepository.save(game);
			return;
		}

		game.nextQuestion();
		Instant deadlineAt = game.getQuestionStartedAt().plusSeconds(nextQuestion.get().durationSeconds());
		game.setQuestionDeadlineAt(deadlineAt);
		gameJpaRepository.save(game);
		reveals.schedule(game.getId(), game.getCode(), deadlineAt);
	}

	public void finishGame(String code) {
		Game game = gameJpaRepository.findByCode(code)
				.orElseThrow(() -> new GameException("Sala não encontrada."));
		if (game.getStatus() != GameStatus.QUESTION && game.getStatus() != GameStatus.REVEAL) {
			throw new GameException("O quiz não está em andamento.");
		}
		reveals.cancel(game.getId());
		game.finish();
		game.setQuestionDeadlineAt(null);
		gameJpaRepository.save(game);
	}

	public void closeGame(String code) {
		Game game = gameJpaRepository.findByCode(code).orElse(null);
		if (game != null) {
			reveals.cancel(game.getId());
			game.finish();
			game.setQuestionDeadlineAt(null);
			gameJpaRepository.save(game);
		}
	}

	@Transactional
	public void restartGame(String code) {
		Game game = gameJpaRepository.findByCode(code)
				.orElseThrow(() -> new GameException("Sala não encontrada."));
		if (game.getStatus() != GameStatus.FINISHED) {
			throw new GameException("A partida ainda não terminou.");
		}
		reveals.cancel(game.getId());
		jdbc.sql("DELETE FROM answers WHERE game_id = :gameId").param("gameId", game.getId()).update();
		List<GamePlayer> players = gamePlayerJpaRepository.findByGameIdOrderByScoreDescJoinedAtAsc(game.getId());
		for (GamePlayer gp : players) {
			gp.resetScore();
		}
		gamePlayerJpaRepository.saveAll(players);
		game.restart();
		gameJpaRepository.save(game);
	}

	private GameRow requireGame(String code) {
		return repository.getGame(code).orElseThrow(() -> new GameException("Sala não encontrada."));
	}
}
