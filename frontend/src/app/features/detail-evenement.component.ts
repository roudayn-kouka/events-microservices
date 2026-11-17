import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { messageErreur } from '../core/api-error';
import { DetailsEvenement, LIBELLES_TACHE, Participant } from '../core/models';

/**
 * Vue composée d'event-service (Feign vers participant- et logistics-service). Pendant une panne,
 * elle reste affichée en mode dégradé : la Gateway répond 200 et « avertissements » dit ce qui manque.
 */
@Component({
  selector: 'app-detail-evenement',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <p><a routerLink="/evenements">← Événements</a></p>
      @if (erreur()) { <div class="alerte alerte-erreur" role="alert">{{ erreur() }}</div> }
      @if (info()) { <div class="alerte alerte-ok">{{ info() }}</div> }

      @if (details(); as d) {
        <div class="page-header">
          <div>
            <h1>{{ d.description }}</h1>
            <p class="sous-titre">
              {{ d.dateDebut | date: 'dd/MM/yyyy' }} → {{ d.dateFin | date: 'dd/MM/yyyy' }} · coût {{ d.cout | number: '1.0-2' }} DT
            </p>
          </div>
          <div>
            @if (instance()) { <span class="badge">servi par {{ instance() }}</span> }
            <button class="btn btn-secondaire" (click)="charger()">Actualiser</button>
          </div>
        </div>

        @for (a of d.avertissements; track a) {
          <div class="alerte alerte-degradee" role="status">Vue dégradée : {{ a }}</div>
        }

        <div class="grille">
          <div class="carte">
            <h2>Participants ({{ d.participants.length }})</h2>
            <table>
              <tbody>
                @for (p of d.participants; track p.idPart) {
                  <tr><td>{{ p.prenom }} {{ p.nom }}</td><td><span class="badge">{{ libelle(p) }}</span></td></tr>
                } @empty {
                  <tr><td class="vide">Aucun participant affiché.</td></tr>
                }
              </tbody>
            </table>
            <form class="ligne" style="margin-top: 12px" (ngSubmit)="inscrire()">
              <label>Inscrire
                <select name="participant" [(ngModel)]="aInscrire">
                  <option [ngValue]="null">— choisir —</option>
                  @for (p of participants(); track p.idPart) {
                    <option [ngValue]="p.idPart">{{ p.prenom }} {{ p.nom }} ({{ libelle(p) }})</option>
                  }
                </select>
              </label>
              <button class="btn" type="submit" [disabled]="aInscrire === null">Inscrire</button>
            </form>
          </div>
          <div class="carte">
            <h2>Logistique ({{ d.logistics.length }})</h2>
            <table>
              <tbody>
                @for (l of d.logistics; track l.idLog) {
                  <tr>
                    <td>{{ l.description }}</td>
                    <td>{{ l.quantite }} × {{ l.prixUnit | number: '1.0-2' }}</td>
                    <td><span class="badge" [class.badge-ok]="l.reserve">{{ l.reserve ? 'réservée' : 'non réservée' }}</span></td>
                  </tr>
                } @empty {
                  <tr><td class="vide">Aucune logistique affichée.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </section>
  `,
})
export class DetailEvenementComponent implements OnInit {
  private readonly api = inject(ApiService);
  /** Paramètre de route :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  readonly details = signal<DetailsEvenement | null>(null);
  readonly instance = signal<string | null>(null);
  readonly participants = signal<Participant[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  aInscrire: number | null = null;

  ngOnInit(): void {
    this.charger();
    this.api.participants().subscribe({ next: (p) => this.participants.set(p), error: () => this.participants.set([]) });
  }

  charger(): void {
    this.erreur.set(null);
    this.api.details(this.id()).subscribe({
      next: (r) => {
        this.details.set(r.corps);
        this.instance.set(r.instance);
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Événement indisponible')),
    });
  }

  inscrire(): void {
    if (this.aInscrire === null) return;
    this.erreur.set(null);
    this.info.set(null);
    this.api.inscrire(this.id(), this.aInscrire).subscribe({
      next: () => {
        this.info.set('Participant inscrit.');
        this.aInscrire = null;
        this.charger();
      },
      // en panne : 503 « circuit ouvert », 504 « TimeLimiter », 502 « réponse incorrecte »
      error: (e) => this.erreur.set(messageErreur(e, 'Inscription refusée')),
    });
  }

  libelle(p: Participant): string {
    return LIBELLES_TACHE[p.tache] ?? p.tache;
  }
}
