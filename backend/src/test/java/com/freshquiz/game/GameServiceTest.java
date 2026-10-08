package com.freshquiz.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.freshquiz.TestcontainersConfiguration;
import com.freshquiz.game.GameRepository.ChoiceRow;
import com.freshquiz.game.model.GameSession;
import com.freshquiz.game.model.GameState;
import com.freshquiz.game.model.GameStatus;
import com.freshquiz.game.model.PlayerSession;
import com.freshquiz.game.model.PlayerSummary;
import com.freshquiz.game.model.Role;
import com.freshquiz.game.model.Viewer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GameServiceTest {

	@Autowired
	GameService games;

	@Autowired
	GameRepository repository;

	@Autowired
	RevealScheduler reveals;

	@Autowired
	JdbcClient jdbc;

	private static final Viewer HOST = new Viewer(Role.HOST, null);

	private static Viewer player(PlayerSession session) {
		return new Viewer(Role.PLAYER, session.playerToken());
	}

	private GameState state(String code, Viewer viewer) {
		return games.getState(code, viewer).orElseThrow();
	}

	private ChoiceRow choice(String code, boolean correct) {
		GameRepository.GameRow game = repository.getGame(code).orElseThrow();
		String questionId = repository.getQuestion(game.quizId(), game.currentQuestionPosition()).orElseThrow().id();
		return repository.getQuestionChoices(questionId).stream()
				.filter(c -> c.isCorrect() == correct)
				.findFirst()
				.orElseThrow();
	}

	private void expireDeadline(String code) {
		jdbc.sql("UPDATE games SET question_deadline_at = now() - interval '1 second' WHERE code = :code")
				.param("code", code)
				.update();
	}

	@Test
	void fullGameLifecycle() {
		GameSession session = games.createGame("  Host Test  ");
		String code = session.code();
		assertThat(code).matches("[A-HJ-NP-Z2-9]{6}");
		assertThat(games.verifyHost(code, session.hostToken())).isTrue();

		PlayerSession ana = games.joinGame(code, "Ana");
		PlayerSession bia = games.joinGame(code, "Bia");
		GameState lobby = state(code, HOST);
		assertThat(lobby.status()).isEqualTo(GameStatus.LOBBY);
		assertThat(lobby.hostNickname()).isEqualTo("Host Test");
		assertThat(lobby.players()).extracting(PlayerSummary::nickname).containsExactly("Ana", "Bia");
		assertThat(lobby.totalQuestions()).isEqualTo(16);

		games.startGame(code);
		GameState question = state(code, player(ana));
		assertThat(question.status()).isEqualTo(GameStatus.QUESTION);
		assertThat(question.currentQuestion().correctChoiceId()).isNull();
		assertThat(question.currentQuestion().answerCounts()).isNull();
		assertThat(question.deadlineAt()).isAfter(Instant.now());

		int points = games.submitAnswer(code, ana.playerToken(), choice(code, true).id());
		assertThat(points).isBetween(1000, 1500);
		assertThat(games.submitAnswer(code, bia.playerToken(), choice(code, false).id())).isZero();

		// Host vê a votação ao vivo; jogador que respondeu também.
		assertThat(state(code, HOST).currentQuestion().answerCounts()).hasSize(2);
		assertThat(state(code, player(ana)).currentQuestion().answerCounts()).isNotNull();
		assertThat(state(code, HOST).players()).allMatch(PlayerSummary::hasAnswered);

		assertThatThrownBy(() -> games.advanceGame(code))
				.hasMessage("Aguarde o tempo da pergunta terminar.");

		reveals.revealCurrentQuestion(code);
		GameState revealed = state(code, player(bia));
		assertThat(revealed.status()).isEqualTo(GameStatus.REVEAL);
		assertThat(revealed.currentQuestion().correctChoiceId()).isEqualTo(choice(code, true).id());
		assertThat(revealed.players().getFirst().nickname()).isEqualTo("Ana");
		assertThat(revealed.players().getFirst().score()).isEqualTo(points);

		games.advanceGame(code);
		assertThat(state(code, HOST).currentQuestionPosition()).isEqualTo(2);

		games.finishGame(code);
		assertThat(state(code, HOST).status()).isEqualTo(GameStatus.FINISHED);

		games.restartGame(code);
		GameState restarted = state(code, HOST);
		assertThat(restarted.status()).isEqualTo(GameStatus.LOBBY);
		assertThat(restarted.currentQuestion()).isNull();
		assertThat(restarted.players()).hasSize(2).allMatch(p -> p.score() == 0);
	}

	@Test
	void rejectsDuplicateAndLateAnswers() {
		String code = games.createGame("Host").code();
		PlayerSession ana = games.joinGame(code, "Ana");
		PlayerSession bia = games.joinGame(code, "Bia");
		games.startGame(code);

		String correct = choice(code, true).id();
		games.submitAnswer(code, ana.playerToken(), correct);
		assertThatThrownBy(() -> games.submitAnswer(code, ana.playerToken(), correct))
				.hasMessage("Você já respondeu esta pergunta.");

		expireDeadline(code);
		assertThatThrownBy(() -> games.submitAnswer(code, bia.playerToken(), correct))
				.hasMessage("O tempo acabou.");
		reveals.cancelPending(code);
	}

	@Test
	void skipRevealsAndKeepsPointsOfWhoAnswered() {
		String code = games.createGame("Host").code();
		PlayerSession ana = games.joinGame(code, "Ana");
		games.startGame(code);
		int points = games.submitAnswer(code, ana.playerToken(), choice(code, true).id());

		games.skipQuestion(code);
		GameState state = state(code, HOST);
		assertThat(state.status()).isEqualTo(GameStatus.REVEAL);
		assertThat(state.deadlineAt()).isNull();
		assertThat(state.players().getFirst().score()).isEqualTo(points);
	}

	@Test
	void validatesNicknameAndLobby() {
		assertThatThrownBy(() -> games.createGame(" a ")).hasMessage("Informe um apelido válido.");
		String code = games.createGame("Host").code();
		assertThat(games.joinGame(code, "x".repeat(40)).nickname()).hasSize(24);
		games.startGame(code);
		assertThatThrownBy(() -> games.joinGame(code, "Tarde")).hasMessage("A partida já começou.");
		assertThatThrownBy(() -> games.joinGame("ZZZZZZ", "Ana")).hasMessage("Sala não encontrada.");
		reveals.cancelPending(code);
	}

	@Test
	void timerRevealsQuestionAutomatically() throws InterruptedException {
		String code = games.createGame("Host").code();
		games.joinGame(code, "Ana");
		games.startGame(code);
		GameRepository.GameRow game = repository.getGame(code).orElseThrow();
		reveals.schedule(game.id(), code, Instant.now().plusMillis(200));

		Thread.sleep(1000);
		assertThat(state(code, HOST).status()).isEqualTo(GameStatus.REVEAL);
	}
}
