import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GameApi, errorMessage } from '../core/game-api';
import type { QuizSummary } from '../core/models';
import { Brand } from '../ui/brand';

@Component({
  selector: 'app-host-landing-page',
  imports: [Brand, FormsModule, RouterLink],
  template: `
    <main class="page-background">
      <div class="shell shell-small">
        <app-brand />
        <div class="landing-shell">
          <form class="island island-host" (ngSubmit)="createGame()">
            <div>
              <p class="eyebrow">Modo host</p>
              <h1 class="island-title island-title-lg">Crie uma sala e compartilhe o código.</h1>
              <p class="island-copy island-copy-lg">Seu nome aparecerá em destaque para os jogadores.</p>
            </div>
            <label class="field-label">
              Seu nome
              <input
                class="field"
                maxlength="24"
                name="nickname"
                placeholder="Como os jogadores devem te chamar?"
                [(ngModel)]="nickname"
                required
              />
            </label>
            @if (quizzes().length > 0) {
              <label class="field-label">
                Quiz da partida
                <select
                  class="field"
                  name="quiz"
                  [ngModel]="selectedQuizId()"
                  (ngModelChange)="selectedQuizId.set($event)"
                >
                  @for (quiz of quizzes(); track quiz.id) {
                    <option [value]="quiz.id">
                      {{ quiz.title }} ({{ quiz.questionCount }}
                      {{ quiz.questionCount === 1 ? 'pergunta' : 'perguntas' }})
                    </option>
                  }
                </select>
              </label>
            }
            <div class="flex items-center justify-between gap-2">
              <a class="home-link" routerLink="/quizzes/new">Criar meu próprio quiz</a>
              @if (selectedQuiz()?.canEdit) {
                <a class="home-link" [routerLink]="['/quizzes', selectedQuizId(), 'edit']">Editar este quiz</a>
              }
            </div>
            @if (error()) {
              <p class="error-message">{{ error() }}</p>
            }
            <button class="button button-primary button-block" [disabled]="loading()" type="submit">
              {{ loading() ? 'Criando sala...' : 'Criar nova sala' }}
            </button>
          </form>
        </div>
      </div>
    </main>
  `,
})
export class HostLandingPage implements OnInit {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);

  readonly quiz = input<string>();
  protected nickname = '';
  protected readonly quizzes = signal<QuizSummary[]>([]);
  protected readonly selectedQuizId = signal('');
  protected readonly selectedQuiz = computed(() =>
    this.quizzes().find((q) => q.id === this.selectedQuizId()),
  );
  protected readonly error = signal('');
  protected readonly loading = signal(false);

  async ngOnInit(): Promise<void> {
    try {
      const quizzes = await this.api.listQuizzes();
      this.quizzes.set(quizzes);
      const requested = quizzes.find((quiz) => quiz.id === this.quiz());
      this.selectedQuizId.set((requested ?? quizzes[0])?.id ?? '');
    } catch {
    }
  }

  protected async createGame(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const { code } = await this.api.createGame(this.nickname, this.selectedQuizId() || undefined);
      await this.router.navigate(['/host', code]);
    } catch (cause) {
      this.error.set(errorMessage(cause, 'Não foi possível criar a sala.'));
      this.loading.set(false);
    }
  }
}
