package tn.fst.events.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.fst.events.api.event.EventEvents.EventCostUpdated;
import tn.fst.events.api.event.EventEvents.EventCreated;
import tn.fst.events.api.event.EventEvents.LogistiqueAffectee;
import tn.fst.events.api.feedback.FeedbackEvents.FeedbackSubmitted;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsCreated;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantCreated;
import tn.fst.events.statistics.clients.Clients.EventClient;
import tn.fst.events.statistics.clients.Clients.FeedbackClient;
import tn.fst.events.statistics.clients.Clients.LogisticsClient;
import tn.fst.events.statistics.clients.Clients.ParticipantClient;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.projections.StatsEvenementRepository;
import tn.fst.events.statistics.services.IEventStatsService;

/**
 * Ajouté (EVT-47 à EVT-50) : mon module APRÈS CQRS, de bout en bout dans un vrai contexte Spring
 * (Axon local : event store JPA sur H2). Des événements publiés dans Axon → processeur tracking →
 * db_stats → @QueryHandler via QueryGateway → API HTTP. Aucun appel Feign. Puis replay.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:statsCqrs;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class StatsCqrsIntegrationTest {

    static final String EV = "22222222-2222-2222-2222-222222222222";

    @Autowired EventGateway eventGateway;
    @Autowired IEventStatsService statistiques;
    @Autowired StatsEvenementRepository evenements;
    @Autowired MockMvc mvc;

    // présents pour prouver qu'ils ne sont PAS appelés
    @MockitoBean EventClient eventClient;
    @MockitoBean ParticipantClient participantClient;
    @MockitoBean LogisticsClient logisticsClient;
    @MockitoBean FeedbackClient feedbackClient;

    private static void attendre(BooleanSupplier condition, String quoi) throws InterruptedException {
        long fin = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) {
                throw new AssertionError("délai dépassé : " + quoi);
            }
            Thread.sleep(100);
        }
    }

    @Test
    void lesEvenementsPublieDeviennentDesStatistiquesSansAppelReseauPuisLeReplayReconstruit() throws Exception {
        eventGateway.publish(new ParticipantCreated(1, "Tounsi", "Ahmed", "ORGANISATEUR"),
                new ParticipantCreated(2, "Ben Salah", "Amira", "INVITE"),
                new LogisticsCreated(9, "Chaises", true, 2.5f, 100),
                new LogisticsCreated(10, "Goodies", false, 4f, 50),
                new EventCreated(EV, "Forum CQRS", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), Set.of(1, 2)),
                new LogistiqueAffectee(EV, 9),
                new LogistiqueAffectee(EV, 10),
                new EventCostUpdated(EV, 250f),
                new FeedbackSubmitted(1, EV, 2, 4, Instant.parse("2026-11-16T10:00:00Z")));

        // cohérence à terme : le processeur tracking traite le flux en arrière-plan
        attendre(() -> statistiques.getGlobalStats().getNbFeedbacks() == 1, "projection des événements");

        EventStatsDTO stats = statistiques.getEventStats(EV);
        assertEquals(2, stats.getNbParticipants());
        assertEquals(Long.valueOf(1), stats.getParticipantsParTache().get(tn.fst.events.statistics.dto.Tache.INVITE));
        assertEquals(2, stats.getNbLogistics());
        assertEquals(250f, stats.getCoutLogistiqueReservee(), 0.001); // même calcul qu'avant CQRS
        assertEquals(4.0, stats.getAverageRating(), 0.001);
        GlobalStatsDTO global = statistiques.getGlobalStats();
        assertEquals(50f, global.getTauxReservation(), 0.001);

        mvc.perform(get("/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Stats-Source", "projections-cqrs"))
                .andExpect(jsonPath("$.nbParticipants").value(2));
        mvc.perform(get("/stats/events/inconnu")).andExpect(status().isNotFound()); // 404 transporté par le bus
        mvc.perform(get("/stats/between/2026-12-31/2026-01-01")).andExpect(status().isBadRequest());

        verifyNoInteractions(eventClient, participantClient, logisticsClient, feedbackClient);

        // ---- replay : tables vidées (@ResetHandler) puis reconstruites depuis l'event store
        evenements.deleteAll(); // « corruption » de la projection
        assertTrue(evenements.findAll().isEmpty());
        mvc.perform(post("/stats/admin/replay"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processeur").value("statistiques"));
        attendre(() -> evenements.count() == 1 && statistiques.getGlobalStats().getNbFeedbacks() == 1, "replay");

        EventStatsDTO apres = statistiques.getEventStats(EV);
        assertEquals(stats.getCoutLogistiqueReservee(), apres.getCoutLogistiqueReservee(), 0.001);
        assertEquals(stats.getNbParticipants(), apres.getNbParticipants());
        assertEquals(stats.getAverageRating(), apres.getAverageRating(), 0.001);
        mvc.perform(get("/stats/admin/projection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("projections-cqrs"))
                .andExpect(jsonPath("$.evenements").value(1))
                .andExpect(jsonPath("$.avis").value(1));
    }
}
