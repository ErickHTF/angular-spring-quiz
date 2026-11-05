import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GameApi, errorMessage } from '../core/game-api';
import {
  QUIZ_LIMITS,
  QuizDraft,
  addChoice,
  addQuestion,
  emptyQuiz,
  fromQuizDetails,
  moveQuestion,
  removeChoice,
  removeQuestion,
  setCorrectChoice,
  toQuizPayload,
  updateChoiceLabel,
  updateQuestion,
  validateQuiz,
} from '../core/quiz-draft';
import { Brand } from '../ui/brand';

@Component({
  selector: 'app-quiz-builder-page',
  imports: [Brand, FormsModule, RouterLink],
  template: `
    <main class="page-background">
      <div class="shell shell-small">
        <header class="home-header">
          <app-brand />
          <a class="home-link" routerLink="/quizzes">Voltar aos quizzes</a>
        </header>
        <form class="island island-quiz quiz-builder" (ngSubmit)="save()">
          <div>
            <p class="eyebrow">{{ !isEditing() ? 'Quiz personalizado' : canEdit() ? 'Editar quiz' : 'Visualizar quiz' }}</p>
            <h1 class="island-title island-title-lg">
              {{ !isEditing() ? 'Monte as suas perguntas.' : canEdit() ? 'Edite o seu quiz.' : 'Perguntas do quiz.' }}
            </h1>
            <p class="island-copy island-copy-lg">
              Cada pergunta tem de {{ limits.choicesMin }} a {{ limits.choicesMax }} alternativas e uma única resposta
              correta. Depois de salvar, ele aparece em Meus quizzes e na criação da sala.
            </p>
          </div>

          <label class="field-label">
            Título do quiz
            <input
              class="field"
              [attr.maxlength]="limits.titleMax"
              name="title"
              placeholder="Ex.: Fundamentos de Java"
              [value]="draft().title"
              [disabled]="loading() || !canEdit()"
              (input)="setTitle(valueOf($event))"
            />
          </label>

          <div class="question-list">
            @for (question of draft().questions; track question.key; let qi = $index) {
              <section class="question-card" [attr.aria-label]="'Pergunta ' + (qi + 1)">
                <div class="question-card-head">
                  <h2 class="question-card-title">Pergunta {{ qi + 1 }}</h2>
                  <div class="question-card-actions">
                    <button
                      class="button button-ghost"
                      type="button"
                      [disabled]="loading() || !canEdit() || qi === 0"
                      [attr.aria-label]="'Subir a pergunta ' + (qi + 1)"
                      (click)="move(qi, -1)"
                    >
                      Subir
                    </button>
                    <button
                      class="button button-ghost"
                      type="button"
                      [disabled]="loading() || !canEdit() || qi === draft().questions.length - 1"
                      [attr.aria-label]="'Descer a pergunta ' + (qi + 1)"
                      (click)="move(qi, 1)"
                    >
                      Descer
                    </button>
                    <button
                      class="button button-ghost"
                      type="button"
                      [disabled]="loading() || !canEdit() || draft().questions.length === 1"
                      [attr.aria-label]="'Remover a pergunta ' + (qi + 1)"
                      (click)="remove(qi)"
                    >
                      Remover
                    </button>
                  </div>
                </div>

                <label class="field-label">
                  Enunciado
                  <textarea
                    class="field"
                    [attr.maxlength]="limits.promptMax"
                    [name]="'prompt-' + question.key"
                    placeholder="Qual é a pergunta?"
                    [value]="question.prompt"
                    [disabled]="loading() || !canEdit()"
                    (input)="setPrompt(qi, valueOf($event))"
                  ></textarea>
                </label>

                <label class="field-label">
                  Tempo para responder (segundos)
                  <input
                    class="field field-duration"
                    type="number"
                    step="1"
                    [attr.min]="limits.durationMin"
                    [attr.max]="limits.durationMax"
                    [name]="'duration-' + question.key"
                    [value]="question.durationSeconds"
                    [disabled]="loading() || !canEdit()"
                    (input)="setDuration(qi, $event)"
                  />
                </label>

                <fieldset class="choice-editor">
                  <legend class="field-label">Alternativas (marque a correta)</legend>
                  @for (choice of question.choices; track choice.key; let ci = $index) {
                    <div class="choice-row">
                      <input
                        class="choice-radio"
                        type="radio"
                        [name]="'correct-' + question.key"
                        [checked]="choice.isCorrect"
                        [disabled]="loading() || !canEdit()"
                        [attr.aria-label]="'Alternativa ' + (ci + 1) + ' é a correta'"
                        (change)="markCorrect(qi, ci)"
                      />
                      <input
                        class="field"
                        [attr.maxlength]="limits.labelMax"
                        [name]="'choice-' + choice.key"
                        [attr.aria-label]="'Texto da alternativa ' + (ci + 1)"
                        [placeholder]="'Alternativa ' + (ci + 1)"
                        [value]="choice.label"
                        [disabled]="loading() || !canEdit()"
                        (input)="setChoiceLabel(qi, ci, valueOf($event))"
                      />
                      <button
                        class="button button-ghost"
                        type="button"
                        [disabled]="loading() || !canEdit() || question.choices.length <= limits.choicesMin"
                        [attr.aria-label]="'Remover a alternativa ' + (ci + 1)"
                        (click)="removeAlternative(qi, ci)"
                      >
                        Remover
                      </button>
                    </div>
                  }
                  @if (question.choices.length < limits.choicesMax) {
                    <button
                      class="button button-ghost"
                      type="button"
                      [disabled]="loading() || !canEdit()"
                      (click)="addAlternative(qi)"
                    >
                      Adicionar alternativa
                    </button>
                  }
                </fieldset>
              </section>
            }
          </div>

          <button
            class="button button-ghost button-block"
            type="button"
            [disabled]="loading() || !canEdit() || draft().questions.length >= limits.questionsMax"
            (click)="add()"
          >
            Adicionar pergunta ({{ draft().questions.length }}/{{ limits.questionsMax }})
          </button>

          @if (error()) {
            <p class="error-message" role="alert">{{ error() }}</p>
          }
          <button class="button button-primary button-block" [disabled]="saving() || loading() || !canEdit()" type="submit">
            {{ saving() ? 'Salvando...' : isEditing() ? 'Salvar alterações' : 'Salvar quiz' }}
          </button>
          @if (isEditing() && canEdit()) {
            <button
              class="button button-ghost button-block"
              [disabled]="deleting() || saving() || loading()"
              type="button"
              (click)="deleteQuiz()"
            >
              {{ deleting() ? 'Excluindo quiz...' : 'Excluir quiz' }}
            </button>
          }
        </form>
      </div>
    </main>
  `,
})
export class QuizBuilderPage implements OnInit {
  private readonly api = inject(GameApi);
  private readonly router = inject(Router);

  readonly id = input<string>();
  protected readonly isEditing = computed(() => !!this.id());
  protected readonly limits = QUIZ_LIMITS;
  protected readonly draft = signal<QuizDraft>(emptyQuiz());
  protected readonly error = signal('');
  protected readonly saving = signal(false);
  protected readonly deleting = signal(false);
  protected readonly loading = signal(false);
  protected readonly canEdit = signal(true);

  async ngOnInit(): Promise<void> {
    const quizId = this.id();
    if (!quizId) return;

    this.loading.set(true);
    this.error.set('');
    try {
      const details = await this.api.getQuiz(quizId);
      this.canEdit.set(details.canEdit);
      if (!details.canEdit) {
        this.error.set('Somente leitura: apenas quem criou este quiz pode alterá-lo.');
      }
      this.draft.set(fromQuizDetails(details));
    } catch (cause) {
      this.error.set(errorMessage(cause, 'Não foi possível carregar o quiz.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLTextAreaElement).value;
  }

  protected setTitle(title: string): void {
    this.draft.update((draft) => ({ ...draft, title }));
  }

  protected setPrompt(index: number, prompt: string): void {
    this.draft.update((draft) => updateQuestion(draft, index, { prompt }));
  }

  protected setDuration(index: number, event: Event): void {
    const durationSeconds = (event.target as HTMLInputElement).valueAsNumber;
    this.draft.update((draft) => updateQuestion(draft, index, { durationSeconds }));
  }

  protected setChoiceLabel(questionIndex: number, choiceIndex: number, label: string): void {
    this.draft.update((draft) => updateChoiceLabel(draft, questionIndex, choiceIndex, label));
  }

  protected markCorrect(questionIndex: number, choiceIndex: number): void {
    this.draft.update((draft) => setCorrectChoice(draft, questionIndex, choiceIndex));
  }

  protected add(): void {
    this.draft.update(addQuestion);
  }

  protected remove(index: number): void {
    this.draft.update((draft) => removeQuestion(draft, index));
  }

  protected move(index: number, offset: -1 | 1): void {
    this.draft.update((draft) => moveQuestion(draft, index, offset));
  }

  protected addAlternative(questionIndex: number): void {
    this.draft.update((draft) => addChoice(draft, questionIndex));
  }

  protected removeAlternative(questionIndex: number, choiceIndex: number): void {
    this.draft.update((draft) => removeChoice(draft, questionIndex, choiceIndex));
  }

  protected async save(): Promise<void> {
    const problem = validateQuiz(this.draft());
    if (problem) {
      this.error.set(problem);
      return;
    }
    this.saving.set(true);
    this.error.set('');
    try {
      const quizId = this.id();
      if (quizId) {
        await this.api.updateQuiz(quizId, toQuizPayload(this.draft()));
      } else {
        await this.api.createQuiz(toQuizPayload(this.draft()));
      }
      await this.router.navigate(['/quizzes']);
    } catch (cause) {
      this.error.set(errorMessage(cause, 'Não foi possível salvar o quiz.'));
      this.saving.set(false);
    }
  }

  protected async deleteQuiz(): Promise<void> {
    const quizId = this.id();
    if (!quizId) return;

    this.deleting.set(true);
    this.error.set('');
    try {
      await this.api.deleteQuiz(quizId);
      await this.router.navigate(['/quizzes']);
    } catch (cause) {
      this.error.set(errorMessage(cause, 'Não foi possível excluir o quiz.'));
      this.deleting.set(false);
    }
  }
}
