import { Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { GameApi, HostAction, errorMessage } from '../core/game-api';
import { GameStateStore } from '../core/game-state.store';
import type { GameStatus } from '../core/models';
import { totalVotes, voteShare } from '../core/votes';
import { Countdown } from '../ui/countdown';
import { LoadingState } from '../ui/loading-state';
import { Podium } from '../ui/podium';

const STATUS_LABELS: Record<GameStatus, string> = {
  lobby: 'Lobby',
  question: 'Valendo',
  reveal: 'Resultado',
  finished: 'Final',
};

@Component({
  selector: 'app-host-game',
  imports: [Countdown, LoadingState, Podium],
  template: `
    <section class="island island-host">
      <div class="game-header">
        <div>
          <p class="eyebrow">Você conduz em</p>
          <h1 class="island-title island-title-lg island-title-tight">{{ code() }}</h1>
        </div>
        <div class="header-actions">
          <span class="score-pill">{{ state()?.players?.length ?? 0 }} jogadores</span>
          <button class="button button-ghost" (click)="leaveRoom()" type="button">Sair da sala</button>
        </div>
      </div>

      @if (store.error() || actionError()) {
        <p class="error-message">{{ store.error() || actionError() }}</p>
      }

      @let game = state();
      @if (!game) {
        <app-loading-state />
      } @else {
        @if (game.status === 'lobby') {
          <div class="empty-state">
            <span class="empty-icon glow-element">01</span>
            <h2>Aguardando jogadores</h2>
            <p>Compartilhe o código da sala e comece quando todos estiverem prontos.</p>
          </div>
        }

        @if (game.currentQuestion; as current) {
          @if (game.status === 'question' || game.status === 'reveal') {
            <div class="question-view">
              <div class="question-meta">
                <p class="eyebrow">Pergunta {{ game.currentQuestionPosition }} de {{ game.totalQuestions }}</p>
                <div class="question-meta-actions">
                  <span class="status status-{{ game.status }}">{{ statusLabels[game.status] }}</span>
                  @if (game.status === 'question') {
                    <app-countdown [deadlineAt]="game.deadlineAt" />
                  }
                </div>
              </div>
              <h2 class="question-title">{{ current.prompt }}</h2>
              @if (current.answerCounts) {
                <div class="votes-summary">
                  <span class="eyebrow">{{ game.status === 'question' ? 'Votação ao vivo' : 'Votos por alternativa' }}</span>
                  <span class="votes-summary-count">{{ totalAnswers() }} de {{ game.players.length }} responderam</span>
                </div>
              }
              <div class="choice-grid">
                @for (choice of current.choices; track choice.id) {
                  <div class="choice-card" [class.choice-correct]="current.correctChoiceId === choice.id">
                    <span class="choice-index">{{ choice.position }}</span>
                    <span class="choice-body">
                      <span>{{ choice.label }}</span>
                      @if (current.answerCounts) {
                        <span class="vote-row">
                          <span class="vote-track">
                            <span class="vote-fill" [style.width.%]="share(choice.id)"></span>
                          </span>
                          <span class="vote-pct">{{ share(choice.id) }}%</span>
                        </span>
                      }
                    </span>
                  </div>
                }
              </div>
            </div>
          }
        }

        @if (game.status === 'finished') {
          <div class="empty-state">
            <span class="empty-icon">🏆</span>
            <h2>Quiz encerrado</h2>
            <p>Parabéns! Veja o Top 3 do quiz:</p>
            <app-podium [players]="game.players" />
          </div>
          <button class="button button-primary button-block" [disabled]="loading()" (click)="restartQuiz()" type="button">
            {{ loading() ? 'Reiniciando...' : 'Jogar novamente' }}
          </button>
        } @else {
          <div class="actions-row">
            @if (game.status !== 'question') {
              <button
                class="button button-primary button-grow"
                [disabled]="loading() || (game.status === 'lobby' && game.players.length === 0)"
                (click)="action(game.status === 'lobby' ? 'start' : 'next')"
                type="button"
              >
                {{ loading() ? 'Atualizando...' : actionLabel() }}
              </button>
            } @else {
              <button class="button button-ghost" [disabled]="loading()" (click)="action('skip')" type="button">
                Pular pergunta
              </button>
            }
            @if (game.status !== 'lobby') {
              <button class="button button-danger" [disabled]="loading()" (click)="finishQuiz()" type="button">
                Encerrar quiz
              </button>
            }
          </div>
        }
      }
    </section>
  `,
})
export class HostGame {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);
  protected readonly store = inject(GameStateStore);

  readonly code = input.required<string>();

  protected readonly state = this.store.state;
  protected readonly actionError = signal('');
  protected readonly loading = signal(false);
  protected readonly statusLabels = STATUS_LABELS;

  protected readonly totalAnswers = computed(() => totalVotes(this.state()?.currentQuestion?.answerCounts));
  protected readonly actionLabel = computed(() => {
    const game = this.state();
    if (game?.status === 'lobby') return 'Começar quiz';
    if (game?.status === 'reveal') {
      return game.currentQuestionPosition === game.totalQuestions ? 'Ver resultado final' : 'Próxima pergunta';
    }
    return '';
  });

  protected share(choiceId: string): number {
    return voteShare(this.state()?.currentQuestion?.answerCounts, choiceId);
  }

  protected async action(action: HostAction): Promise<void> {
    this.loading.set(true);
    this.actionError.set('');
    try {
      await this.api.hostAction(this.code(), action);
    } catch (cause) {
      this.actionError.set(errorMessage(cause, 'Não foi possível atualizar a partida.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async leaveRoom(): Promise<void> {
    if (!confirm('Sair da sala? Você perderá o acesso de host a esta partida.')) return;
    await this.api.leave(this.code()).catch(() => undefined);
    await this.router.navigateByUrl('/');
  }

  protected finishQuiz(): void {
    if (confirm('Encerrar o quiz agora e mostrar o resultado final?')) void this.action('finish');
  }

  protected restartQuiz(): void {
    if (confirm('Jogar novamente? O placar e as respostas desta partida serão zerados.')) void this.action('restart');
  }
}
