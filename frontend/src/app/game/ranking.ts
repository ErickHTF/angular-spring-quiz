import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { GameStateStore } from '../core/game-state.store';
import type { PlayerSummary } from '../core/models';

const NO_PLAYERS: PlayerSummary[] = [];

@Component({
  selector: 'app-ranking',
  template: `
    <aside class="island island-ranking ranking-island">
      <div class="ranking-heading">
        <div>
          <p class="eyebrow">Placar ao vivo</p>
          <h2>Ranking</h2>
        </div>
        <span class="ranking-connection ranking-{{ store.connection() }}">{{ connectionLabel() }}</span>
      </div>

      @if (store.error()) {
        <p class="error-message">{{ store.error() }}</p>
      }

      @if (hostNickname(); as host) {
        <section class="ranking-group">
          <p class="ranking-group-label">Host</p>
          <div class="host-card">
            <span class="host-avatar">{{ host.slice(0, 1) }}</span>
            <span class="host-name">{{ host }}</span>
            <span class="host-tag">Host</span>
          </div>
        </section>
      }

      <section class="ranking-group">
        <p class="ranking-group-label">Jogadores</p>
        @if (players().length === 0) {
          <p class="ranking-empty">Aguardando jogadores...</p>
        }
        <ol class="ranking-list">
          @for (player of players(); track player.id; let index = $index) {
            <li
              class="rank-row"
              [class.rank-row-current]="player.id === highlightPlayerId()"
              [class.row-slide-up]="slideUp().has(player.id)"
              [class.row-glow]="glow().has(player.id)"
            >
              <span class="rank-number">{{ index + 1 }}</span>
              <span class="rank-name">{{ player.nickname }}</span>
              <span class="rank-score" [class.rank-pop]="glow().has(player.id)">{{ player.score }}</span>
            </li>
          }
        </ol>
      </section>
    </aside>
  `,
})
export class Ranking {
  protected readonly store = inject(GameStateStore);
  readonly highlightPlayerId = input<string | null>(null);

  protected readonly players = computed(() => this.store.state()?.players ?? NO_PLAYERS);
  protected readonly hostNickname = computed(() => this.store.state()?.hostNickname ?? null);
  protected readonly connectionLabel = computed(() => {
    const connection = this.store.connection();
    return connection === 'online' ? 'LIVE' : connection === 'connecting' ? '...' : 'OFF';
  });

  /** Quem subiu de posição e quem pontuou no último reveal, para animar as linhas. */
  protected readonly slideUp = signal<Set<string>>(new Set());
  protected readonly glow = signal<Set<string>>(new Set());
  private previousPositions = new Map<string, number>();
  private previousScores = new Map<string, number>();

  constructor() {
    effect(() => {
      const status = this.store.state()?.status;
      const players = this.players();

      untracked(() => {
        if (status === 'question') {
          if (this.slideUp().size > 0) this.slideUp.set(new Set());
          if (this.glow().size > 0) this.glow.set(new Set());
        }

        if (status === 'reveal') {
          const slidUp = new Set<string>();
          const glowing = new Set<string>();
          players.forEach((player, index) => {
            if (index < (this.previousPositions.get(player.id) ?? index)) slidUp.add(player.id);
            if (player.score > (this.previousScores.get(player.id) ?? 0)) glowing.add(player.id);
          });
          if (slidUp.size > 0) this.slideUp.set(slidUp);
          if (glowing.size > 0) this.glow.set(glowing);
        }

        this.previousPositions = new Map(players.map((player, index) => [player.id, index]));
        this.previousScores = new Map(players.map((player) => [player.id, player.score]));
      });
    });
  }
}
