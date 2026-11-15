package tn.fst.events.feedback.clients;

import java.time.LocalDate;
import java.util.Set;

/**
 * Ce que feedback-service lit d'un événement : sa date de fin (événement terminé ?) et ses
 * inscrits (participant associé ?). Les autres champs de la réponse sont ignorés.
 */
public record EventDTO(String idEvent, String description, LocalDate dateDebut, LocalDate dateFin,
                       Set<Integer> participantIds) {
}
