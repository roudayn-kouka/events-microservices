import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { messageErreur } from '../core/api-error';
import { Evenement, NouvelEvenement } from '../core/models';

/** event-service : chaque création passe par une commande Axon (CreateEvent). */
@Component({
  selector: 'app-evenements',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Événements</h1>
          <p class="sous-titre">event-service · commandes Axon, coût calculé par Feign (logistics-service)</p>
        </div>
      </div>

      @if (erreur()) { <div class="alerte alerte-erreur" role="alert">{{ erreur() }}</div> }
      @if (info()) { <div class="alerte alerte-ok">{{ info() }}</div> }

      <div class="carte" style="margin-bottom: 16px">
        <h2>Nouvel événement</h2>
        <form class="ligne" (ngSubmit)="creer()">
          <label>Description <input name="description" [(ngModel)]="nouveau.description" required /></label>
          <label>Début <input type="date" name="dateDebut" [(ngModel)]="nouveau.dateDebut" required /></label>
          <label>Fin <input type="date" name="dateFin" [(ngModel)]="nouveau.dateFin" required /></label>
          <button class="btn" type="submit" [disabled]="!nouveau.description || !nouveau.dateDebut || !nouveau.dateFin">Créer</button>
        </form>
      </div>

      <div class="carte tableau">
        <table>
          <thead><tr><th>Description</th><th>Dates</th><th>Participants</th><th>Logistique</th><th>Coût</th><th></th></tr></thead>
          <tbody>
            @for (e of evenements(); track e.idEvent) {
              <tr>
                <td><a [routerLink]="['/evenements', e.idEvent]">{{ e.description }}</a></td>
                <td>{{ e.dateDebut | date: 'dd/MM/yyyy' }} → {{ e.dateFin | date: 'dd/MM/yyyy' }}</td>
                <td>{{ e.participantIds.length }}</td>
                <td>{{ e.logisticsIds.length }}</td>
                <td>{{ e.cout | number: '1.0-2' }}</td>
                <td><button class="btn btn-danger" (click)="supprimer(e)">Supprimer</button></td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="vide">Aucun événement (make seed crée un jeu de démonstration).</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class EvenementsComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly evenements = signal<Evenement[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  nouveau: NouvelEvenement = { description: '', dateDebut: '', dateFin: '' };

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.api.evenements().subscribe({
      next: (liste) => this.evenements.set([...liste].sort((a, b) => b.dateDebut.localeCompare(a.dateDebut))),
      error: (e) => this.erreur.set(messageErreur(e, 'Liste des événements indisponible')),
    });
  }

  creer(): void {
    this.erreur.set(null);
    this.api.creerEvenement(this.nouveau).subscribe({
      next: (e) => {
        this.info.set(`Événement « ${e.description} » créé (${e.idEvent}).`);
        this.nouveau = { description: '', dateDebut: '', dateFin: '' };
        this.charger();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Création refusée')),
    });
  }

  supprimer(e: Evenement): void {
    if (!confirm(`Supprimer « ${e.description} » ?`)) return;
    this.api.supprimerEvenement(e.idEvent).subscribe({
      next: () => {
        this.info.set(`Événement « ${e.description} » supprimé.`);
        this.charger();
      },
      error: (err) => this.erreur.set(messageErreur(err, 'Suppression refusée')),
    });
  }
}
