package com.freshquiz.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.freshquiz.game.model.GameStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelEntitiesTest {

	@Test
	@DisplayName("Quiz e Question: testa composicao, métodos de dominio e operacoes do Astah")
	void testQuizAndQuestionOperations() {
		Quiz quiz = new Quiz(UUID.randomUUID().toString(), "Titulo Original", null, Instant.now());
		assertThat(quiz.getTitle()).isEqualTo("Titulo Original");
		assertThat(quiz.canDelete()).isTrue();

		quiz.updateTitle("Novo Titulo");
		assertThat(quiz.getTitle()).isEqualTo("Novo Titulo");

		String rawToken = "segredo123";
		quiz.setOwnerTokenHash(Quiz.class.getName()); // temporary
		// Use real hash
		java.security.MessageDigest md;
		try {
			md = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			quiz.setOwnerTokenHash(java.util.HexFormat.of().formatHex(hash));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		assertThat(quiz.isOwner(rawToken)).isTrue();
		assertThat(quiz.isOwner("outro")).isFalse();

		Question q1 = quiz.addQuestion("Pergunta 1?", 20);
		assertThat(quiz.getQuestions()).hasSize(1);
		assertThat(q1.getPrompt()).isEqualTo("Pergunta 1?");
		assertThat(q1.getDurationSeconds()).isEqualTo(20);
		assertThat(q1.getPosition()).isEqualTo(1);

		q1.update("Pergunta 1 Atualizada?", 30);
		assertThat(q1.getPrompt()).isEqualTo("Pergunta 1 Atualizada?");
		assertThat(q1.getDurationSeconds()).isEqualTo(30);

		Choice c1 = q1.addChoice("Alternativa A", true);
		Choice c2 = q1.addChoice("Alternativa B", false);
		assertThat(q1.getChoices()).hasSize(2);
		assertThat(c1.isCorrect()).isTrue();
		assertThat(c2.isCorrect()).isFalse();

		c2.update("Alternativa B Modificada", false);
		assertThat(c2.getLabel()).isEqualTo("Alternativa B Modificada");

		q1.setCorrectChoice(c2.getId());
		assertThat(c1.isCorrect()).isFalse();
		assertThat(c2.isCorrect()).isTrue();

		q1.removeChoice(c1.getId());
		assertThat(q1.getChoices()).hasSize(1);
		assertThat(q1.getChoices().get(0).getPosition()).isEqualTo(1);

		quiz.removeQuestion(q1.getId());
		assertThat(quiz.getQuestions()).isEmpty();
	}

	@Test
	@DisplayName("Game e GamePlayer: testa ciclo de vida e pontuacao conforme Astah")
	void testGameAndGamePlayerLifecycle() {
		Quiz quiz = new Quiz(UUID.randomUUID().toString(), "Quiz Teste", null, Instant.now());
		Game game = new Game(UUID.randomUUID().toString(), quiz, "ABC123", "tokenHost", "HostNickname");

		assertThat(game.getStatus()).isEqualTo(GameStatus.LOBBY);
		assertThat(game.getCurrentQuestionPosition()).isEqualTo(0);

		game.start();
		assertThat(game.getStatus()).isEqualTo(GameStatus.QUESTION);
		assertThat(game.getCurrentQuestionPosition()).isEqualTo(1);
		assertThat(game.getQuestionStartedAt()).isNotNull();

		game.nextQuestion();
		assertThat(game.getCurrentQuestionPosition()).isEqualTo(2);

		game.skipQuestion();
		assertThat(game.getStatus()).isEqualTo(GameStatus.REVEAL);

		game.finish();
		assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);

		game.restart();
		assertThat(game.getStatus()).isEqualTo(GameStatus.LOBBY);
		assertThat(game.getCurrentQuestionPosition()).isEqualTo(0);

		Player player = new Player(UUID.randomUUID().toString(), "Jogador1", "pToken", Instant.now());
		GamePlayer gamePlayer = new GamePlayer(game, player);
		assertThat(gamePlayer.getScore()).isEqualTo(0);

		gamePlayer.addScore(100);
		assertThat(gamePlayer.getScore()).isEqualTo(100);
		gamePlayer.addScore(50);
		assertThat(gamePlayer.getScore()).isEqualTo(150);

		gamePlayer.resetScore();
		assertThat(gamePlayer.getScore()).isEqualTo(0);
	}
}
