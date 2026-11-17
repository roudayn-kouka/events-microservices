import { Routes } from '@angular/router';
import { TableauDeBordComponent } from './features/tableau-de-bord.component';
import { EvenementsComponent } from './features/evenements.component';
import { DetailEvenementComponent } from './features/detail-evenement.component';
import { ParticipantsComponent } from './features/participants.component';
import { LogistiqueComponent } from './features/logistique.component';
import { AvisComponent } from './features/avis.component';

export const routes: Routes = [
  { path: 'statistiques', component: TableauDeBordComponent, title: 'Statistiques · Events' },
  { path: 'evenements', component: EvenementsComponent, title: 'Événements · Events' },
  { path: 'evenements/:id', component: DetailEvenementComponent, title: 'Détail · Events' },
  { path: 'participants', component: ParticipantsComponent, title: 'Participants · Events' },
  { path: 'logistique', component: LogistiqueComponent, title: 'Logistique · Events' },
  { path: 'avis', component: AvisComponent, title: 'Avis · Events' },
  { path: '', pathMatch: 'full', redirectTo: 'statistiques' },
  { path: '**', redirectTo: 'statistiques' },
];
