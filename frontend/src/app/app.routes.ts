import { Routes } from '@angular/router';
import { HomePage } from './pages/home-page';
import { HostGamePage } from './pages/host-game-page';
import { HostLandingPage } from './pages/host-landing-page';
import { PlayerGamePage } from './pages/player-game-page';

export const routes: Routes = [
  { path: '', component: HomePage, title: 'Fresh Quiz | Aprenda jogando' },
  { path: 'host', component: HostLandingPage, title: 'Criar partida | Fresh Quiz' },
  { path: 'host/:code', component: HostGamePage },
  { path: 'play/:code', component: PlayerGamePage },
  { path: '**', redirectTo: '' },
];
