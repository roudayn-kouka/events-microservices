import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { messageErreur } from '../core/api-error';
import { LIBELLES_TACHE, Participant, TACHES, Tache } from '../core/models';

/** participant-service (2 instances derrière le LoadBalancer : l'en-tête X-Instance-Id le montre). */
@Component({
  selector: 'app-participants',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Participants</h1>
          <p class="sous-titre">participant-service · 2 instances, réparties par Spring Cloud LoadBalancer</p>
        </div>
      </div>
      @if (erreur()) { <div class="alerte alerte-erreur" role="alert">{{ erreur() }}</div> }
      @if (info()) { <div class="alerte alerte-ok">{{ info() }}</div> }

      <div class="carte" style="margin-bottom: 16px">
        <h2>Nouveau participant</h2>
        <form class="ligne" (ngSubmit)="creer()">
          <label>Nom <input name="nom" [(ngModel)]="nouveau.nom" required /></label>
          <label>Prénom <input name="prenom" [(ngModel)]="nouveau.prenom" required /></label>
          <label>Tâche
            <select name="tache" [(ngModel)]="nouveau.tache">
              @for (t of taches; track t) { <option [ngValue]="t">{{ libelles[t] }}</option> }
            </select>
          </label>
          <button class="btn" type="submit" [disabled]="!nouveau.nom || !nouveau.prenom">Ajouter</button>
        </form>
      </div>

      <div class="carte tableau">
        <table>
          <thead><tr><th>#</th><th>Nom</th><th>Prénom</th><th>Tâche</th><th></th></tr></thead>
          <tbody>
            @for (p of participants(); track p.idPart) {
              <tr>
                <td>{{ p.idPart }}</td>
                <td>{{ p.nom }}</td>
                <td>{{ p.prenom }}</td>
                <td>
                  <select [ngModel]="p.tache" (ngModelChange)="changerTache(p, $event)" [attr.aria-label]="'Tâche de ' + p.prenom">
                    @for (t of taches; track t) { <option [ngValue]="t">{{ libelles[t] }}</option> }
                  </select>
                </td>
                <td><button class="btn btn-danger" (click)="supprimer(p)">Supprimer</button></td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="vide">Aucun participant.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class ParticipantsComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly taches = TACHES;
  readonly libelles = LIBELLES_TACHE;
  readonly participants = signal<Participant[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  nouveau: Omit<Participant, 'idPart'> = { nom: '', prenom: '', tache: 'INVITE' };

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.api.participants().subscribe({
      next: (p) => this.participants.set(p),
      error: (e) => this.erreur.set(messageErreur(e, 'Liste des participants indisponible')),
    });
  }

  creer(): void {
    this.erreur.set(null);
    this.api.creerParticipant(this.nouveau).subscribe({
      next: (p) => {
        this.info.set(`${p.prenom} ${p.nom} ajouté (n° ${p.idPart}).`);
        this.nouveau = { nom: '', prenom: '', tache: 'INVITE' };
        this.charger();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Ajout refusé')),
    });
  }

  changerTache(p: Participant, tache: Tache): void {
    this.api.changerTache(p.idPart, tache).subscribe({
      next: () => {
        this.info.set(`${p.prenom} ${p.nom} : ${LIBELLES_TACHE[tache]}.`);
        this.charger();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Changement refusé')),
    });
  }

  supprimer(p: Participant): void {
    if (!confirm(`Supprimer ${p.prenom} ${p.nom} ?`)) return;
    this.api.supprimerParticipant(p.idPart).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(messageErreur(e, 'Suppression refusée')),
    });
  }
}
