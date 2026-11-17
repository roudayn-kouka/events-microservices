package tn.fst.events.event.clients;

/** Vue d'un participant telle que renvoyée par participant-service (copie locale du contrat). */
public record ParticipantDTO(Integer idPart, String nom, String prenom, String tache) {
}
