package tn.fst.events.event.commands;

import java.time.LocalDate;
import java.util.Set;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Commandes de l'agrégat EventAggregate (côté écriture CQRS). Internes à event-service : seul
 * l'agrégat les traite. @TargetAggregateIdentifier indique à Axon quelle instance charger.
 */
public final class EventCommands {

    private EventCommands() {
    }

    public record CreateEvent(@TargetAggregateIdentifier String idEvent, String description, LocalDate dateDebut,
                              LocalDate dateFin, Set<Integer> participantIds) {
    }

    public record UpdateEvent(@TargetAggregateIdentifier String idEvent, String description, LocalDate dateDebut,
                              LocalDate dateFin) {
    }

    public record DeleteEvent(@TargetAggregateIdentifier String idEvent) {
    }

    public record AffecterParticipant(@TargetAggregateIdentifier String idEvent, int idPart) {
    }

    public record AffecterLogistique(@TargetAggregateIdentifier String idEvent, int idLog) {
    }

    /** Envoyée par calculCout ; sans effet (aucun événement) si le coût n'a pas changé. */
    public record UpdateEventCost(@TargetAggregateIdentifier String idEvent, float cout) {
    }
}
