import {
  QUIZ_LIMITS,
  QuizDraft,
  addChoice,
  addQuestion,
  emptyQuiz,
  fromQuizDetails,
  moveQuestion,
  removeChoice,
  removeQuestion,
  setCorrectChoice,
  toQuizPayload,
  updateChoiceLabel,
  updateQuestion,
  validateQuiz,
} from './quiz-draft';

function filledQuiz(): QuizDraft {
  let draft = emptyQuiz();
  draft = { ...draft, title: '  Quiz de Java  ' };
  draft = updateQuestion(draft, 0, { prompt: '  Java é compilado?  ' });
  draft = updateChoiceLabel(draft, 0, 0, ' Sim ');
  draft = updateChoiceLabel(draft, 0, 1, 'Não');
  return draft;
}

describe('quiz-draft', () => {
  it('starts with one question, two choices and the first one correct', () => {
    const draft = emptyQuiz();
    expect(draft.questions).toHaveLength(1);
    expect(draft.questions[0].durationSeconds).toBe(QUIZ_LIMITS.durationDefault);
    expect(draft.questions[0].choices.map((c) => c.isCorrect)).toEqual([true, false]);
  });

  it('gives every question and choice its own key', () => {
    const draft = addQuestion(emptyQuiz());
    const keys = draft.questions.flatMap((q) => [q.key, ...q.choices.map((c) => c.key)]);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('never exceeds the question and choice limits', () => {
    let draft = emptyQuiz();
    for (let i = 0; i < QUIZ_LIMITS.questionsMax + 5; i++) draft = addQuestion(draft);
    expect(draft.questions).toHaveLength(QUIZ_LIMITS.questionsMax);

    for (let i = 0; i < QUIZ_LIMITS.choicesMax + 5; i++) draft = addChoice(draft, 0);
    expect(draft.questions[0].choices).toHaveLength(QUIZ_LIMITS.choicesMax);
  });

  it('keeps at least the minimum number of choices', () => {
    let draft = emptyQuiz();
    draft = removeChoice(draft, 0, 1);
    expect(draft.questions[0].choices).toHaveLength(QUIZ_LIMITS.choicesMin);
  });

  it('moves the correct mark to the first choice left when the correct one is removed', () => {
    let draft = addChoice(emptyQuiz(), 0);
    draft = removeChoice(draft, 0, 0);
    expect(draft.questions[0].choices.map((c) => c.isCorrect)).toEqual([true, false]);
  });

  it('marks exactly one correct choice per question', () => {
    const draft = setCorrectChoice(addChoice(emptyQuiz(), 0), 0, 2);
    expect(draft.questions[0].choices.map((c) => c.isCorrect)).toEqual([false, false, true]);
  });

  it('removes and reorders questions without mutating the original', () => {
    const original = addQuestion(addQuestion(emptyQuiz()));
    const [a, b, c] = original.questions.map((q) => q.key);

    expect(moveQuestion(original, 0, 1).questions.map((q) => q.key)).toEqual([b, a, c]);
    expect(moveQuestion(original, 0, -1)).toBe(original);
    expect(moveQuestion(original, 2, 1)).toBe(original);
    expect(removeQuestion(original, 1).questions.map((q) => q.key)).toEqual([a, c]);
    expect(original.questions.map((q) => q.key)).toEqual([a, b, c]);
  });

  it('accepts a filled quiz', () => {
    expect(validateQuiz(filledQuiz())).toBeNull();
  });

  it('reports the first problem in screen order', () => {
    expect(validateQuiz(emptyQuiz())).toBe('O título deve ter entre 3 e 80 caracteres.');
    expect(validateQuiz({ title: 'Quiz', questions: [] })).toBe('Adicione ao menos uma pergunta.');

    const untitled = { ...emptyQuiz(), title: 'Quiz' };
    expect(validateQuiz(untitled)).toBe('Pergunta 1: o enunciado deve ter entre 3 e 300 caracteres.');
  });

  it('validates time, labels and the correct choice', () => {
    const base = filledQuiz();

    expect(validateQuiz(updateQuestion(base, 0, { durationSeconds: 4 }))).toBe(
      'Pergunta 1: o tempo deve ficar entre 5 e 120 segundos.',
    );
    expect(validateQuiz(updateQuestion(base, 0, { durationSeconds: 12.5 }))).toBe(
      'Pergunta 1: o tempo deve ficar entre 5 e 120 segundos.',
    );
    expect(validateQuiz(updateChoiceLabel(base, 0, 1, '   '))).toBe(
      'Pergunta 1: cada alternativa deve ter de 1 a 120 caracteres.',
    );
    expect(validateQuiz(updateChoiceLabel(base, 0, 1, ' sim '))).toBe(
      'Pergunta 1: as alternativas não podem se repetir.',
    );

    const noCorrect = {
      ...base,
      questions: [
        { ...base.questions[0], choices: base.questions[0].choices.map((c) => ({ ...c, isCorrect: false })) },
      ],
    };
    expect(validateQuiz(noCorrect)).toBe('Pergunta 1: marque exatamente uma alternativa correta.');
  });

  it('numbers the question that has the problem', () => {
    const draft = addQuestion(filledQuiz());
    expect(validateQuiz(draft)).toMatch(/^Pergunta 2: /);
  });

  it('builds the API payload trimmed and without screen keys', () => {
    expect(toQuizPayload(filledQuiz())).toEqual({
      title: 'Quiz de Java',
      questions: [
        {
          prompt: 'Java é compilado?',
          durationSeconds: 20,
          choices: [
            { label: 'Sim', isCorrect: true },
            { label: 'Não', isCorrect: false },
          ],
        },
      ],
    });
  });

  it('populates draft from quiz details', () => {
    const details = {
      id: 'quiz-1',
      title: 'Quiz Detalhes',
      questions: [
        {
          prompt: 'P1?',
          durationSeconds: 15,
          choices: [
            { label: 'A', isCorrect: true },
            { label: 'B', isCorrect: false },
          ],
        },
      ],
      canEdit: true,
    };
    const draft = fromQuizDetails(details);
    expect(draft.title).toBe('Quiz Detalhes');
    expect(draft.questions).toHaveLength(1);
    expect(draft.questions[0].prompt).toBe('P1?');
    expect(draft.questions[0].durationSeconds).toBe(15);
    expect(draft.questions[0].choices[0].label).toBe('A');
    expect(draft.questions[0].choices[0].isCorrect).toBe(true);
    expect(draft.questions[0].choices[1].label).toBe('B');
  });
});
