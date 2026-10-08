import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { GameApi } from './game-api';
import type { GameState } from './models';

export type ConnectionStatus = 'connecting' | 'online' | 'offline';

/**
 * Mantém o estado da partida sincronizado via SSE.
 * Fornecido pela página da sala: os componentes filhos (jogo e ranking)
 * compartilham a mesma conexão, que fecha quando a página é destruída.
 */
@Injectable()
export class GameStateStore implements OnDestroy {
  private readonly api = inject(GameApi);
  private source: EventSource | null = null;

  readonly state = signal<GameState | null>(null);
  readonly error = signal('');
  readonly connection = signal<ConnectionStatus>('connecting');
  readonly lastEventAt = signal<number | null>(null);

  connect(code: string): void {
    this.source?.close();
    const source = new EventSource(`/api/games/${code}/events`);

    source.onopen = () => {
      this.connection.set('online');
      this.error.set('');
      void this.loadState(code);
    };
    source.addEventListener('state', (event) => {
      this.applyState(JSON.parse((event as MessageEvent<string>).data) as GameState);
    });
    source.onerror = () => {
      this.connection.set('offline');
      this.error.set('A conexão foi interrompida. Tentando reconectar...');
    };

    this.source = source;
  }

  ngOnDestroy(): void {
    this.source?.close();
    this.source = null;
  }

  private applyState(next: GameState): void {
    this.state.set(next);
    this.lastEventAt.set(Date.now());
  }

  private async loadState(code: string): Promise<void> {
    try {
      this.applyState(await this.api.state(code));
    } catch {
      this.error.set('Não foi possível carregar a partida.');
    }
  }
}
