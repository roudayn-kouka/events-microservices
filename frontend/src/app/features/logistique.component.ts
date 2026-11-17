import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { messageErreur } from '../core/api-error';
import { Logistique } from '../core/models';

/** logistics-service : matériel et prestations, réservés ou non. */
@Component({
  selector: 'app-logistique',
  imports: [FormsModule, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Logistique</h1>
          <p class="sous-titre">logistics-service · total réservé : {{ totalReserve() | number: '1.0-2' }} DT</p>
        </div>
      </div>
      @if (erreur()) { <div class="alerte alerte-erreur" role="alert">{{ erreur() }}</div> }

      <div class="carte" style="margin-bottom: 16px">
        <h2>Nouvelle logistique</h2>
        <form class="ligne" (ngSubmit)="creer()">
          <label>Description <input name="description" [(ngModel)]="nouvelle.description" required /></label>
          <label>Prix unitaire <input type="number" min="0" step="0.01" name="prixUnit" [(ngModel)]="nouvelle.prixUnit" /></label>
          <label>Quantité <input type="number" min="0" name="quantite" [(ngModel)]="nouvelle.quantite" /></label>
          <label>Réservée <input type="checkbox" name="reserve" [(ngModel)]="nouvelle.reserve" /></label>
          <button class="btn" type="submit" [disabled]="!nouvelle.description">Ajouter</button>
        </form>
      </div>

      <div class="carte tableau">
        <table>
          <thead><tr><th>#</th><th>Description</th><th>Prix unitaire</th><th>Quantité</th><th>Total</th><th>État</th><th></th></tr></thead>
          <tbody>
            @for (l of logistiques(); track l.idLog) {
              <tr>
                <td>{{ l.idLog }}</td>
                <td>{{ l.description }}</td>
                <td>{{ l.prixUnit | number: '1.0-2' }}</td>
                <td>{{ l.quantite }}</td>
                <td>{{ l.prixUnit * l.quantite | number: '1.0-2' }}</td>
                <td>
                  <button class="badge" [class.badge-ok]="l.reserve" (click)="basculer(l)" title="Changer l'état">
                    {{ l.reserve ? 'réservée' : 'non réservée' }}
                  </button>
                </td>
                <td><button class="btn btn-danger" (click)="supprimer(l)">Supprimer</button></td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="vide">Aucune logistique.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class LogistiqueComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly logistiques = signal<Logistique[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly totalReserve = computed(() =>
    this.logistiques().filter((l) => l.reserve).reduce((s, l) => s + l.prixUnit * l.quantite, 0),
  );
  nouvelle: Omit<Logistique, 'idLog'> = { description: '', reserve: false, prixUnit: 0, quantite: 1 };

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.api.logistiques().subscribe({
      next: (l) => this.logistiques.set(l),
      error: (e) => this.erreur.set(messageErreur(e, 'Liste de la logistique indisponible')),
    });
  }

  creer(): void {
    this.erreur.set(null);
    this.api.creerLogistique(this.nouvelle).subscribe({
      next: () => {
        this.nouvelle = { description: '', reserve: false, prixUnit: 0, quantite: 1 };
        this.charger();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Ajout refusé')),
    });
  }

  basculer(l: Logistique): void {
    this.api.modifierLogistique({ ...l, reserve: !l.reserve }).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(messageErreur(e, 'Modification refusée')),
    });
  }

  supprimer(l: Logistique): void {
    if (!confirm(`Supprimer « ${l.description} » ?`)) return;
    this.api.supprimerLogistique(l.idLog).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(messageErreur(e, 'Suppression refusée')),
    });
  }
}
