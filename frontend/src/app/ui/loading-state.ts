import { Component, input } from '@angular/core';

@Component({
  selector: 'app-loading-state',
  template: `
    <div class="empty-state">
      <span class="loader"></span>
      <p>{{ message() }}</p>
    </div>
  `,
})
export class LoadingState {
  readonly message = input('Carregando sala...');
}
