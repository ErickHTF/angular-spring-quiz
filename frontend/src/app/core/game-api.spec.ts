import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiError, GameApi } from './game-api';

describe('GameApi quizzes', () => {
  let api: GameApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(GameApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the available quizzes', async () => {
    const result = api.listQuizzes();
    http.expectOne('/api/quizzes').flush([{ id: 'web-basics', title: 'Fundamentos da Web', questionCount: 16 }]);
    expect(await result).toEqual([{ id: 'web-basics', title: 'Fundamentos da Web', questionCount: 16 }]);
  });

  it('retrieves quiz details by id', async () => {
    const details = {
      id: 'q1',
      title: 'Meu quiz',
      questions: [],
      canEdit: true,
    };
    const result = api.getQuiz('q1');
    const request = http.expectOne('/api/quizzes/q1');
    expect(request.request.method).toBe('GET');
    request.flush(details);
    expect(await result).toEqual(details);
  });

  it('creates a quiz from the payload', async () => {
    const payload = {
      title: 'Meu quiz',
      questions: [
        {
          prompt: 'Pergunta?',
          durationSeconds: 20,
          choices: [
            { label: 'A', isCorrect: true },
            { label: 'B', isCorrect: false },
          ],
        },
      ],
    };
    const result = api.createQuiz(payload);
    const request = http.expectOne('/api/quizzes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 'q1', title: 'Meu quiz', questionCount: 1, canEdit: true });
    expect(await result).toEqual({ id: 'q1', title: 'Meu quiz', questionCount: 1, canEdit: true });
  });

  it('updates an existing quiz', async () => {
    const payload = {
      title: 'Quiz Editado',
      questions: [
        {
          prompt: 'Nova pergunta?',
          durationSeconds: 30,
          choices: [
            { label: 'X', isCorrect: true },
            { label: 'Y', isCorrect: false },
          ],
        },
      ],
    };
    const result = api.updateQuiz('q1', payload);
    const request = http.expectOne('/api/quizzes/q1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 'q1', title: 'Quiz Editado', questionCount: 1, canEdit: true });
    expect(await result).toEqual({ id: 'q1', title: 'Quiz Editado', questionCount: 1, canEdit: true });
  });

  it('deletes a quiz', async () => {
    const result = api.deleteQuiz('q1');
    const request = http.expectOne('/api/quizzes/q1');
    expect(request.request.method).toBe('DELETE');
    request.flush({ ok: true });
    await result;
  });

  it('sends the chosen quiz when creating a game', async () => {
    const result = api.createGame('Host', 'q1');
    const request = http.expectOne('/api/games');
    expect(request.request.body).toEqual({ nickname: 'Host', quizId: 'q1' });
    request.flush({ code: 'ABC234' });
    expect(await result).toEqual({ code: 'ABC234' });
  });

  it('turns the API error message into an ApiError', async () => {
    const result = api.createQuiz({ title: 'x', questions: [] });
    http.expectOne('/api/quizzes').flush({ error: 'Adicione ao menos uma pergunta.' }, { status: 400, statusText: 'Bad Request' });
    await expect(result).rejects.toEqual(new ApiError(400, 'Adicione ao menos uma pergunta.'));
  });
});
