package tn.fst.events.event.aggregate;

import java.time.LocalDate;
import java.util.Set;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Ajouté (EVT-45) : l'agrégat testé en Given / When / Then, sans Spring ni base. « Given » est
 * l'historique d'événements (l'état est reconstruit par event sourcing), « When » la commande,
 * « Then » les événements publiés ou l'exception.
 */
class EventAggregateTest {

    static final String ID = "3f1c2a8e-6b4d-4e2a-9c1f-0a7b5d9e2c11";
    static final LocalDate DEBUT = LocalDate.of(2026, 12, 10);
    static final LocalDate FIN = LocalDate.of(2026, 12, 11);
    static final EventCreated CREE = new EventCreated(ID, "Gala", DEBUT, FIN, Set.of(1));

    FixtureConfiguration<EventAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(EventAggregate.class);
    }

    // ------------------------------------------------------------------ création

    @Test
    void creerPublieEventCreated() {
        fixture.givenNoPriorActivity()
                .when(new CreateEvent(ID, "Gala", DEBUT, FIN, Set.of(1)))
                .expectSuccessfulHandlerExecution()
                .expectEvents(CREE);
    }

    @Test
    void creerSansParticipantPublieUnEnsembleVide() {
        fixture.givenNoPriorActivity()
                .when(new CreateEvent(ID, "Gala", DEBUT, FIN, null))
                .expectEvents(new EventCreated(ID, "Gala", DEBUT, FIN, Set.of()));
    }

    @Test
    void creerAvecUneDateDeFinAvantLeDebutEstRefuse() {
        fixture.givenNoPriorActivity()
                .when(new CreateEvent(ID, "Gala", FIN, DEBUT, Set.of()))
                .expectNoEvents()
                .expectException(RegleMetierException.class)
                .expectExceptionMessage("La date de fin précède la date de début");
    }

    // ------------------------------------------------------------------ modification, suppression

    @Test
    void modifierPublieEventUpdated() {
        fixture.given(CREE)
                .when(new UpdateEvent(ID, "Gala 2026", DEBUT.plusDays(1), FIN.plusDays(1)))
                .expectEvents(new EventUpdated(ID, "Gala 2026", DEBUT.plusDays(1), FIN.plusDays(1)));
    }

    @Test
    void modifierAvecDesDatesIncoherentesEstRefuse() {
        fixture.given(CREE)
                .when(new UpdateEvent(ID, "Gala", FIN, DEBUT))
                .expectNoEvents()
                .expectException(RegleMetierException.class);
    }

    @Test
    void supprimerPublieEventDeleted() {
        fixture.given(CREE)
                .when(new DeleteEvent(ID))
                .expectEvents(new EventDeleted(ID));
    }

    @Test
    void supprimerDeuxFoisEstRefuse() {
        fixture.given(CREE, new EventDeleted(ID))
                .when(new DeleteEvent(ID))
                .expectNoEvents()
                .expectException(RegleMetierException.class);
    }

    @Test
    void aucuneCommandeSurUnEvenementSupprime() {
        fixture.given(CREE, new EventDeleted(ID))
                .when(new AffecterParticipant(ID, 2))
                .expectNoEvents()
                .expectException(RegleMetierException.class)
                .expectExceptionMessage("Event not found with id " + ID);
    }

    // ------------------------------------------------------------------ affectations

    @Test
    void affecterUnNouveauParticipantPublieParticipantAffecte() {
        fixture.given(CREE)
                .when(new AffecterParticipant(ID, 2))
                .expectEvents(new ParticipantAffecte(ID, 2));
    }

    @Test
    void reaffecterUnParticipantDejaInscritNePublieRien() {
        fixture.given(CREE, new ParticipantAffecte(ID, 2))
                .when(new AffecterParticipant(ID, 2))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void participantInscritALaCreationNEstPasReinscrit() {
        fixture.given(CREE)
                .when(new AffecterParticipant(ID, 1))
                .expectNoEvents();
    }

    @Test
    void affecterUneLogistiquePublieLogistiqueAffectee() {
        fixture.given(CREE)
                .when(new AffecterLogistique(ID, 9))
                .expectEvents(new LogistiqueAffectee(ID, 9));
    }

    @Test
    void rattacherDeuxFoisLaMemeLogistiqueNePublieRien() {
        fixture.given(CREE, new LogistiqueAffectee(ID, 9))
                .when(new AffecterLogistique(ID, 9))
                .expectNoEvents();
    }

    // ------------------------------------------------------------------ coût (calculCout)

    @Test
    void nouveauCoutPublieEventCostUpdated() {
        fixture.given(CREE)
                .when(new UpdateEventCost(ID, 250f))
                .expectEvents(new EventCostUpdated(ID, 250f));
    }

    @Test
    void coutInchangeNePublieRienCeQuiEvitUnEvenementParMinute() {
        fixture.given(CREE, new EventCostUpdated(ID, 250f))
                .when(new UpdateEventCost(ID, 250f))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void coutRemisAZeroPublieEventCostUpdated() {
        fixture.given(CREE, new EventCostUpdated(ID, 250f))
                .when(new UpdateEventCost(ID, 0f))
                .expectEvents(new EventCostUpdated(ID, 0f));
    }
}
