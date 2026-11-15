package tn.fst.events.statistics.clients;

import java.time.LocalDate;
import java.util.Set;

/** Événement tel que le renvoie event-service (participants et logistique par identifiant). */
public record EventDTO(String idEvent, String description, LocalDate dateDebut, LocalDate dateFin, float cout,
                       Set<Integer> participantIds, Set<Integer> logisticsIds) {
}
