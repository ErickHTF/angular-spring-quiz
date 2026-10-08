import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-brand',
  imports: [RouterLink],
  template: `
    <a class="brand" routerLink="/">
      <span class="brand-mark">F</span>
      <span class="brand-name">Fresh Quiz</span>
    </a>
  `,
})
export class Brand {}
