import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GameApi, errorMessage } from '../core/game-api';
import type { QuizSummary } from '../core/models';
import { Brand } from '../ui/brand';
import { LoadingState } from '../ui/loading-state';

@Component({
  selector: 'app-quiz-list-page',
  imports: [Brand, LoadingState, RouterLink],
  template: `
    <main class="page-background">
      <div class="shell">
        <header class="home-header">
          <app-brand />
          <nav class="home-nav" aria-label="Navegação principal">
            <a class="home-link" routerLink="/">Início</a>
            <a class="home-link" routerLink="/host">Criar partida</a>
          </nav>
        </header>

        <section class="quiz-list-head">
          <div>
            <p class="eyebrow">Manter quizzes</p>
            <h1 class="island-title island-title-lg">Seus quizzes e perguntas.</h1>
            <p class="island-copy island-copy-lg">
              Crie, edite ou exclua quizzes personalizados. Só quem criou o quiz neste navegador pode alterá-lo.
            </p>
          </div>
          <a class="button button-primary" routerLink="/quizzes/new">Novo quiz</a>
        </section>

        @if (error()) {
          <p class="error-message" role="alert">{{ error() }}</p>
        }

        @if (loading()) {
          <app-loading-state message="Carregando quizzes..." />
        } @else {
          @if (mine().length > 0) {
            <h2 class="quiz-section-title">Criados por você ({{ mine().length }})</h2>
            <ul class="quiz-grid">
              @for (quiz of mine(); track quiz.id) {
                <li class="island quiz-card">
                  <div>
                    <h3 class="quiz-card-title">{{ quiz.title }}</h3>
                    <p class="island-copy">{{ countLabel(quiz.questionCount) }}</p>
                  </div>
                  <div class="quiz-card-actions">
                    <a class="button button-primary" routerLink="/host" [queryParams]="{ quiz: quiz.id }">Jogar</a>
                    <a class="button button-ghost" [routerLink]="['/quizzes', quiz.id, 'edit']">Editar</a>
                    <button
                      class="button button-danger"
                      type="button"
                      [disabled]="deletingId() === quiz.id"
                      [attr.aria-label]="'Excluir o quiz ' + quiz.title"
                      (click)="remove(quiz)"
                    >
                      {{ deletingId() === quiz.id ? 'Excluindo...' : 'Excluir' }}
                    </button>
                  </div>
                </li>
              }
            </ul>
          } @else {
            <div class="island empty-state empty-state-compact">
              <h2>Você ainda não criou nenhum quiz.</h2>
              <p>Monte suas próprias perguntas e use-as ao criar uma sala.</p>
              <a class="button button-primary" routerLink="/quizzes/new">Criar meu primeiro quiz</a>
            </div>
          }

          @if (others().length > 0) {
            <h2 class="quiz-section-title">Outros quizzes ({{ others().length }})</h2>
            <ul class="quiz-grid">
              @for (quiz of others(); track quiz.id) {
                <li class="island quiz-card">
                  <div>
                    <h3 class="quiz-card-title">{{ quiz.title }}</h3>
                    <p class="island-copy">{{ countLabel(quiz.questionCount) }}</p>
                  </div>
                  <div class="quiz-card-actions">
                    <a class="button button-primary" routerLink="/host" [queryParams]="{ quiz: quiz.id }">Jogar</a>
                    <a class="button button-ghost" [routerLink]="['/quizzes', quiz.id, 'edit']">Ver perguntas</a>
                  </div>
                </li>
              }
            </ul>
          }
        }
      </div>
    </main>
  `,
})
export class QuizListPage implements OnInit {
  private readonly api = inject(GameApi);

  protected readonly quizzes = signal<QuizSummary[]>([]);
  protected readonly mine = computed(() => this.quizzes().filter((quiz) => quiz.canEdit));
  protected readonly others = computed(() => this.quizzes().filter((quiz) => !quiz.canEdit));
  protected readonly loading = signal(true);
  protected readonly deletingId = signal('');
  protected readonly error = signal('');

  async ngOnInit(): Promise<void> {
    try {
      this.quizzes.set(await this.api.listQuizzes());
    } catch (cause) {
      this.error.set(errorMessage(cause, 'Não foi possível carregar os quizzes.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected countLabel(count: number): string {
    return `${count} ${count === 1 ? 'pergunta' : 'perguntas'}`;
  }

  protected async remove(quiz: QuizSummary): Promise<void> {
    if (!confirm(`Excluir o quiz "${quiz.title}"? Essa ação não pode ser desfeita.`)) return;

    this.deletingId.set(quiz.id);
    this.error.set('');
    try {
      await this.api.deleteQuiz(quiz.id);
      this.quizzes.update((list) => list.filter((item) => item.id !== quiz.id));
    } catch (cause) {
      this.error.set(errorMessage(cause, 'Não foi possível excluir o quiz.'));
    } finally {
      this.deletingId.set('');
    }
  }
}
