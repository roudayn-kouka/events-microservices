// Contrats JSON des 5 services, tels que la Gateway les renvoie (vérifiés sur /docs/<service>/v3/api-docs).

export type Tache = 'INVITE' | 'ORGANISATEUR' | 'SERVEUR' | 'ANIMATEUR';

export const TACHES: Tache[] = ['INVITE', 'ORGANISATEUR', 'SERVEUR', 'ANIMATEUR'];

export const LIBELLES_TACHE: Record<Tache, string> = {
  INVITE: 'Invité',
  ORGANISATEUR: 'Organisateur',
  SERVEUR: 'Serveur',
  ANIMATEUR: 'Animateur',
};

/** event-service : agrégat Axon, identifié par un UUID. */
export interface Evenement {
  idEvent: string;
  description: string;
  dateDebut: string; // yyyy-MM-dd
  dateFin: string;
  cout: number;
  participantIds: number[];
  logisticsIds: number[];
}

export interface NouvelEvenement {
  description: string;
  dateDebut: string;
  dateFin: string;
}

/** Vue composée (Feign vers participant- et logistics-service) ; dégradée si l'un est en panne. */
export interface DetailsEvenement {
  idEvent: string;
  description: string;
  dateDebut: string;
  dateFin: string;
  cout: number;
  participants: Participant[];
  logistics: Logistique[];
  avertissements: string[];
}

export interface Participant {
  idPart: number;
  nom: string;
  prenom: string;
  tache: Tache;
}

export interface Logistique {
  idLog: number;
  description: string;
  reserve: boolean;
  prixUnit: number;
  quantite: number;
}

export interface Avis {
  id: number;
  note: number;
  commentaire: string | null;
  date: string;
  eventId: string;
  participantId: number;
}

export interface NouvelAvis {
  note: number;
  commentaire?: string;
}

export interface MoyenneAvis {
  eventId: string;
  averageRating: number;
  totalFeedbacks: number;
}

/** statistics-service (mon module) : modèle de lecture CQRS, projections db_stats. */
export interface StatsGlobales {
  nbEvents: number;
  nbEventsAVenir: number;
  nbParticipants: number;
  nbLogistics: number;
  nbLogisticsReservees: number;
  tauxReservation: number;
  coutTotalLogistiqueReservee: number;
  participantsParTache: Partial<Record<Tache, number>>;
  nbFeedbacks: number;
  noteMoyenne: number;
}

export interface StatsEvenement {
  idEvent: string;
  description: string;
  dateDebut: string;
  dateFin: string;
  dureeJours: number;
  nbParticipants: number;
  participantsParTache: Partial<Record<Tache, number>>;
  nbLogistics: number;
  nbLogisticsReservees: number;
  coutLogistiqueReservee: number;
  averageRating: number;
  nbFeedbacks: number;
}

/** Réponse accompagnée des en-têtes utiles à la démonstration (source CQRS, instance servie). */
export interface AvecOrigine<T> {
  corps: T;
  source: string | null;
  instance: string | null;
}
