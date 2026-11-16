package tn.fst.events.api.event;

import java.time.LocalDate;
import java.util.Set;

/**
 * Événements de l'agrégat EventAggregate (event-service, côté commandes). Ils forment l'event store
 * d'Axon Server : l'état d'un événement est reconstruit en les rejouant (event sourcing).
 * Records imbriqués : un seul fichier pour un même agrégat ; nom complet sérialisé par Axon,
 * par exemple tn.fst.events.api.event.EventEvents$EventCreated.
 */
public final class EventEvents {

    private EventEvents() {
    }

    public record EventCreated(String idEvent, String description, LocalDate dateDebut, LocalDate dateFin,
                               Set<Integer> participantIds) {
    }

    public record EventUpdated(String idEvent, String description, LocalDate dateDebut, LocalDate dateFin) {
    }

    public record EventDeleted(String idEvent) {
    }

    public record ParticipantAffecte(String idEvent, int idPart) {
    }

    /** La logistique elle-même est créée dans logistics-service ; l'événement ne porte que son ID. */
    public record LogistiqueAffectee(String idEvent, int idLog) {
    }

    /** Résultat de calculCout : somme de la logistique réservée. */
    public record EventCostUpdated(String idEvent, float cout) {
    }
}
