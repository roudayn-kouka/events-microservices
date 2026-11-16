package tn.fst.events.statistics.projections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import tn.fst.events.api.event.EventEvents.EventCostUpdated;
import tn.fst.events.api.event.EventEvents.EventCreated;
import tn.fst.events.api.event.EventEvents.EventDeleted;
import tn.fst.events.api.event.EventEvents.EventUpdated;
import tn.fst.events.api.event.EventEvents.LogistiqueAffectee;
import tn.fst.events.api.event.EventEvents.ParticipantAffecte;
import tn.fst.events.api.feedback.FeedbackEvents.FeedbackDeleted;
import tn.fst.events.api.feedback.FeedbackEvents.FeedbackSubmitted;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsCreated;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsDeleted;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsUpdated;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantCreated;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantDeleted;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantUpdated;
import tn.fst.events.statistics.projections.ProjectionModel.StatsEvenement;

/** Ajouté (EVT-47) : chaque @EventHandler de la projection, sur une vraie base (H2). */
@DataJpaTest(showSql = false)
@Import(StatistiquesProjection.class)
class StatistiquesProjectionTest {

    static final String EV = "11111111-1111-1111-1111-111111111111";

    @Autowired StatistiquesProjection projection;
    @Autowired StatsEvenementRepository evenements;
    @Autowired StatsParticipantRepository participants;
    @Autowired StatsLogistiqueRepository logistique;
    @Autowired StatsAvisRepository avis;

    @Test
    void lesEvenementsDeLAgregatConstruisentLaLigneStatsEvenement() {
        projection.on(new EventCreated(EV, "Gala", LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 11), Set.of(1)));
        projection.on(new ParticipantAffecte(EV, 2));
        projection.on(new LogistiqueAffectee(EV, 9));
        projection.on(new EventCostUpdated(EV, 250f));
        projection.on(new EventUpdated(EV, "Gala 2026", LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 13)));

        StatsEvenement s = evenements.findById(EV).orElseThrow();
        assertEquals("Gala 2026", s.getDescription());
        assertEquals(LocalDate.of(2026, 12, 12), s.getDateDebut());
        assertEquals(Set.of(1, 2), s.getParticipantIds());
        assertEquals(Set.of(9), s.getLogisticsIds());
        assertEquals(250f, s.getCout(), 0.001);

        projection.on(new EventDeleted(EV));
        assertFalse(evenements.existsById(EV));
    }

    @Test
    void unEvenementInconnuEstIgnoreSansErreur() {
        projection.on(new ParticipantAffecte("inconnu", 2));
        projection.on(new EventCostUpdated("inconnu", 10f));
        assertEquals(0, evenements.count());
    }

    @Test
    void participantsEtLogistiqueSontDesUpserts() {
        projection.on(new ParticipantCreated(1, "Tounsi", "Ahmed", "ORGANISATEUR"));
        projection.on(new ParticipantUpdated(1, "Tounsi", "Ahmed", "INVITE"));
        projection.on(new ParticipantCreated(1, "Tounsi", "Ahmed", "INVITE")); // rejoué : même état
        projection.on(new LogisticsCreated(9, "Chaises", true, 2.5f, 100));
        projection.on(new LogisticsUpdated(9, "Chaises", false, 2.5f, 100));

        assertEquals(1, participants.count());
        assertEquals("INVITE", participants.findById(1).orElseThrow().getTache());
        assertFalse(logistique.findById(9).orElseThrow().isReserve());

        projection.on(new ParticipantDeleted(1));
        projection.on(new LogisticsDeleted(9));
        assertEquals(0, participants.count());
        assertEquals(0, logistique.count());
    }

    @Test
    void lesAvisAlimententLesMoyennes() {
        projection.on(new FeedbackSubmitted(1, EV, 1, 5, Instant.parse("2026-11-16T10:00:00Z")));
        projection.on(new FeedbackSubmitted(2, EV, 2, 3, Instant.parse("2026-11-16T11:00:00Z")));

        assertEquals(4.0, avis.moyennes().get(0).averageRating(), 0.001);
        assertEquals(2, avis.moyennes().get(0).totalFeedbacks());

        projection.on(new FeedbackDeleted(2, EV));
        assertEquals(5.0, avis.moyennes().get(0).averageRating(), 0.001);
    }

    @Test
    void leResetHandlerVideToutesLesTables() {
        projection.on(new EventCreated(EV, "Gala", null, null, Set.of(1)));
        projection.on(new ParticipantCreated(1, "Tounsi", "Ahmed", "ORGANISATEUR"));
        projection.on(new LogisticsCreated(9, "Chaises", true, 2.5f, 100));
        projection.on(new FeedbackSubmitted(1, EV, 1, 5, Instant.now()));

        projection.reinitialiser();

        assertTrue(evenements.findAll().isEmpty() && participants.findAll().isEmpty()
                && logistique.findAll().isEmpty() && avis.findAll().isEmpty());
    }
}
