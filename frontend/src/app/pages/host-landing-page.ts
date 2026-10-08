import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { GameApi, errorMessage } from '../core/game-api';
import { Brand } from '../ui/brand';

@Component({
  selector: 'app-host-landing-page',
  imports: [Brand, FormsModule],
  template: `
    <main class="page-background">
      <div class="shell shell-small">
        <app-brand />
        <div class="landing-shell">
          <form class="island island-host" (ngSubmit)="createGame()">
            <div>
              <p class="eyebrow">Modo host</p>
              <h1 class="island-title island-title-lg">Crie uma sala e compartilhe o código.</h1>
              <p class="island-copy island-copy-lg">Seu nome aparecerá em destaque para os jogadores.</p>
            </div>
            <label class="field-label">
              Seu nome
              <input
                class="field"
                maxlength="24"
                name="nickname"
                placeholder="Como os jogadores devem te chamar?"
                [(ngModel)]="nickname"
                required
              />
            </label>
            @if (error()) {
              <p class="error-message">{{ error() }}</p>
            }
            <button class="button button-primary button-block" [disabled]="loading()" type="submit">
              {{ loading() ? 'Criando sala...' : 'Criar nova sala' }}
            </button>
          </form>
        </div>
      </div>
    </main>
  `,
})
export class HostLandingPage {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);

  protected nickname = '';
  protected readonly error = signal('');
  protected readonly loading = signal(false);

  protected async createGame(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const { code } = await this.api.createGame(this.nickname);
      await this.router.navigate(['/host', code]);
    } catch (cause) {
      this.error.set(errorMessage(cause, 'Não foi possível criar a sala.'));
      this.loading.set(false);
    }
  }
}
