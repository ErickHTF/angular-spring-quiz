export const QUIZ_LIMITS = {
  titleMin: 3,
  titleMax: 80,
  questionsMax: 30,
  promptMin: 3,
  promptMax: 300,
  durationMin: 5,
  durationMax: 120,
  durationDefault: 20,
  choicesMin: 2,
  choicesMax: 4,
  labelMax: 120,
} as const;

export interface ChoiceDraft {
  key: number;
  label: string;
  isCorrect: boolean;
}

export interface QuestionDraft {
  key: number;
  prompt: string;
  durationSeconds: number;
  choices: ChoiceDraft[];
}

export interface QuizDraft {
  title: string;
  questions: QuestionDraft[];
}

export interface QuizPayload {
  title: string;
  questions: {
    prompt: string;
    durationSeconds: number;
    choices: { label: string; isCorrect: boolean }[];
  }[];
}

let nextKey = 1;

function newKey(): number {
  return nextKey++;
}

export function emptyChoice(isCorrect = false): ChoiceDraft {
  return { key: newKey(), label: '', isCorrect };
}

export function emptyQuestion(): QuestionDraft {
  return {
    key: newKey(),
    prompt: '',
    durationSeconds: QUIZ_LIMITS.durationDefault,
    choices: [emptyChoice(true), emptyChoice()],
  };
}

export function emptyQuiz(): QuizDraft {
  return { title: '', questions: [emptyQuestion()] };
}

export function updateQuestion(
  draft: QuizDraft,
  index: number,
  change: Partial<Pick<QuestionDraft, 'prompt' | 'durationSeconds'>>,
): QuizDraft {
  return withQuestion(draft, index, (question) => ({ ...question, ...change }));
}

export function addQuestion(draft: QuizDraft): QuizDraft {
  if (draft.questions.length >= QUIZ_LIMITS.questionsMax) return draft;
  return { ...draft, questions: [...draft.questions, emptyQuestion()] };
}

export function removeQuestion(draft: QuizDraft, index: number): QuizDraft {
  return { ...draft, questions: draft.questions.filter((_, i) => i !== index) };
}

export function moveQuestion(draft: QuizDraft, index: number, offset: -1 | 1): QuizDraft {
  const target = index + offset;
  if (target < 0 || target >= draft.questions.length) return draft;
  const questions = [...draft.questions];
  [questions[index], questions[target]] = [questions[target], questions[index]];
  return { ...draft, questions };
}

export function addChoice(draft: QuizDraft, questionIndex: number): QuizDraft {
  return withQuestion(draft, questionIndex, (question) =>
    question.choices.length >= QUIZ_LIMITS.choicesMax
      ? question
      : { ...question, choices: [...question.choices, emptyChoice()] },
  );
}

export function removeChoice(draft: QuizDraft, questionIndex: number, choiceIndex: number): QuizDraft {
  return withQuestion(draft, questionIndex, (question) => {
    if (question.choices.length <= QUIZ_LIMITS.choicesMin) return question;
    const choices = question.choices.filter((_, i) => i !== choiceIndex);
    if (!choices.some((choice) => choice.isCorrect)) choices[0] = { ...choices[0], isCorrect: true };
    return { ...question, choices };
  });
}

export function updateChoiceLabel(
  draft: QuizDraft,
  questionIndex: number,
  choiceIndex: number,
  label: string,
): QuizDraft {
  return withQuestion(draft, questionIndex, (question) => ({
    ...question,
    choices: question.choices.map((choice, i) => (i === choiceIndex ? { ...choice, label } : choice)),
  }));
}

export function setCorrectChoice(draft: QuizDraft, questionIndex: number, choiceIndex: number): QuizDraft {
  return withQuestion(draft, questionIndex, (question) => ({
    ...question,
    choices: question.choices.map((choice, i) => ({ ...choice, isCorrect: i === choiceIndex })),
  }));
}

export function validateQuiz(draft: QuizDraft): string | null {
  const title = draft.title.trim();
  if (title.length < QUIZ_LIMITS.titleMin || title.length > QUIZ_LIMITS.titleMax) {
    return `O título deve ter entre ${QUIZ_LIMITS.titleMin} e ${QUIZ_LIMITS.titleMax} caracteres.`;
  }
  if (draft.questions.length === 0) return 'Adicione ao menos uma pergunta.';

  for (const [index, question] of draft.questions.entries()) {
    const problem = validateQuestion(question);
    if (problem) return `Pergunta ${index + 1}: ${problem}`;
  }
  return null;
}

function validateQuestion(question: QuestionDraft): string | null {
  const prompt = question.prompt.trim();
  if (prompt.length < QUIZ_LIMITS.promptMin || prompt.length > QUIZ_LIMITS.promptMax) {
    return `o enunciado deve ter entre ${QUIZ_LIMITS.promptMin} e ${QUIZ_LIMITS.promptMax} caracteres.`;
  }
  const seconds = question.durationSeconds;
  if (!Number.isInteger(seconds) || seconds < QUIZ_LIMITS.durationMin || seconds > QUIZ_LIMITS.durationMax) {
    return `o tempo deve ficar entre ${QUIZ_LIMITS.durationMin} e ${QUIZ_LIMITS.durationMax} segundos.`;
  }

  const labels = question.choices.map((choice) => choice.label.trim());
  if (labels.some((label) => label.length === 0 || label.length > QUIZ_LIMITS.labelMax)) {
    return `cada alternativa deve ter de 1 a ${QUIZ_LIMITS.labelMax} caracteres.`;
  }
  if (new Set(labels.map((label) => label.toLowerCase())).size !== labels.length) {
    return 'as alternativas não podem se repetir.';
  }
  if (question.choices.filter((choice) => choice.isCorrect).length !== 1) {
    return 'marque exatamente uma alternativa correta.';
  }
  return null;
}

export function fromQuizDetails(details: {
  title: string;
  questions: { prompt: string; durationSeconds: number; choices: { label: string; isCorrect: boolean }[] }[];
}): QuizDraft {
  return {
    title: details.title,
    questions: details.questions.map((q) => ({
      key: newKey(),
      prompt: q.prompt,
      durationSeconds: q.durationSeconds,
      choices: q.choices.map((c) => ({
        key: newKey(),
        label: c.label,
        isCorrect: c.isCorrect,
      })),
    })),
  };
}

export function toQuizPayload(draft: QuizDraft): QuizPayload {
  return {
    title: draft.title.trim(),
    questions: draft.questions.map((question) => ({
      prompt: question.prompt.trim(),
      durationSeconds: question.durationSeconds,
      choices: question.choices.map((choice) => ({
        label: choice.label.trim(),
        isCorrect: choice.isCorrect,
      })),
    })),
  };
}

function withQuestion(
  draft: QuizDraft,
  index: number,
  change: (question: QuestionDraft) => QuestionDraft,
): QuizDraft {
  return {
    ...draft,
    questions: draft.questions.map((question, i) => (i === index ? change(question) : question)),
  };
}
