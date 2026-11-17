import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { messageErreur } from '../core/api-error';
import { Avis, Evenement, MoyenneAvis, Participant } from '../core/models';
import { BarresComponent, Barre } from './graphiques';

/**
 * feedback-service : un avis n'est accepté que si l'événement est passé et que le participant y
 * était inscrit (vérifié par Feign auprès d'event-service ; refus 400 ou 409 sinon).
 */
@Component({
  selector: 'app-avis',
  imports: [FormsModule, DatePipe, BarresComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Avis</h1>
          <p class="sous-titre">feedback-service · règles vérifiées par Feign auprès d'event-service</p>
        </div>
      </div>
      @if (erreur()) { <div class="alerte alerte-erreur" role="alert">{{ erreur() }}</div> }
      @if (info()) { <div class="alerte alerte-ok">{{ info() }}</div> }

      <div class="grille">
        <div class="carte">
          <h2>Moyenne par événement</h2>
          <app-barres [barres]="barresMoyennes()" titre="Note moyenne par événement" unite=" / 5" />
        </div>
        <div class="carte">
          <h2>Donner un avis</h2>
          <form class="ligne" (ngSubmit)="donner()">
            <label>Événement
              <select name="ev" [(ngModel)]="idEvent" (ngModelChange)="choisir($event)">
                <option [ngValue]="null">— choisir —</option>
                @for (e of evenements(); track e.idEvent) { <option [ngValue]="e.idEvent">{{ e.description }}</option> }
              </select>
            </label>
            <label>Participant
              <select name="part" [(ngModel)]="idPart">
                <option [ngValue]="null">— choisir —</option>
                @for (p of inscrits(); track p.idPart) { <option [ngValue]="p.idPart">{{ p.prenom }} {{ p.nom }}</option> }
              </select>
            </label>
            <label>Note
              <select name="note" [(ngModel)]="note">
                @for (n of [1, 2, 3, 4, 5]; track n) { <option [ngValue]="n">{{ n }}</option> }
              </select>
            </label>
            <label>Commentaire <input name="commentaire" [(ngModel)]="commentaire" maxlength="500" /></label>
            <button class="btn" type="submit" [disabled]="idEvent === null || idPart === null">Envoyer</button>
          </form>
        </div>
      </div>

      @if (idEvent) {
        <div class="carte tableau" style="margin-top: 16px">
          <h2>Avis de l'événement</h2>
          <table>
            <thead><tr><th>Date</th><th>Participant</th><th>Note</th><th>Commentaire</th><th></th></tr></thead>
            <tbody>
              @for (a of avis(); track a.id) {
                <tr>
                  <td>{{ a.date | date: 'dd/MM/yyyy' }}</td>
                  <td>{{ nomDe(a.participantId) }}</td>
                  <td>{{ a.note }} / 5</td>
                  <td>{{ a.commentaire }}</td>
                  <td><button class="btn btn-danger" (click)="supprimer(a)">Supprimer</button></td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="vide">Aucun avis pour cet événement.</td></tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class AvisComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly evenements = signal<Evenement[]>([]);
  readonly participants = signal<Participant[]>([]);
  readonly moyennes = signal<MoyenneAvis[]>([]);
  readonly avis = signal<Avis[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly choisi = signal<string | null>(null);

  idEvent: string | null = null;
  idPart: number | null = null;
  note = 5;
  commentaire = '';

  readonly inscrits = computed(() => {
    const ev = this.evenements().find((e) => e.idEvent === this.choisi());
    return ev ? this.participants().filter((p) => ev.participantIds.includes(p.idPart)) : [];
  });

  readonly barresMoyennes = computed<Barre[]>(() =>
    this.moyennes()
      .map((m) => ({
        libelle: `${this.evenements().find((e) => e.idEvent === m.eventId)?.description ?? m.eventId.slice(0, 8)} (${m.totalFeedbacks})`,
        valeur: m.averageRating,
      }))
      .sort((a, b) => b.valeur - a.valeur),
  );

  ngOnInit(): void {
    this.api.evenements().subscribe({ next: (e) => this.evenements.set(e), error: (e) => this.erreur.set(messageErreur(e, 'Événements indisponibles')) });
    this.api.participants().subscribe({ next: (p) => this.participants.set(p), error: () => this.participants.set([]) });
    this.chargerMoyennes();
  }

  chargerMoyennes(): void {
    this.api.moyennes().subscribe({ next: (m) => this.moyennes.set(m), error: (e) => this.erreur.set(messageErreur(e, 'Moyennes indisponibles')) });
  }

  choisir(idEvent: string | null): void {
    this.choisi.set(idEvent);
    this.idPart = null;
    this.avis.set([]);
    if (idEvent) {
      this.api.avisDe(idEvent).subscribe({ next: (a) => this.avis.set(a), error: (e) => this.erreur.set(messageErreur(e, 'Avis indisponibles')) });
    }
  }

  donner(): void {
    if (this.idEvent === null || this.idPart === null) return;
    this.erreur.set(null);
    this.info.set(null);
    this.api.donnerAvis(this.idEvent, this.idPart, { note: this.note, commentaire: this.commentaire || undefined }).subscribe({
      next: () => {
        this.info.set('Avis enregistré.');
        this.commentaire = '';
        this.choisir(this.idEvent);
        this.chargerMoyennes();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Avis refusé')),
    });
  }

  supprimer(a: Avis): void {
    this.api.supprimerAvis(a.id).subscribe({
      next: () => {
        this.choisir(this.idEvent);
        this.chargerMoyennes();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Suppression refusée')),
    });
  }

  nomDe(id: number): string {
    const p = this.participants().find((x) => x.idPart === id);
    return p ? `${p.prenom} ${p.nom}` : `n° ${id}`;
  }
}
