package com.freshquiz.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.freshquiz.TestcontainersConfiguration;
import com.freshquiz.game.GameService;
import com.freshquiz.game.model.GameSession;
import com.freshquiz.game.model.GameState;
import com.freshquiz.game.model.Role;
import com.freshquiz.game.model.Viewer;
import com.freshquiz.quiz.model.ChoiceDraft;
import com.freshquiz.quiz.model.QuestionDraft;
import com.freshquiz.quiz.model.QuizCreated;
import com.freshquiz.quiz.model.QuizDetails;
import com.freshquiz.quiz.model.QuizDraft;
import com.freshquiz.quiz.model.QuizSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QuizServiceTest {

	private static final Viewer HOST = new Viewer(Role.HOST, null);

	@Autowired
	QuizService quizzes;

	@Autowired
	GameService games;

	private static ChoiceDraft choice(String label, boolean correct) {
		return new ChoiceDraft(label, correct);
	}

	private static QuestionDraft question(String prompt, int seconds, ChoiceDraft... choices) {
		return new QuestionDraft(prompt, seconds, List.of(choices));
	}

	private static QuestionDraft validQuestion(String prompt) {
		return question(prompt, 15, choice("Sim", true), choice("Não", false));
	}

	private void assertRejected(QuizDraft draft, String message) {
		assertThatThrownBy(() -> quizzes.createQuiz(draft)).hasMessage(message);
	}

	@Test
	void createdQuizIsListedAndPlayedInOrder() {
		QuizCreated created = quizzes.createQuiz(new QuizDraft("  Quiz de Java  ", List.of(
				validQuestion("  Java é compilado?  "),
				question("Qual versão é LTS?", 30, choice("21", true), choice("22", false), choice("23", false)))));

		QuizSummary summary = created.summary();
		assertThat(summary.title()).isEqualTo("Quiz de Java");
		assertThat(summary.questionCount()).isEqualTo(2);
		assertThat(summary.canEdit()).isTrue();
		assertThat(created.ownerToken()).isNotBlank();

		List<QuizSummary> all = quizzes.listQuizzes(id -> id.equals(summary.id()) ? created.ownerToken() : null);
		assertThat(all).filteredOn(q -> q.id().equals(summary.id()))
				.first()
				.satisfies(q -> assertThat(q.canEdit()).isTrue());

		GameSession session = games.createGame("Host", summary.id());
		games.startGame(session.code());
		GameState state = games.getState(session.code(), HOST).orElseThrow();
		assertThat(state.totalQuestions()).isEqualTo(2);
		assertThat(state.currentQuestion().prompt()).isEqualTo("Java é compilado?");
		assertThat(state.currentQuestion().durationSeconds()).isEqualTo(15);
		assertThat(state.currentQuestion().choices()).extracting(c -> c.label()).containsExactly("Sim", "Não");
	}

	@Test
	void getQuizDetailsReflectsOwnership() {
		QuizCreated created = quizzes.createQuiz(new QuizDraft("Quiz Exclusivo", List.of(validQuestion("Pergunta?"))));

		QuizDetails detailsWithOwner = quizzes.getQuiz(created.summary().id(), created.ownerToken());
		assertThat(detailsWithOwner.title()).isEqualTo("Quiz Exclusivo");
		assertThat(detailsWithOwner.questions()).hasSize(1);
		assertThat(detailsWithOwner.canEdit()).isTrue();

		QuizDetails detailsWithoutOwner = quizzes.getQuiz(created.summary().id(), "token-invalido");
		assertThat(detailsWithoutOwner.canEdit()).isFalse();
	}

	@Test
	void updateAndAndDeleteRequireOwnership() {
		QuizCreated created = quizzes.createQuiz(new QuizDraft("Quiz Original", List.of(validQuestion("Pergunta 1?"))));
		String quizId = created.summary().id();
		String token = created.ownerToken();

		assertThatThrownBy(() -> quizzes.updateQuiz(quizId, new QuizDraft("Quiz Atualizado", List.of(validQuestion("Pergunta 1?"))), "errado"))
				.hasMessage("Você não tem permissão para editar este quiz.");

		QuizSummary updated = quizzes.updateQuiz(quizId, new QuizDraft("Quiz Atualizado", List.of(
				validQuestion("Pergunta Atualizada 1"),
				validQuestion("Pergunta Atualizada 2"))), token);
		assertThat(updated.title()).isEqualTo("Quiz Atualizado");
		assertThat(updated.questionCount()).isEqualTo(2);

		assertThatThrownBy(() -> quizzes.deleteQuiz(quizId, "errado"))
				.hasMessage("Você não tem permissão para excluir este quiz.");

		quizzes.deleteQuiz(quizId, token);
		assertThatThrownBy(() -> quizzes.getQuiz(quizId, null))
				.hasMessage("Quiz não encontrado.");
	}

	@Test
	void cannotDeleteQuizWithExistingGames() {
		QuizCreated created = quizzes.createQuiz(new QuizDraft("Quiz com Partida", List.of(validQuestion("Pergunta 1?"))));
		String quizId = created.summary().id();
		games.createGame("Host", quizId);

		assertThatThrownBy(() -> quizzes.deleteQuiz(quizId, created.ownerToken()))
				.hasMessage("Não é possível excluir um quiz que já possui partidas registradas.");
	}

	@Test
	void gameWithoutQuizKeepsTheInitialQuiz() {
		String code = games.createGame("Host").code();
		assertThat(games.getState(code, HOST).orElseThrow().totalQuestions()).isEqualTo(16);
	}

	@Test
	void rejectsUnknownQuizOnCreateGame() {
		assertThatThrownBy(() -> games.createGame("Host", "nao-existe")).hasMessage("Quiz não encontrado.");
	}

	@Test
	void rejectsInvalidTitleAndQuestionCount() {
		assertRejected(null, "Informe os dados do quiz.");
		assertRejected(new QuizDraft("ab", List.of(validQuestion("Pergunta?"))),
				"O título deve ter entre 3 e 80 caracteres.");
		assertRejected(new QuizDraft("Título válido", List.of()), "Adicione ao menos uma pergunta.");
		assertRejected(new QuizDraft("Título válido", null), "Adicione ao menos uma pergunta.");
		assertRejected(new QuizDraft("Título válido", java.util.Collections.nCopies(31, validQuestion("Pergunta?"))),
				"O quiz pode ter no máximo 30 perguntas.");
	}

	@Test
	void rejectsInvalidQuestions() {
		assertRejected(new QuizDraft("Título válido", List.of(validQuestion("ok?"), validQuestion("  "))),
				"Pergunta 2: o enunciado deve ter entre 3 e 300 caracteres.");
		assertRejected(new QuizDraft("Título válido",
				List.of(question("Pergunta?", 4, choice("A", true), choice("B", false)))),
				"Pergunta 1: o tempo deve ficar entre 5 e 120 segundos.");
		assertRejected(new QuizDraft("Título válido",
				List.of(question("Pergunta?", 121, choice("A", true), choice("B", false)))),
				"Pergunta 1: o tempo deve ficar entre 5 e 120 segundos.");
		assertRejected(new QuizDraft("Título válido", List.of(question("Pergunta?", 20, choice("A", true)))),
				"Pergunta 1: use de 2 a 4 alternativas.");
		assertRejected(new QuizDraft("Título válido", List.of(question("Pergunta?", 20,
				choice("A", true), choice("B", false), choice("C", false), choice("D", false), choice("E", false)))),
				"Pergunta 1: use de 2 a 4 alternativas.");
	}

	@Test
	void rejectsInvalidChoices() {
		assertRejected(new QuizDraft("Título válido",
				List.of(question("Pergunta?", 20, choice("A", true), choice("  ", false)))),
				"Pergunta 1: cada alternativa deve ter de 1 a 120 caracteres.");
		assertRejected(new QuizDraft("Título válido",
				List.of(question("Pergunta?", 20, choice("Sim", true), choice(" sim ", false)))),
				"Pergunta 1: as alternativas não podem se repetir.");
		assertRejected(new QuizDraft("Título válido",
				List.of(question("Pergunta?", 20, choice("A", false), choice("B", false)))),
				"Pergunta 1: marque exatamente uma alternativa correta.");
		assertRejected(new QuizDraft("Título válido",
				List.of(question("Pergunta?", 20, choice("A", true), choice("B", true)))),
				"Pergunta 1: marque exatamente uma alternativa correta.");
	}

	@Test
	void invalidQuizIsNotPartiallySaved() {
		int before = quizzes.listQuizzes().size();
		assertThatThrownBy(() -> quizzes.createQuiz(new QuizDraft("Título válido", List.of(
				validQuestion("Primeira ok?"),
				question("Segunda inválida?", 20, choice("A", false), choice("B", false))))))
				.isInstanceOf(RuntimeException.class);
		assertThat(quizzes.listQuizzes()).hasSize(before);
	}
}
