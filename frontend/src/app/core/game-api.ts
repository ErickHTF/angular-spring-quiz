import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import type { GameState, Me, QuizDetails, QuizSummary } from './models';
import type { QuizPayload } from './quiz-draft';

export type HostAction = 'start' | 'next' | 'skip' | 'finish' | 'restart';

@Injectable({ providedIn: 'root' })
export class GameApi {
  private readonly http = inject(HttpClient);

  createGame(nickname: string, quizId?: string): Promise<{ code: string }> {
    return this.call(this.http.post<{ code: string }>('/api/games', { nickname, quizId }));
  }

  listQuizzes(): Promise<QuizSummary[]> {
    return this.call(this.http.get<QuizSummary[]>('/api/quizzes'));
  }

  getQuiz(id: string): Promise<QuizDetails> {
    return this.call(this.http.get<QuizDetails>(`/api/quizzes/${id}`));
  }

  createQuiz(quiz: QuizPayload): Promise<QuizSummary> {
    return this.call(this.http.post<QuizSummary>('/api/quizzes', quiz));
  }

  updateQuiz(id: string, quiz: QuizPayload): Promise<QuizSummary> {
    return this.call(this.http.put<QuizSummary>(`/api/quizzes/${id}`, quiz));
  }

  deleteQuiz(id: string): Promise<unknown> {
    return this.call(this.http.delete(`/api/quizzes/${id}`));
  }

  join(code: string, nickname: string): Promise<{ code: string; nickname: string; playerId: string }> {
    return this.call(this.http.post<{ code: string; nickname: string; playerId: string }>(
      `/api/games/${code}/join`,
      { nickname },
    ));
  }

  me(code: string): Promise<Me> {
    return this.call(this.http.get<Me>(`/api/games/${code}/me`));
  }

  state(code: string): Promise<GameState> {
    return this.call(this.http.get<GameState>(`/api/games/${code}/state`));
  }

  hostAction(code: string, action: HostAction): Promise<unknown> {
    return this.call(this.http.post(`/api/games/${code}/${action}`, null));
  }

  answer(code: string, choiceId: string): Promise<unknown> {
    return this.call(this.http.post(`/api/games/${code}/answer`, { choiceId }));
  }

  leave(code: string): Promise<unknown> {
    return this.call(this.http.post(`/api/games/${code}/leave`, null));
  }

  private async call<T>(request: Observable<T>): Promise<T> {
    try {
      return await firstValueFrom(request);
    } catch (cause) {
      if (cause instanceof HttpErrorResponse) {
        throw new ApiError(cause.status, cause.error?.error ?? 'Não foi possível completar a ação.');
      }
      throw cause;
    }
  }
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function errorMessage(cause: unknown, fallback: string): string {
  return cause instanceof Error && cause.message ? cause.message : fallback;
}
