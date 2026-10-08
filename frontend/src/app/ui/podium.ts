import { Component, computed, input } from '@angular/core';
import type { PlayerSummary } from '../core/models';

const PLACE_CLASSES = ['podium-first', 'podium-second', 'podium-third'];

@Component({
  selector: 'app-podium',
  template: `
    <div class="podium">
      @for (player of top(); track player.id; let index = $index) {
        <div
          class="podium-item {{ placeClasses[index] }}"
          [class.podium-current]="player.id === highlightPlayerId()"
        >
          <span class="podium-medal">{{ index + 1 }}</span>
          <span class="podium-name">{{ player.nickname }}</span>
          <span class="podium-score">{{ player.score }} pts</span>
        </div>
      }
    </div>
  `,
})
export class Podium {
  readonly players = input.required<PlayerSummary[]>();
  readonly highlightPlayerId = input<string | null>(null);
  protected readonly top = computed(() => this.players().slice(0, 3));
  protected readonly placeClasses = PLACE_CLASSES;
}
