package tn.fst.events.event.dto;

import java.time.LocalDate;
import java.util.List;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.clients.ParticipantDTO;

/**
 * Vue détaillée : l'événement (db_event) complété par ses participants (participant-service)
 * et sa logistique (logistics-service), lus par deux appels Feign. Équivalent de la réponse du
 * monolithe, mais composé à partir de trois services.
 */
public record EventDetailsDTO(String idEvent, String description, LocalDate dateDebut, LocalDate dateFin,
                              float cout, List<ParticipantDTO> participants, List<LogisticsDTO> logistics) {
}
