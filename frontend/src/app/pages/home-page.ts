import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GameApi, errorMessage } from '../core/game-api';
import type { QuizSummary } from '../core/models';
import { Brand } from '../ui/brand';

@Component({
  selector: 'app-home-page',
  imports: [Brand, FormsModule, RouterLink],
  template: `
    <main class="page-background">
      <div class="shell">
        <header class="home-header">
          <app-brand />
          <nav class="home-nav" aria-label="Navegação principal">
            <a class="home-link" routerLink="/quizzes">Meus quizzes</a>
            <a class="home-link" routerLink="/host">Criar partida</a>
          </nav>
        </header>
        <section class="hero-grid">
          <div class="hero-copy">
            <p class="eyebrow">Aprendizado em tempo real</p>
            <h1>Conhecimento que vira <span class="text-accent text-gradient">jogo.</span></h1>
            <p class="hero-description">
              Crie uma sala, compartilhe o código e dispute com a turma em perguntas cronometradas, com placar ao vivo.
              Use o quiz de fundamentos da web ou monte o seu.
            </p>
            <div class="hero-stats">
              <span><strong>{{ quizCount() }}</strong> {{ quizCount() === 1 ? 'quiz' : 'quizzes' }}</span>
              <span><strong>{{ questionCount() }}</strong> perguntas</span>
              <span><strong>1500</strong> pontos por acerto rápido</span>
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

        <section class="home-actions" aria-label="O que você quer fazer?">
          <a class="island home-action home-action-host" routerLink="/host">
            <p class="eyebrow">Anfitrião</p>
            <h2 class="island-title">Criar partida</h2>
            <p class="island-copy">Escolha um quiz, abra a sala e conduza as perguntas acompanhando os votos.</p>
          </a>
          <a class="island home-action home-action-quiz" routerLink="/quizzes">
            <p class="eyebrow">Conteúdo</p>
            <h2 class="island-title">Gerenciar quizzes</h2>
            <p class="island-copy">Veja os quizzes disponíveis e edite ou exclua os que você criou.</p>
          </a>
          <a class="island home-action home-action-new" routerLink="/quizzes/new">
            <p class="eyebrow">Perguntas</p>
            <h2 class="island-title">Novo quiz</h2>
            <p class="island-copy">Monte perguntas com 2 a 4 alternativas, a resposta correta e o tempo de cada uma.</p>
          </a>
        </section>
      </div>
    </main>
  `,
})
export class HomePage implements OnInit {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);

  protected readonly code = signal('');
  protected nickname = '';
  protected readonly error = signal('');
  protected readonly loading = signal(false);
  protected readonly quizzes = signal<QuizSummary[]>([]);
  protected readonly quizCount = computed(() => this.quizzes().length);
  protected readonly questionCount = computed(() =>
    this.quizzes().reduce((total, quiz) => total + quiz.questionCount, 0),
  );

  async ngOnInit(): Promise<void> {
    try {
      this.quizzes.set(await this.api.listQuizzes());
    } catch {
      // Os números do hero são decorativos; a home continua funcionando sem a API.
    }
  }

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
