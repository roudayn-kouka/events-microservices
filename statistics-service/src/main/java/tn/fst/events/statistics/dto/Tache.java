package tn.fst.events.statistics.dto;

/**
 * Tâches d'un participant, pour la répartition « participants par tâche » (toutes les tâches
 * apparaissent, même à 0). Copie de l'énumération de participant-service : statistics-service ne
 * partage pas de code métier avec les autres services, seulement le contrat JSON.
 */
public enum Tache {
    INVITE, ORGANISATEUR, SERVEUR, ANIMATEUR
}
