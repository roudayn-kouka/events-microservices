package tn.fst.events.event.dto;

import java.time.LocalDate;
import java.util.List;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.clients.ParticipantDTO;

/**
 * Vue détaillée : l'événement (db_event) complété par ses participants (participant-service)
 * et sa logistique (logistics-service), lus par deux appels Feign. Équivalent de la réponse du
 * monolithe, mais composé à partir de trois services.
 * Phase 4 (fallback dégradé) : si un des deux services est en panne, la liste correspondante est
 * vide et « avertissements » dit ce qui manque et pourquoi (réponse 200 plutôt qu'un refus).
 */
public record EventDetailsDTO(String idEvent, String description, LocalDate dateDebut, LocalDate dateFin,
                              float cout, List<ParticipantDTO> participants, List<LogisticsDTO> logistics,
                              List<String> avertissements) {
}
