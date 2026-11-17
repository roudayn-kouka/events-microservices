package tn.fst.events.event.aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import tn.fst.events.api.event.EventEvents.EventCostUpdated;
import tn.fst.events.api.event.EventEvents.EventCreated;
import tn.fst.events.api.event.EventEvents.EventDeleted;
import tn.fst.events.api.event.EventEvents.EventUpdated;
import tn.fst.events.api.event.EventEvents.LogistiqueAffectee;
import tn.fst.events.api.event.EventEvents.ParticipantAffecte;
import tn.fst.events.event.commands.EventCommands.AffecterLogistique;
import tn.fst.events.event.commands.EventCommands.AffecterParticipant;
import tn.fst.events.event.commands.EventCommands.CreateEvent;
import tn.fst.events.event.commands.EventCommands.DeleteEvent;
import tn.fst.events.event.commands.EventCommands.UpdateEvent;
import tn.fst.events.event.commands.EventCommands.UpdateEventCost;
import tn.fst.events.event.commands.RegleMetierException;

/**
 * CÔTÉ COMMANDES du CQRS : l'événement est un agrégat Axon en event sourcing. Son état n'est
 * stocké nulle part en base : il est reconstruit en rejouant ses événements depuis Axon Server
 * (@EventSourcingHandler). Les @CommandHandler vérifient les règles puis publient (apply).
 *
 * Règles gardées ici (et non dans le contrôleur) :
 * - la date de fin ne précède pas la date de début ;
 * - aucune commande sur un événement supprimé ;
 * - idempotence : réinscrire un participant, rattacher deux fois une logistique ou recalculer un
 *   coût identique ne produit aucun événement (calculCout tourne chaque minute : sans cela,
 *   l'event store recevrait un événement par minute et par événement).
 * Les vérifications qui demandent le réseau (participant existant, création de la logistique)
 * sont faites AVANT l'envoi de la commande, dans EventServicesImpl.
 */
@Aggregate
public class EventAggregate {

    @AggregateIdentifier
    private String idEvent;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private final Set<Integer> participantIds = new HashSet<>();
    private final Set<Integer> logisticsIds = new HashSet<>();
    private float cout;
    private boolean supprime;

    /** Exigé par Axon pour reconstruire l'agrégat avant de rejouer ses événements. */
    protected EventAggregate() {
    }

    @CommandHandler
    public EventAggregate(CreateEvent commande) {
        validerDates(commande.dateDebut(), commande.dateFin());
        apply(new EventCreated(commande.idEvent(), commande.description(), commande.dateDebut(), commande.dateFin(),
                commande.participantIds() == null ? Set.of() : Set.copyOf(commande.participantIds())));
    }

    @CommandHandler
    public void handle(UpdateEvent commande) {
        actif();
        validerDates(commande.dateDebut(), commande.dateFin());
        apply(new EventUpdated(idEvent, commande.description(), commande.dateDebut(), commande.dateFin()));
    }

    @CommandHandler
    public void handle(DeleteEvent commande) {
        actif();
        apply(new EventDeleted(idEvent));
    }

    @CommandHandler
    public void handle(AffecterParticipant commande) {
        actif();
        if (!participantIds.contains(commande.idPart())) {
            apply(new ParticipantAffecte(idEvent, commande.idPart()));
        }
    }

    @CommandHandler
    public void handle(AffecterLogistique commande) {
        actif();
        if (!logisticsIds.contains(commande.idLog())) {
            apply(new LogistiqueAffectee(idEvent, commande.idLog()));
        }
    }

    @CommandHandler
    public void handle(UpdateEventCost commande) {
        actif();
        if (Float.compare(cout, commande.cout()) != 0) {
            apply(new EventCostUpdated(idEvent, commande.cout()));
        }
    }

    // ------------------------------------------------------------ event sourcing (état seulement)

    @EventSourcingHandler
    void on(EventCreated evenement) {
        idEvent = evenement.idEvent();
        dateDebut = evenement.dateDebut();
        dateFin = evenement.dateFin();
        participantIds.addAll(evenement.participantIds());
    }

    @EventSourcingHandler
    void on(EventUpdated evenement) {
        dateDebut = evenement.dateDebut();
        dateFin = evenement.dateFin();
    }

    @EventSourcingHandler
    void on(EventDeleted evenement) {
        // indicateur plutôt que markDeleted() : Axon refuserait alors de charger l'agrégat AVANT le
        // handler, et la commande échouerait sans le 404 explicite (et son code) de actif()
        supprime = true;
    }

    @EventSourcingHandler
    void on(ParticipantAffecte evenement) {
        participantIds.add(evenement.idPart());
    }

    @EventSourcingHandler
    void on(LogistiqueAffectee evenement) {
        logisticsIds.add(evenement.idLog());
    }

    @EventSourcingHandler
    void on(EventCostUpdated evenement) {
        cout = evenement.cout();
    }

    // ------------------------------------------------------------ règles

    private void actif() {
        if (supprime) {
            throw new RegleMetierException(404, "Event not found with id " + idEvent);
        }
    }

    private static void validerDates(LocalDate debut, LocalDate fin) {
        if (debut != null && fin != null && fin.isBefore(debut)) {
            throw new RegleMetierException(400, "La date de fin précède la date de début");
        }
    }
}
