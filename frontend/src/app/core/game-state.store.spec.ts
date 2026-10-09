import { TestBed } from '@angular/core/testing';
import { GameApi } from './game-api';
import { GameStateStore } from './game-state.store';
import type { GameState } from './models';

class FakeEventSource {
  static last: FakeEventSource;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  private listeners = new Map<string, (event: MessageEvent<string>) => void>();

  constructor(readonly url: string) {
    FakeEventSource.last = this;
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void {
    this.listeners.set(type, listener);
  }

  emit(type: string, data: unknown): void {
    this.listeners.get(type)?.({ data: JSON.stringify(data) } as MessageEvent<string>);
  }

  close(): void {
    this.closed = true;
  }
}

const lobby: GameState = {
  code: 'ABC234',
  status: 'lobby',
  hostNickname: 'Host',
  currentQuestion: null,
  currentQuestionPosition: 0,
  totalQuestions: 16,
  deadlineAt: null,
  players: [],
};

describe('GameStateStore', () => {
  let store: GameStateStore;
  const api = { state: vi.fn() };

  beforeEach(() => {
    vi.stubGlobal('EventSource', FakeEventSource);
    api.state.mockResolvedValue({ ...lobby, players: [{ id: 'p1', nickname: 'Ana', score: 0, hasAnswered: false }] });
    TestBed.configureTestingModule({ providers: [GameStateStore, { provide: GameApi, useValue: api }] });
    store = TestBed.inject(GameStateStore);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('applies states pushed through SSE', () => {
    store.connect('ABC234');
    expect(FakeEventSource.last.url).toBe('/api/games/ABC234/events');
    FakeEventSource.last.emit('state', lobby);
    expect(store.state()).toEqual(lobby);
  });

  it('reloads the state on (re)connection and tracks status', async () => {
    store.connect('ABC234');
    FakeEventSource.last.onerror?.();
    expect(store.connection()).toBe('offline');
    expect(store.error()).toContain('Tentando reconectar');

    FakeEventSource.last.onopen?.();
    await Promise.resolve();
    expect(store.connection()).toBe('online');
    expect(store.error()).toBe('');
    await vi.waitFor(() => expect(store.state()?.players).toHaveLength(1));
  });

  it('closes the stream when destroyed', () => {
    store.connect('ABC234');
    const source = FakeEventSource.last;
    store.ngOnDestroy();
    expect(source.closed).toBe(true);
  });
});
