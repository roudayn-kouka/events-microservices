package tn.fst.events.event.dto;

import java.time.LocalDate;
import java.util.Set;

/**
 * Événement exposé par l'API : les participants et la logistique sont des IDENTIFIANTS.
 * (Monolithe : objets imbriqués, avec @JsonIgnoreProperties pour casser la boucle JSON.)
 */
public record EventDTO(String idEvent, String description, LocalDate dateDebut, LocalDate dateFin, float cout,
                       Set<Integer> participantIds, Set<Integer> logisticsIds) {
}
