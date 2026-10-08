import { Component } from '@angular/core';

@Component({
  selector: 'app-loading-state',
  template: `
    <div class="empty-state">
      <span class="loader"></span>
      <p>Carregando sala...</p>
    </div>
  `,
})
export class LoadingState {}
