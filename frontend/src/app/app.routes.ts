import { Routes } from '@angular/router';
import { HomePage } from './pages/home-page';
import { HostGamePage } from './pages/host-game-page';
import { HostLandingPage } from './pages/host-landing-page';
import { PlayerGamePage } from './pages/player-game-page';
import { QuizBuilderPage } from './pages/quiz-builder-page';
import { QuizListPage } from './pages/quiz-list-page';

export const routes: Routes = [
  { path: '', component: HomePage, title: 'Fresh Quiz | Aprenda jogando' },
  { path: 'host', component: HostLandingPage, title: 'Criar partida | Fresh Quiz' },
  { path: 'host/:code', component: HostGamePage },
  { path: 'quizzes', component: QuizListPage, title: 'Meus quizzes | Fresh Quiz' },
  { path: 'quizzes/new', component: QuizBuilderPage, title: 'Criar quiz | Fresh Quiz' },
  { path: 'quizzes/:id/edit', component: QuizBuilderPage, title: 'Editar quiz | Fresh Quiz' },
  { path: 'play/:code', component: PlayerGamePage },
  { path: '**', redirectTo: '' },
];
