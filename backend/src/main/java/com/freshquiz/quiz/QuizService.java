package com.freshquiz.quiz;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.freshquiz.game.GameException;
import com.freshquiz.quiz.model.ChoiceDraft;
import com.freshquiz.quiz.model.QuestionDraft;
import com.freshquiz.quiz.model.QuizCreated;
import com.freshquiz.quiz.model.QuizDetails;
import com.freshquiz.quiz.model.QuizDraft;
import com.freshquiz.quiz.model.QuizSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

	public static final int TITLE_MIN = 3;
	public static final int TITLE_MAX = 80;
	public static final int QUESTIONS_MAX = 30;
	public static final int PROMPT_MIN = 3;
	public static final int PROMPT_MAX = 300;
	public static final int DURATION_MIN = 5;
	public static final int DURATION_MAX = 120;
	public static final int CHOICES_MIN = 2;
	public static final int CHOICES_MAX = 4;
	public static final int LABEL_MAX = 120;

	private final QuizRepository repository;

	public QuizService(QuizRepository repository) {
		this.repository = repository;
	}

	public List<QuizSummary> listQuizzes() {
		return listQuizzes(id -> null);
	}

	public List<QuizSummary> listQuizzes(Function<String, String> tokenProvider) {
		List<QuizRepository.QuizRow> rows = repository.listQuizRows();
		List<QuizSummary> result = new ArrayList<>(rows.size());
		for (QuizRepository.QuizRow row : rows) {
			String token = tokenProvider != null ? tokenProvider.apply(row.id()) : null;
			boolean canEdit = isHashMatch(row.ownerTokenHash(), token);
			result.add(new QuizSummary(row.id(), row.title(), row.questionCount(), canEdit));
		}
		return result;
	}

	public boolean verifyOwner(String quizId, String token) {
		if (quizId == null || token == null) return false;
		return repository.findOwnerTokenHash(quizId)
				.map(hash -> isHashMatch(hash, token))
				.orElse(false);
	}

	public QuizDetails getQuiz(String quizId, String token) {
		String title = repository.findTitle(quizId)
				.orElseThrow(() -> new GameException("Quiz não encontrado."));

		List<QuizRepository.QuestionRow> questionRows = repository.findQuestions(quizId);
		List<String> questionIds = questionRows.stream().map(QuizRepository.QuestionRow::id).toList();
		List<QuizRepository.ChoiceRow> choiceRows = repository.findChoices(questionIds);

		Map<String, List<QuizRepository.ChoiceRow>> choicesByQuestion = choiceRows.stream()
				.collect(Collectors.groupingBy(QuizRepository.ChoiceRow::questionId));

		List<QuestionDraft> questions = new ArrayList<>(questionRows.size());
		for (QuizRepository.QuestionRow qRow : questionRows) {
			List<QuizRepository.ChoiceRow> cList = choicesByQuestion.getOrDefault(qRow.id(), List.of());
			List<ChoiceDraft> choices = cList.stream()
					.map(c -> new ChoiceDraft(c.label(), c.isCorrect()))
					.toList();
			questions.add(new QuestionDraft(qRow.prompt(), qRow.durationSeconds(), choices));
		}

		boolean canEdit = verifyOwner(quizId, token);
		return new QuizDetails(quizId, title, questions, canEdit);
	}

	@Transactional
	public QuizCreated createQuiz(QuizDraft draft) {
		QuizDraft quiz = normalize(draft);

		String quizId = QuizRepository.createId();
		String ownerToken = UUID.randomUUID().toString();
		String hash = hashToken(ownerToken);

		repository.insertQuiz(quizId, quiz.title(), hash);
		persistQuestions(quizId, quiz.questions());

		QuizSummary summary = new QuizSummary(quizId, quiz.title(), quiz.questions().size(), true);
		return new QuizCreated(summary, ownerToken);
	}

	@Transactional
	public QuizSummary updateQuiz(String quizId, QuizDraft draft, String token) {
		if (!verifyOwner(quizId, token)) {
			throw new GameException("Você não tem permissão para editar este quiz.");
		}

		QuizDraft quiz = normalize(draft);
		repository.updateQuizTitle(quizId, quiz.title());
		repository.deleteQuestions(quizId);
		persistQuestions(quizId, quiz.questions());

		return new QuizSummary(quizId, quiz.title(), quiz.questions().size(), true);
	}

	@Transactional
	public void deleteQuiz(String quizId, String token) {
		if (!verifyOwner(quizId, token)) {
			throw new GameException("Você não tem permissão para excluir este quiz.");
		}
		if (repository.hasGames(quizId)) {
			throw new GameException("Não é possível excluir um quiz que já possui partidas registradas.");
		}
		repository.deleteQuiz(quizId);
	}

	private void persistQuestions(String quizId, List<QuestionDraft> questions) {
		int questionPosition = 1;
		for (QuestionDraft question : questions) {
			String questionId = QuizRepository.createId();
			repository.insertQuestion(questionId, quizId, questionPosition++,
					question.prompt(), question.durationSeconds());
			int choicePosition = 1;
			for (ChoiceDraft choice : question.choices()) {
				repository.insertChoice(QuizRepository.createId(), questionId, choicePosition++,
						choice.label(), choice.isCorrect());
			}
		}
	}

	public static String hashToken(String token) {
		if (token == null || token.isBlank()) return null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean isHashMatch(String storedHash, String rawToken) {
		if (storedHash == null || rawToken == null || rawToken.isBlank()) return false;
		String computed = hashToken(rawToken);
		return MessageDigest.isEqual(
				storedHash.getBytes(StandardCharsets.UTF_8),
				computed.getBytes(StandardCharsets.UTF_8));
	}

	private static QuizDraft normalize(QuizDraft draft) {
		if (draft == null) throw new GameException("Informe os dados do quiz.");

		String title = strip(draft.title());
		if (title.length() < TITLE_MIN || title.length() > TITLE_MAX) {
			throw new GameException("O título deve ter entre %d e %d caracteres.".formatted(TITLE_MIN, TITLE_MAX));
		}

		List<QuestionDraft> questions = draft.questions() == null ? List.of() : draft.questions();
		if (questions.isEmpty()) throw new GameException("Adicione ao menos uma pergunta.");
		if (questions.size() > QUESTIONS_MAX) {
			throw new GameException("O quiz pode ter no máximo %d perguntas.".formatted(QUESTIONS_MAX));
		}

		List<QuestionDraft> clean = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			clean.add(normalizeQuestion(questions.get(i), i + 1));
		}
		return new QuizDraft(title, clean);
	}

	private static QuestionDraft normalizeQuestion(QuestionDraft question, int number) {
		String where = "Pergunta " + number + ": ";
		if (question == null) throw new GameException(where + "dados inválidos.");

		String prompt = strip(question.prompt());
		if (prompt.length() < PROMPT_MIN || prompt.length() > PROMPT_MAX) {
			throw new GameException(where + "o enunciado deve ter entre %d e %d caracteres."
					.formatted(PROMPT_MIN, PROMPT_MAX));
		}
		if (question.durationSeconds() < DURATION_MIN || question.durationSeconds() > DURATION_MAX) {
			throw new GameException(where + "o tempo deve ficar entre %d e %d segundos."
					.formatted(DURATION_MIN, DURATION_MAX));
		}

		List<ChoiceDraft> choices = question.choices() == null ? List.of() : question.choices();
		if (choices.size() < CHOICES_MIN || choices.size() > CHOICES_MAX) {
			throw new GameException(where + "use de %d a %d alternativas.".formatted(CHOICES_MIN, CHOICES_MAX));
		}

		Set<String> labels = new HashSet<>();
		List<ChoiceDraft> clean = new ArrayList<>();
		int correct = 0;
		for (ChoiceDraft choice : choices) {
			String label = choice == null ? "" : strip(choice.label());
			if (label.isEmpty() || label.length() > LABEL_MAX) {
				throw new GameException(where + "cada alternativa deve ter de 1 a %d caracteres."
						.formatted(LABEL_MAX));
			}
			if (!labels.add(label.toLowerCase(Locale.ROOT))) {
				throw new GameException(where + "as alternativas não podem se repetir.");
			}
			if (choice.isCorrect()) correct++;
			clean.add(new ChoiceDraft(label, choice.isCorrect()));
		}
		if (correct != 1) throw new GameException(where + "marque exatamente uma alternativa correta.");

		return new QuestionDraft(prompt, question.durationSeconds(), clean);
	}

	private static String strip(String value) {
		return value == null ? "" : value.strip();
	}
}
