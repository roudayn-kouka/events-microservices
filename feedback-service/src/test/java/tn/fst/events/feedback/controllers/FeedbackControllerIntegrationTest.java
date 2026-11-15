package tn.fst.events.feedback.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tn.fst.events.feedback.clients.EventClient;
import tn.fst.events.feedback.clients.EventDTO;
import tn.fst.events.feedback.clients.FeignErreurs;
import tn.fst.events.feedback.clients.ParticipantClient;
import tn.fst.events.feedback.clients.ParticipantDTO;
import tn.fst.events.feedback.repositories.FeedbackRepository;

/**
 * Repris de feature/feedback (10 méthodes, 21 exécutions ; contexte Spring complet, H2, MockMvc).
 * Tous adaptés : l'événement et le participant ne sont plus persistés dans la même base, ils
 * sont renvoyés par les clients Feign simulés (@MockitoBean) ; plus de context-path /events
 * (la Gateway préfixe /api) ; erreurs au format ApiError commun.
 * - malformedIdReturnsReadableBadRequest : l'identifiant d'événement est un UUID (texte) ; le
 *   test porte sur l'identifiant de participant non numérique ;
 * - eventStatisticsReflectFeedbackCreationAndDeletion : /event-stats appartient à
 *   statistics-service ; le test vérifie la source de ces statistiques, GET /feedback/moyennes.
 * 2 tests ajoutés : panne d'event-service (503), absence de vérification distante sur /moyennes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedbackControllerIntegrationTest {

    static final String EV = "11111111-1111-1111-1111-111111111111";
    static final String INCONNU = "99999999-9999-9999-9999-999999999999";
    static final int PART = 2;
    static final int AUTRE = 3;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired FeedbackRepository repository;
    @MockitoBean EventClient eventClient;
    @MockitoBean ParticipantClient participantClient;

    @BeforeEach
    void setup() {
        evenement(LocalDate.now().minusDays(1), Set.of(PART));
        when(participantClient.getParticipant(PART)).thenReturn(new ParticipantDTO(PART, "Doe", "Jane", "INVITE"));
        when(participantClient.getParticipant(AUTRE)).thenReturn(new ParticipantDTO(AUTRE, "Roe", "Max", "INVITE"));
        when(participantClient.getParticipant(-1)).thenThrow(FeignErreurs.introuvable("/participants/-1"));
        when(eventClient.getEvent(INCONNU)).thenThrow(FeignErreurs.introuvable("/events/" + INCONNU));
    }

    void evenement(LocalDate dateFin, Set<Integer> inscrits) {
        when(eventClient.getEvent(EV)).thenReturn(new EventDTO(EV, "Gala", LocalDate.now().minusDays(2), dateFin, inscrits));
    }

    ResultActions submit(String event, int participant, String body) throws Exception {
        return mvc.perform(post("/feedback/{event}/{participant}", event, participant)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void fullLifecycleReturnsSafeDtosAverageAndNoContentOnDelete() throws Exception {
        Instant before = Instant.now();
        String json = submit(EV, PART, "{\"note\":5,\"commentaire\":\"Excellent\",\"date\":\"2000-01-01T00:00:00Z\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.note").value(5))
                .andExpect(jsonPath("$.eventId").value(EV))
                .andExpect(jsonPath("$.participantId").value(PART))
                .andExpect(jsonPath("$.event").doesNotExist())
                .andExpect(jsonPath("$.participant").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = mapper.readTree(json);
        assertFalse(Instant.parse(response.get("date").asText()).isBefore(before));
        int id = response.get("id").asInt();
        mvc.perform(get("/feedback/event/{id}", EV))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].commentaire").value("Excellent"));
        mvc.perform(get("/feedback/event/{id}/moyenne", EV))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.totalFeedbacks").value(1));
        mvc.perform(delete("/feedback/{id}", id))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertFalse(repository.existsById(id));
        mvc.perform(delete("/feedback/{id}", id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Feedback not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"note\":null}", "{\"note\":0}", "{\"note\":6}",
            "{\"note\":1.5}", "{\"note\":5.0}", "{\"note\":\"5\"}", "{\"note\":true}",
            "{\"note\":2147483648}", "null", "{broken"})
    void invalidRequestIsBadRequestAndDoesNotPersist(String body) throws Exception {
        submit(EV, PART, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").isNotEmpty());
        assertEquals(0, repository.countByEventId(EV));
    }

    @Test
    void duplicateReturnsConflict() throws Exception {
        submit(EV, PART, "{\"note\":1}").andExpect(status().isCreated());
        submit(EV, PART, "{\"note\":5}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertEquals(1, repository.countByEventId(EV));
    }

    @Test
    void missingEventOrParticipantReturnsNotFound() throws Exception {
        submit(INCONNU, PART, "{\"note\":5}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found"));
        submit(EV, -1, "{\"note\":5}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Participant not found"));
        mvc.perform(get("/feedback/event/{id}", INCONNU)).andExpect(status().isNotFound());
        mvc.perform(get("/feedback/event/{id}/moyenne", INCONNU)).andExpect(status().isNotFound());
    }

    @Test
    void unassociatedParticipantReturnsBadRequest() throws Exception {
        submit(EV, AUTRE, "{\"note\":5}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Participant is not associated with this event"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void ongoingAndFutureEventsReturnBadRequest(int days) throws Exception {
        evenement(LocalDate.now().plusDays(days), Set.of(PART));
        submit(EV, PART, "{\"note\":5}").andExpect(status().isBadRequest());
    }

    @Test
    void eventWithNoEndDateReturnsBadRequest() throws Exception {
        evenement(null, Set.of(PART));
        submit(EV, PART, "{\"note\":5}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Event has not finished"));
    }

    @Test
    void emptyEventReturnsEmptyListAndZeroAverage() throws Exception {
        mvc.perform(get("/feedback/event/{id}", EV))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/feedback/event/{id}/moyenne", EV))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eventId").value(EV))
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.totalFeedbacks").value(0));
    }

    @Test
    void malformedIdReturnsReadableBadRequest() throws Exception {
        mvc.perform(post("/feedback/{event}/abc", EV).contentType(MediaType.APPLICATION_JSON).content("{\"note\":5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(delete("/feedback/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void eventStatisticsReflectFeedbackCreationAndDeletion() throws Exception {
        evenement(LocalDate.now().minusDays(1), Set.of(PART, AUTRE));
        mvc.perform(get("/feedback/moyennes")).andExpect(status().isOk()).andExpect(content().json("[]"));
        String created = submit(EV, PART, "{\"note\":4}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int feedbackId = mapper.readTree(created).get("id").asInt();
        submit(EV, AUTRE, "{\"note\":5}").andExpect(status().isCreated());
        mvc.perform(get("/feedback/moyennes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventId == '" + EV + "')].averageRating")
                        .value(org.hamcrest.Matchers.contains(4.5)))
                .andExpect(jsonPath("$[0].totalFeedbacks").value(2));
        mvc.perform(delete("/feedback/{id}", feedbackId)).andExpect(status().isNoContent());
        mvc.perform(get("/feedback/event/{id}/moyenne", EV))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(5.0));
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void eventServiceEnPanneRepond503AuFormatApiError() throws Exception {
        when(eventClient.getEvent(EV)).thenThrow(FeignErreurs.injoignable("/events/" + EV));
        submit(EV, PART, "{\"note\":5}").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("event-service indisponible (pas de réponse)"))
                .andExpect(jsonPath("$.path").value("/feedback/" + EV + "/" + PART));
        assertEquals(0, repository.countByEventId(EV));
    }

    @Test
    void moyennesRepondMemeSiEventServiceEstEnPanne() throws Exception {
        submit(EV, PART, "{\"note\":3}").andExpect(status().isCreated());
        when(eventClient.getEvent(EV)).thenThrow(FeignErreurs.injoignable("/events/" + EV));
        mvc.perform(get("/feedback/moyennes"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].averageRating").value(3.0))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Instance-Id", "feedback-service"));
    }
}
