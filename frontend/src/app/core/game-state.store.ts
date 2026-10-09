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

  connect(code: string): void {
    this.source?.close();
    const source = new EventSource(`/api/games/${code}/events`);

    // O servidor já envia o estado assim que o stream abre (inclusive em cada reconexão);
    // o GET /state é redundante, mantido do original como segurança caso esse evento falhe.
    source.onopen = () => {
      this.connection.set('online');
      this.error.set('');
      void this.loadState(code);
    };
    source.addEventListener('state', (event) => {
      this.state.set(JSON.parse((event as MessageEvent<string>).data) as GameState);
    });
    // O EventSource reconecta sozinho em quedas de rede; se o servidor responder com erro HTTP
    // (ex.: 401 com a sessão expirada), ele desiste e a mensagem abaixo fica na tela.
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

  private async loadState(code: string): Promise<void> {
    try {
      this.state.set(await this.api.state(code));
    } catch {
      this.error.set('Não foi possível carregar a partida.');
    }
  }
}
