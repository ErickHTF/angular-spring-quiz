import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { GameApi, errorMessage } from '../core/game-api';
import { GameStateStore } from '../core/game-state.store';
import { shuffleChoices } from '../core/shuffle';
import { totalVotes, voteShare } from '../core/votes';
import { Countdown } from '../ui/countdown';
import { LoadingState } from '../ui/loading-state';
import { Podium } from '../ui/podium';

@Component({
  selector: 'app-player-game',
  imports: [Countdown, LoadingState, Podium],
  template: `
    <section class="island island-player">
      <div class="game-header">
        <div>
          <p class="eyebrow">Você está jogando em</p>
          <h1 class="island-title island-title-lg island-title-tight">{{ code() }}</h1>
        </div>
        <div class="header-actions">
          <span class="score-pill" [class.score-bounce]="scoreAnimating()">
            {{ player()?.score ?? 0 }} pts
            @if (scoreAnimating()) {
              <!-- Trocar a chave recria o elemento, reiniciando a animação CSS a cada reveal. -->
              @for (key of [floatKey()]; track key) {
                <span class="points-float">+{{ earnedPoints() }}</span>
              }
            }
          </span>
          <button class="button button-ghost" (click)="leaveRoom()" type="button">Sair</button>
        </div>
      </div>

      @if (store.error() || feedback()) {
        <p [class]="store.error() ? 'error-message' : 'success-message'">{{ store.error() || feedback() }}</p>
      }

      @let game = state();
      @if (!game) {
        <app-loading-state />
      } @else {
        @if (game.status === 'lobby') {
          <div class="empty-state">
            <span class="empty-icon glow-element">02</span>
            <h2>Você entrou!</h2>
            <p>Aguarde o host começar o quiz.</p>
          </div>
        }

        @if (game.currentQuestion; as current) {
          @if (game.status === 'question' || game.status === 'reveal') {
            <div class="question-view">
              <div class="question-meta">
                <p class="eyebrow">Pergunta {{ game.currentQuestionPosition }} de {{ game.totalQuestions }}</p>
                @if (game.status === 'question') {
                  <app-countdown [deadlineAt]="game.deadlineAt" (expired)="timeExpired.set(true)" />
                }
              </div>
              <h2 class="question-title">{{ current.prompt }}</h2>
              <div class="choice-grid">
                @for (choice of choices(); track choice.id) {
                  @let correct = game.status === 'reveal' && current.correctChoiceId === choice.id;
                  <button
                    class="choice-card choice-button"
                    [class.choice-selected]="selected() === choice.id"
                    [class.choice-correct]="correct"
                    [class.choice-wrong]="game.status === 'reveal' && selected() === choice.id && !correct"
                    [disabled]="answered() || game.status !== 'question' || timeExpired()"
                    (click)="answer(choice.id)"
                    type="button"
                  >
                    <span class="choice-index">{{ choice.position }}</span>
                    <span class="choice-body">
                      <span>{{ choice.label }}</span>
                      @if (showVotes()) {
                        <span class="vote-row">
                          <span class="vote-track">
                            <span class="vote-fill" [style.width.%]="share(choice.id)"></span>
                          </span>
                          <span class="vote-pct">{{ share(choice.id) }}%</span>
                        </span>
                      }
                    </span>
                  </button>
                }
              </div>
              @if (game.status === 'question' && answered()) {
                <p class="waiting-note">Aguardando os outros jogadores...</p>
              }
            </div>
          }
        }

        @if (game.status === 'finished') {
          <div class="empty-state empty-state-compact">
            <span class="empty-icon">🏆</span>
            <h2>Fim de jogo</h2>
            <p>Parabéns! Veja o Top 3 do quiz:</p>
            <app-podium [players]="game.players" [highlightPlayerId]="playerId()" />
          </div>
        }
      }
    </section>
  `,
})
export class PlayerGame {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);
  protected readonly store = inject(GameStateStore);

  readonly code = input.required<string>();
  readonly playerId = input.required<string>();

  protected readonly state = this.store.state;
  protected readonly selected = signal('');
  protected readonly submitted = signal(false);
  protected readonly feedback = signal('');
  protected readonly timeExpired = signal(false);
  protected readonly scoreAnimating = signal(false);
  protected readonly earnedPoints = signal(0);
  protected readonly floatKey = signal(0);

  private readonly questionId = computed(() => this.state()?.currentQuestion?.id ?? '');
  protected readonly player = computed(() => this.state()?.players.find((item) => item.id === this.playerId()));
  /**
   * Já respondeu a pergunta atual: pelo envio feito nesta tela ou pelo servidor, que mantém a
   * informação quando a página é recarregada no meio da pergunta.
   */
  protected readonly answered = computed(() => this.submitted() || !!this.player()?.hasAnswered);
  protected readonly choices = computed(() => {
    const current = this.state()?.currentQuestion;
    return current ? shuffleChoices(current.choices, `${this.playerId() || 'player'}:${current.id}`) : [];
  });
  protected readonly showVotes = computed(
    () =>
      this.state()?.status === 'question' &&
      this.answered() &&
      totalVotes(this.state()?.currentQuestion?.answerCounts) > 0,
  );

  private previousScore = 0;
  private animationTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    // Nova pergunta: limpa a seleção da rodada anterior.
    effect(() => {
      if (!this.questionId()) return;
      untracked(() => {
        this.selected.set('');
        this.submitted.set(false);
        this.feedback.set('');
        this.timeExpired.set(false);
      });
    });

    // Anima os pontos ganhos quando a resposta é revelada. A diferença é medida a cada push de
    // estado (não só no reveal); senão, outro push durante o reveal repetiria a animação.
    effect(() => {
      const status = this.state()?.status;
      const currentScore = this.player()?.score ?? 0;
      const gained = currentScore - this.previousScore;
      this.previousScore = currentScore;
      if (status !== 'reveal' || gained <= 0) return;
      untracked(() => {
        this.earnedPoints.set(gained);
        this.floatKey.update((key) => key + 1);
        this.scoreAnimating.set(true);
      });
      clearTimeout(this.animationTimer);
      this.animationTimer = setTimeout(() => this.scoreAnimating.set(false), 800);
    });
  }

  protected share(choiceId: string): number {
    return voteShare(this.state()?.currentQuestion?.answerCounts, choiceId);
  }

  protected async answer(choiceId: string): Promise<void> {
    if (this.answered() || this.timeExpired() || this.state()?.status !== 'question') return;
    this.selected.set(choiceId);
    try {
      await this.api.answer(this.code(), choiceId);
      this.submitted.set(true);
      this.feedback.set('Resposta enviada');
    } catch (cause) {
      this.feedback.set(errorMessage(cause, 'Não foi possível enviar a resposta.'));
      this.selected.set('');
    }
  }

  protected async leaveRoom(): Promise<void> {
    if (!confirm('Sair da sala? Você não receberá mais atualizações do quiz.')) return;
    await this.api.leave(this.code()).catch(() => undefined);
    await this.router.navigateByUrl('/');
  }
}
