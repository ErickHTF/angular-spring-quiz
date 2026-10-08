import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { GameApi } from '../core/game-api';
import { GameStateStore } from '../core/game-state.store';
import { PlayerGame } from '../game/player-game';
import { Ranking } from '../game/ranking';
import { DevToolsGuide } from '../ui/devtools-guide';

@Component({
  selector: 'app-player-game-page',
  imports: [DevToolsGuide, PlayerGame, Ranking],
  providers: [GameStateStore],
  template: `
    <main class="page-background">
      <div class="shell">
        <div class="game-layout">
          <app-devtools-guide />
          @if (playerId(); as id) {
            <app-player-game [code]="roomCode()" [playerId]="id" />
            <app-ranking [highlightPlayerId]="id" />
          }
        </div>
      </div>
    </main>
  `,
})
export class PlayerGamePage implements OnInit {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);
  private readonly store = inject(GameStateStore);
  private readonly title = inject(Title);

  /** Parâmetro `:code` da rota (withComponentInputBinding). */
  readonly code = input.required<string>();
  protected readonly roomCode = signal('');
  protected readonly playerId = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const code = this.code().toUpperCase();
    this.roomCode.set(code);
    this.title.setTitle(`Jogar ${code} | Fresh Quiz`);
    // O cookie da sessão é HttpOnly: quem é o jogador vem do servidor.
    const me = await this.api.me(code).catch(() => null);
    if (me?.role !== 'player' || !me.playerId) {
      await this.router.navigateByUrl('/');
      return;
    }
    this.store.connect(code);
    this.playerId.set(me.playerId);
  }
}
