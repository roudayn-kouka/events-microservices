package tn.fst.events.statistics.controllers;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.common.web.CommonWebAutoConfiguration;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.dto.Tache;
import tn.fst.events.statistics.services.IEventStatsService;

/**
 * Repris de feature/event-statistics (5 tests AVEC Mockito : service simulé). Adaptés : JUnit 4
 * → 5 ; @WebMvcTest + @MockitoBean au lieu du standaloneSetup ; chemins /stats/... (au lieu de
 * /event-stats) ; identifiant UUID. 4 tests ajoutés : en-tête X-Stats-Source, 404 et 400 au
 * format ApiError, 503 quand un service lu est en panne (AVANT CQRS).
 */
@WebMvcTest(EventStatsController.class)
@ImportAutoConfiguration(CommonWebAutoConfiguration.class)
class EventStatsControllerMockTest {

    static final String ID = "3f1c2a8e-6b4d-4e2a-9c1f-0a7b5d9e2c11";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IEventStatsService eventStatsService;

    private EventStatsDTO buildStats() {
        Map<Tache, Long> parTache = new EnumMap<>(Tache.class);
        parTache.put(Tache.ORGANISATEUR, 1L);
        parTache.put(Tache.INVITE, 2L);
        return EventStatsDTO.builder()
                .idEvent(ID)
                .description("Journée Portes Ouvertes")
                .dureeJours(3)
                .nbParticipants(3)
                .participantsParTache(parTache)
                .nbLogistics(3)
                .nbLogisticsReservees(2)
                .coutLogistiqueReservee(60f)
                .averageRating(4.5)
                .build();
    }

    @Test
    void testGetEventStats() throws Exception {
        when(eventStatsService.getEventStats(ID)).thenReturn(buildStats());

        mockMvc.perform(get("/stats/events/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEvent").value(ID))
                .andExpect(jsonPath("$.nbParticipants").value(3))
                .andExpect(jsonPath("$.participantsParTache.INVITE").value(2))
                .andExpect(jsonPath("$.nbLogisticsReservees").value(2))
                .andExpect(jsonPath("$.coutLogistiqueReservee").value(60.0))
                .andExpect(jsonPath("$.averageRating").value(4.5));

        verify(eventStatsService, times(1)).getEventStats(ID);
    }

    @Test
    void testGetUpcomingEventsStats() throws Exception {
        when(eventStatsService.getUpcomingEventsStats()).thenReturn(Collections.singletonList(buildStats()));

        mockMvc.perform(get("/stats/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Journée Portes Ouvertes"));

        verify(eventStatsService).getUpcomingEventsStats();
    }

    @Test
    void testGetEventsStatsBetween() throws Exception {
        LocalDate d1 = LocalDate.of(2025, 5, 1);
        LocalDate d2 = LocalDate.of(2025, 5, 31);
        when(eventStatsService.getEventsStatsBetween(d1, d2)).thenReturn(Collections.singletonList(buildStats()));

        mockMvc.perform(get("/stats/between/{d1}/{d2}", "2025-05-01", "2025-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEvent").value(ID));

        verify(eventStatsService, times(1)).getEventsStatsBetween(d1, d2);
    }

    @Test
    void testGetAllEventsStats() throws Exception {
        when(eventStatsService.getAllEventsStats()).thenReturn(Collections.singletonList(buildStats()));

        mockMvc.perform(get("/stats/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nbLogistics").value(3));

        verify(eventStatsService).getAllEventsStats();
    }

    @Test
    void testGetGlobalStats() throws Exception {
        GlobalStatsDTO global = GlobalStatsDTO.builder()
                .nbEvents(6)
                .nbEventsAVenir(4)
                .nbParticipants(12)
                .nbLogistics(20)
                .nbLogisticsReservees(15)
                .tauxReservation(75f)
                .coutTotalLogistiqueReservee(9876.5f)
                .build();
        when(eventStatsService.getGlobalStats()).thenReturn(global);

        mockMvc.perform(get("/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbEvents").value(6))
                .andExpect(jsonPath("$.nbEventsAVenir").value(4))
                .andExpect(jsonPath("$.tauxReservation").value(75.0));

        verify(eventStatsService, times(1)).getGlobalStats();
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void chaqueReponseIndiqueSaSourceDeDonnees() throws Exception {
        when(eventStatsService.source()).thenReturn("feign-avant-cqrs");
        when(eventStatsService.getGlobalStats()).thenReturn(new GlobalStatsDTO());

        mockMvc.perform(get("/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Stats-Source", "feign-avant-cqrs"));
    }

    @Test
    void evenementInconnuRepond404AuFormatApiError() throws Exception {
        when(eventStatsService.getEventStats("inconnu"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found with id inconnu"));

        mockMvc.perform(get("/stats/events/inconnu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found with id inconnu"))
                .andExpect(jsonPath("$.path").value("/stats/events/inconnu"));
    }

    @Test
    void dateInvalideRepond400() throws Exception {
        mockMvc.perform(get("/stats/between/{d1}/{d2}", "2025-13-01", "2025-05-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void serviceLuEnPanneRepond503() throws Exception {
        when(eventStatsService.getGlobalStats()).thenThrow(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "feedback-service indisponible (pas de réponse)"));

        mockMvc.perform(get("/stats/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("feedback-service indisponible (pas de réponse)"));
    }
}
