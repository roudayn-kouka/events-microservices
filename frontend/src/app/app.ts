import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class AppComponent {
  readonly liens = [
    { route: '/statistiques', libelle: 'Statistiques' },
    { route: '/evenements', libelle: 'Événements' },
    { route: '/participants', libelle: 'Participants' },
    { route: '/logistique', libelle: 'Logistique' },
    { route: '/avis', libelle: 'Avis' },
  ];
}
