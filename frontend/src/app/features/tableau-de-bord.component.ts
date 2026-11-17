import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { messageErreur } from '../core/api-error';
import { LIBELLES_TACHE, StatsEvenement, StatsGlobales, TACHES } from '../core/models';
import { AnneauComponent, BarresComponent, Barre } from './graphiques';

const COULEURS_TACHE = { INVITE: '#0ea5e9', ORGANISATEUR: '#8b5cf6', SERVEUR: '#f59e0b', ANIMATEUR: '#10b981' };

/**
 * Tableau de bord de statistics-service (mon module) : il ne lit que le modèle de lecture CQRS
 * (projections db_stats), donc il reste affiché même si les services d'écriture sont arrêtés.
 */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [DecimalPipe, DatePipe, RouterLink, BarresComponent, AnneauComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Statistiques</h1>
          <p class="sous-titre">statistics-service · côté requêtes CQRS (projections Axon)</p>
        </div>
        <div>
          @if (source()) { <span class="badge badge-ok">source : {{ source() }}</span> }
          @if (instance()) { <span class="badge">instance : {{ instance() }}</span> }
          <button class="btn btn-secondaire" (click)="charger()">Actualiser</button>
        </div>
      </div>

      @if (erreur()) { <div class="alerte alerte-erreur" role="alert">{{ erreur() }}</div> }
      @if (chargement()) { <p class="meta">Chargement…</p> }

      @if (resume(); as r) {
        <div class="kpis">
          <div class="kpi"><div class="valeur">{{ r.nbEvents }}</div><div class="libelle">événements</div></div>
          <div class="kpi"><div class="valeur">{{ r.nbEventsAVenir }}</div><div class="libelle">à venir</div></div>
          <div class="kpi"><div class="valeur">{{ r.nbParticipants }}</div><div class="libelle">participants</div></div>
          <div class="kpi"><div class="valeur">{{ r.tauxReservation | number: '1.0-1' }} %</div><div class="libelle">logistique réservée</div></div>
          <div class="kpi"><div class="valeur">{{ r.coutTotalLogistiqueReservee | number: '1.0-0' }}</div><div class="libelle">coût réservé (DT)</div></div>
          <div class="kpi"><div class="valeur">{{ r.noteMoyenne | number: '1.0-2' }} / 5</div><div class="libelle">note moyenne ({{ r.nbFeedbacks }} avis)</div></div>
        </div>

        <div class="grille">
          <div class="carte">
            <h2>Participants par tâche</h2>
            <app-anneau [barres]="parTache()" titre="Participants par tâche" [centre]="'' + r.nbParticipants" />
          </div>
          <div class="carte">
            <h2>Logistique</h2>
            <app-anneau [barres]="logistique()" titre="Logistique réservée ou non" [centre]="(r.tauxReservation | number: '1.0-0') + ' %'" />
          </div>
          <div class="carte">
            <h2>Événements les plus suivis</h2>
            <app-barres [barres]="topParticipants()" titre="Participants par événement" />
          </div>
          <div class="carte">
            <h2>Coût logistique réservé par événement</h2>
            <app-barres [barres]="topCouts()" titre="Coût réservé par événement" unite=" DT" format="1.0-0" />
          </div>
          <div class="carte">
            <h2>Note moyenne des événements notés</h2>
            <app-barres [barres]="notes()" titre="Note moyenne par événement" unite=" / 5" />
          </div>
          <div class="carte">
            <h2>Événements à venir</h2>
            @if (aVenir().length === 0) { <p class="vide">Aucun événement à venir.</p> }
            <div class="tableau">
              <table>
                <tbody>
                  @for (e of aVenir().slice(0, 8); track e.idEvent) {
                    <tr>
                      <td><a [routerLink]="['/evenements', e.idEvent]">{{ e.description }}</a></td>
                      <td>{{ e.dateDebut | date: 'dd/MM/yyyy' }}</td>
                      <td>{{ e.nbParticipants }} pers.</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        </div>
      }
    </section>
  `,
})
export class TableauDeBordComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly resume = signal<StatsGlobales | null>(null);
  readonly source = signal<string | null>(null);
  readonly instance = signal<string | null>(null);
  readonly evenements = signal<StatsEvenement[]>([]);
  readonly aVenir = signal<StatsEvenement[]>([]);

  readonly parTache = computed<Barre[]>(() => {
    const r = this.resume();
    return r ? TACHES.map((t) => ({ libelle: LIBELLES_TACHE[t], valeur: r.participantsParTache[t] ?? 0, couleur: COULEURS_TACHE[t] })) : [];
  });

  readonly logistique = computed<Barre[]>(() => {
    const r = this.resume();
    return r
      ? [
          { libelle: 'Réservée', valeur: r.nbLogisticsReservees, couleur: '#10b981' },
          { libelle: 'Non réservée', valeur: r.nbLogistics - r.nbLogisticsReservees, couleur: '#cbd5e1' },
        ]
      : [];
  });

  readonly topParticipants = computed(() => top(this.evenements(), (e) => e.nbParticipants));
  readonly topCouts = computed(() => top(this.evenements(), (e) => e.coutLogistiqueReservee));
  readonly notes = computed(() => top(this.evenements().filter((e) => e.nbFeedbacks > 0), (e) => e.averageRating));

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    forkJoin({ resume: this.api.resume(), evenements: this.api.statsEvenements(), aVenir: this.api.aVenir() }).subscribe({
      next: ({ resume, evenements, aVenir }) => {
        this.resume.set(resume.corps);
        this.source.set(resume.source);
        this.instance.set(resume.instance);
        this.evenements.set(evenements);
        this.aVenir.set(aVenir);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e, 'Statistiques indisponibles'));
        this.chargement.set(false);
      },
    });
  }
}

/** Les 6 plus grandes valeurs non nulles, en ordre décroissant. */
export function top(evenements: StatsEvenement[], valeur: (e: StatsEvenement) => number, n = 6): Barre[] {
  return evenements
    .map((e) => ({ libelle: e.description, valeur: valeur(e) }))
    .filter((b) => b.valeur > 0)
    .sort((a, b) => b.valeur - a.valeur)
    .slice(0, n);
}
