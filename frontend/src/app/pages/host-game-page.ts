import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { GameApi } from '../core/game-api';
import { GameStateStore } from '../core/game-state.store';
import { HostGame } from '../game/host-game';
import { Ranking } from '../game/ranking';
import { DevToolsGuide } from '../ui/devtools-guide';

@Component({
  selector: 'app-host-game-page',
  imports: [DevToolsGuide, HostGame, Ranking],
  providers: [GameStateStore],
  template: `
    <main class="page-background">
      <div class="shell">
        <div class="game-layout">
          <app-devtools-guide />
          @if (ready()) {
            <app-host-game [code]="roomCode()" />
            <app-ranking />
          }
        </div>
      </div>
    </main>
  `,
})
export class HostGamePage implements OnInit {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);
  private readonly store = inject(GameStateStore);
  private readonly title = inject(Title);

  /** Parâmetro `:code` da rota (withComponentInputBinding). */
  readonly code = input.required<string>();
  protected readonly roomCode = signal('');
  protected readonly ready = signal(false);

  async ngOnInit(): Promise<void> {
    const code = this.code().toUpperCase();
    this.roomCode.set(code);
    this.title.setTitle(`Sala ${code} | Fresh Quiz`);
    const me = await this.api.me(code).catch(() => null);
    if (me?.role !== 'host') {
      await this.router.navigateByUrl('/host');
      return;
    }
    this.store.connect(code);
    this.ready.set(true);
  }
}
