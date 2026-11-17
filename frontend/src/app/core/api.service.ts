import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import {
  Avis,
  AvecOrigine,
  DetailsEvenement,
  Evenement,
  Logistique,
  MoyenneAvis,
  NouvelAvis,
  NouvelEvenement,
  Participant,
  StatsEvenement,
  StatsGlobales,
  Tache,
} from './models';

/** Accès aux 5 services, toujours à travers la Gateway (aucune URL de service en dur). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL).replace(/\/$/, '');

  // ── event-service ──────────────────────────────────────────────────────────
  evenements(): Observable<Evenement[]> {
    return this.http.get<Evenement[]>(`${this.base}/events`);
  }

  details(id: string): Observable<AvecOrigine<DetailsEvenement>> {
    return this.http
      .get<DetailsEvenement>(`${this.base}/events/${id}/details`, { observe: 'response' })
      .pipe(map((r) => avecOrigine(r)));
  }

  creerEvenement(e: NouvelEvenement): Observable<Evenement> {
    return this.http.post<Evenement>(`${this.base}/events`, e);
  }

  supprimerEvenement(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${id}`);
  }

  inscrire(idEvent: string, idPart: number): Observable<Evenement> {
    return this.http.post<Evenement>(`${this.base}/events/${idEvent}/participants/${idPart}`, null);
  }

  // ── participant-service ────────────────────────────────────────────────────
  participants(): Observable<Participant[]> {
    return this.http.get<Participant[]>(`${this.base}/participants`);
  }

  creerParticipant(p: Omit<Participant, 'idPart'>): Observable<Participant> {
    return this.http.post<Participant>(`${this.base}/participants`, p);
  }

  changerTache(id: number, tache: Tache): Observable<Participant> {
    return this.http.put<Participant>(`${this.base}/participants/${id}/tache/${tache}`, null);
  }

  supprimerParticipant(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/participants/${id}`);
  }

  // ── logistics-service ──────────────────────────────────────────────────────
  logistiques(): Observable<Logistique[]> {
    return this.http.get<Logistique[]>(`${this.base}/logistics`);
  }

  creerLogistique(l: Omit<Logistique, 'idLog'>): Observable<Logistique> {
    return this.http.post<Logistique>(`${this.base}/logistics`, l);
  }

  modifierLogistique(l: Logistique): Observable<Logistique> {
    return this.http.put<Logistique>(`${this.base}/logistics`, l);
  }

  supprimerLogistique(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/logistics/${id}`);
  }

  // ── feedback-service ───────────────────────────────────────────────────────
  avisDe(idEvent: string): Observable<Avis[]> {
    return this.http.get<Avis[]>(`${this.base}/feedback/event/${idEvent}`);
  }

  moyennes(): Observable<MoyenneAvis[]> {
    return this.http.get<MoyenneAvis[]>(`${this.base}/feedback/moyennes`);
  }

  donnerAvis(idEvent: string, idPart: number, avis: NouvelAvis): Observable<Avis> {
    return this.http.post<Avis>(`${this.base}/feedback/${idEvent}/${idPart}`, avis);
  }

  supprimerAvis(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/feedback/${id}`);
  }

  // ── statistics-service (mon module, CQRS) ─────────────────────────────────
  resume(): Observable<AvecOrigine<StatsGlobales>> {
    return this.http
      .get<StatsGlobales>(`${this.base}/stats/summary`, { observe: 'response' })
      .pipe(map((r) => avecOrigine(r)));
  }

  statsEvenements(): Observable<StatsEvenement[]> {
    return this.http.get<StatsEvenement[]>(`${this.base}/stats/events`);
  }

  aVenir(): Observable<StatsEvenement[]> {
    return this.http.get<StatsEvenement[]>(`${this.base}/stats/upcoming`);
  }
}

function avecOrigine<T>(r: HttpResponse<T>): AvecOrigine<T> {
  return {
    corps: r.body as T,
    source: r.headers.get('X-Stats-Source'),
    instance: r.headers.get('X-Instance-Id'),
  };
}
