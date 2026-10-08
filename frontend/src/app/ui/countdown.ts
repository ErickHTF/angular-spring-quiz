import { Component, effect, input, output, signal } from '@angular/core';

@Component({
  selector: 'app-countdown',
  template: `
    <div class="timer" [class.timer-warning]="remaining() <= 5" role="timer">{{ remaining() }}s</div>
  `,
})
export class Countdown {
  readonly deadlineAt = input<string | null>(null);
  readonly expired = output<void>();
  protected readonly remaining = signal(0);

  constructor() {
    effect((onCleanup) => {
      const deadlineAt = this.deadlineAt();
      let hasExpired = false;

      const update = () => {
        if (!deadlineAt) return;
        const next = Math.max(0, Math.ceil((new Date(deadlineAt).getTime() - Date.now()) / 1000));
        this.remaining.set(next);
        if (next === 0 && !hasExpired) {
          hasExpired = true;
          this.expired.emit();
        }
      };

      update();
      const timer = setInterval(update, 250);
      onCleanup(() => clearInterval(timer));
    });
  }
}
