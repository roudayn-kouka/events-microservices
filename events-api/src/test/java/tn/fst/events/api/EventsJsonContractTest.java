package tn.fst.events.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tn.fst.events.api.event.EventEvents;
import tn.fst.events.api.feedback.FeedbackEvents;
import tn.fst.events.api.logistics.LogisticsEvents;
import tn.fst.events.api.participant.ParticipantEvents;

/**
 * Contrat : chaque événement fait l'aller-retour JSON sans perte, avec un ObjectMapper réglé comme
 * celui de Spring Boot (dates ISO), que le sérialiseur Jackson d'Axon utilise.
 */
class EventsJsonContractTest {

    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    static List<Object> evenements() {
        String id = "3f1c2a8e-6b4d-4e2a-9c1f-0a7b5d9e2c11";
        return List.of(
                new EventEvents.EventCreated(id, "Gala", LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 11), Set.of(1, 2)),
                new EventEvents.EventUpdated(id, "Gala 2026", LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 13)),
                new EventEvents.EventDeleted(id),
                new EventEvents.ParticipantAffecte(id, 3),
                new EventEvents.LogistiqueAffectee(id, 9),
                new EventEvents.EventCostUpdated(id, 250f),
                new ParticipantEvents.ParticipantCreated(1, "Tounsi", "Ahmed", "ORGANISATEUR"),
                new ParticipantEvents.ParticipantUpdated(1, "Tounsi", "Ahmed", "INVITE"),
                new ParticipantEvents.ParticipantDeleted(1),
                new LogisticsEvents.LogisticsCreated(9, "Chaises", true, 2.5f, 100),
                new LogisticsEvents.LogisticsUpdated(9, "Chaises", false, 2.5f, 100),
                new LogisticsEvents.LogisticsDeleted(9),
                new FeedbackEvents.FeedbackSubmitted(4, id, 1, 5, Instant.parse("2026-11-16T10:00:00Z")),
                new FeedbackEvents.FeedbackDeleted(4, id));
    }

    @ParameterizedTest
    @MethodSource("evenements")
    void allerRetourJsonSansPerte(Object evenement) throws Exception {
        String json = JSON.writeValueAsString(evenement);
        assertEquals(evenement, JSON.readValue(json, evenement.getClass()));
    }
}
