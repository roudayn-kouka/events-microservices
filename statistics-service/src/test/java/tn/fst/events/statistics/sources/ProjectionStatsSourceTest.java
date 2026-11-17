package tn.fst.events.statistics.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import tn.fst.events.api.event.EventEvents.EventCreated;
import tn.fst.events.api.event.EventEvents.LogistiqueAffectee;
import tn.fst.events.api.feedback.FeedbackEvents.FeedbackSubmitted;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsCreated;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantCreated;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.projections.StatistiquesProjection;

/** Ajouté : la source APRÈS CQRS renvoie les mêmes DTO que la source Feign, lus dans db_stats. */
@DataJpaTest(showSql = false)
@Import({StatistiquesProjection.class, ProjectionStatsSource.class})
class ProjectionStatsSourceTest {

    static final String EV = "11111111-1111-1111-1111-111111111111";

    @Autowired StatistiquesProjection projection;
    @Autowired ProjectionStatsSource source;

    @BeforeEach
    void donnees() {
        projection.on(new EventCreated(EV, "Gala", LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 11), Set.of(1)));
        projection.on(new LogistiqueAffectee(EV, 9));
        projection.on(new ParticipantCreated(1, "Tounsi", "Ahmed", "ORGANISATEUR"));
        projection.on(new LogisticsCreated(9, "Chaises", true, 2.5f, 100));
        projection.on(new FeedbackSubmitted(1, EV, 1, 4, Instant.now()));
    }

    @Test
    void sonNomIndiqueLaVersionApresCqrs() {
        assertEquals("projections-cqrs", source.nom());
    }

    @Test
    void lesDtoSontReconstruitsDepuisLesProjections() {
        EventDTO e = source.evenement(EV).orElseThrow();
        assertEquals("Gala", e.description());
        assertEquals(Set.of(1), e.participantIds());
        assertEquals(Set.of(9), e.logisticsIds());
        assertEquals(1, source.evenements().size());
        assertEquals("ORGANISATEUR", source.participants(Set.of(1)).get(0).tache());
        assertEquals(1, source.participants().size());
        assertEquals(250f, source.logistique(Set.of(9)).get(0).prixUnit() * source.logistique().get(0).quantite(), 0.001);
        assertEquals(4.0, source.moyennes().get(EV).averageRating(), 0.001);
    }

    @Test
    void lotsVidesEtEvenementInconnu() {
        assertTrue(source.participants(Set.of()).isEmpty());
        assertTrue(source.logistique(Set.of()).isEmpty());
        assertTrue(source.evenement("inconnu").isEmpty());
    }
}
