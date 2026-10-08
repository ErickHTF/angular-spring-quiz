import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GameApi, errorMessage } from '../core/game-api';
import { Brand } from '../ui/brand';

@Component({
  selector: 'app-home-page',
  imports: [Brand, FormsModule, RouterLink],
  template: `
    <main class="page-background">
      <div class="shell">
        <header class="home-header">
          <app-brand />
          <a class="home-link" routerLink="/host">Criar partida</a>
        </header>
        <section class="hero-grid">
          <div class="hero-copy">
            <p class="eyebrow">Aprendizado em tempo real</p>
            <h1>Conhecimento que vira <span class="text-accent text-gradient">jogo.</span></h1>
            <p class="hero-description">
              Um quiz colaborativo sobre desenvolvimento web, feito com Angular, Spring Boot e PostgreSQL.
            </p>
            <div class="hero-stats">
              <span><strong>16</strong> perguntas</span>
              <span><strong>20s</strong> por rodada</span>
            </div>
          </div>

          <form class="island island-join" (ngSubmit)="join()">
            <div>
              <p class="eyebrow">Entrar em uma partida</p>
              <h2 class="island-title">Pronto para jogar?</h2>
              <p class="island-copy">Use o código compartilhado pelo host e escolha um apelido.</p>
            </div>
            <label class="field-label">
              Código da sala
              <input
                class="field field-code"
                maxlength="6"
                name="code"
                placeholder="ABC123"
                [ngModel]="code()"
                (ngModelChange)="code.set($event.toUpperCase())"
                required
              />
            </label>
            <label class="field-label">
              Seu apelido
              <input
                class="field"
                maxlength="24"
                name="nickname"
                placeholder="Como devemos chamar você?"
                [(ngModel)]="nickname"
                required
              />
            </label>
            @if (error()) {
              <p class="error-message">{{ error() }}</p>
            }
            <button class="button button-primary button-block" [disabled]="loading()" type="submit">
              {{ loading() ? 'Entrando...' : 'Entrar na sala' }}
            </button>
          </form>
        </section>
      </div>
    </main>
  `,
})
export class HomePage {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);

  protected readonly code = signal('');
  protected nickname = '';
  protected readonly error = signal('');
  protected readonly loading = signal(false);

  protected async join(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const result = await this.api.join(this.code().trim().toUpperCase(), this.nickname);
      await this.router.navigate(['/play', result.code]);
    } catch (cause) {
      this.error.set(errorMessage(cause, 'Não foi possível entrar.'));
    } finally {
      this.loading.set(false);
    }
  }
}
