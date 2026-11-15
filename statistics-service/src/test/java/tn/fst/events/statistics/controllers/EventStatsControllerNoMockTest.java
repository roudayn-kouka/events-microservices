package tn.fst.events.statistics.controllers;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.dto.Tache;
import tn.fst.events.statistics.services.IEventStatsService;

/**
 * Repris de feature/event (5 tests SANS Mockito : faux service écrit à la main, aucune
 * bibliothèque de mock, aucun contexte Spring). Adaptés : JUnit 4 → 5 ; chemins /stats/... ;
 * identifiant UUID ; le faux service implémente aussi source() (nouvelle méthode), vérifiée par
 * l'en-tête X-Stats-Source dans testGetGlobalStats.
 */
class EventStatsControllerNoMockTest {

    /** Faux service : renvoie des données fixes et compte les appels (remplace verify()). */
    static class FakeEventStatsService implements IEventStatsService {
        int getEventStatsCalls;
        int getUpcomingCalls;
        int getBetweenCalls;
        int getAllCalls;
        int getGlobalCalls;
        String lastRequestedId;
        LocalDate lastFrom;
        LocalDate lastTo;
        private final EventStatsDTO stats;
        private final GlobalStatsDTO global;

        FakeEventStatsService(EventStatsDTO stats, GlobalStatsDTO global) {
            this.stats = stats;
            this.global = global;
        }

        @Override
        public EventStatsDTO getEventStats(String idEvent) {
            getEventStatsCalls++;
            lastRequestedId = idEvent;
            return stats;
        }

        @Override
        public List<EventStatsDTO> getUpcomingEventsStats() {
            getUpcomingCalls++;
            return Collections.singletonList(stats);
        }

        @Override
        public List<EventStatsDTO> getEventsStatsBetween(LocalDate d1, LocalDate d2) {
            getBetweenCalls++;
            lastFrom = d1;
            lastTo = d2;
            return Collections.singletonList(stats);
        }

        @Override
        public List<EventStatsDTO> getAllEventsStats() {
            getAllCalls++;
            return Collections.singletonList(stats);
        }

        @Override
        public GlobalStatsDTO getGlobalStats() {
            getGlobalCalls++;
            return global;
        }

        @Override
        public String source() {
            return "fausse-source";
        }
    }

    FakeEventStatsService fakeService;
    MockMvc mockMvc;

    private EventStatsDTO buildStats() {
        Map<Tache, Long> parTache = new EnumMap<>(Tache.class);
        parTache.put(Tache.ORGANISATEUR, 1L);
        parTache.put(Tache.INVITE, 2L);
        return EventStatsDTO.builder()
                .idEvent("e-1")
                .description("Journée Portes Ouvertes")
                .dureeJours(3)
                .nbParticipants(3)
                .participantsParTache(parTache)
                .nbLogistics(3)
                .nbLogisticsReservees(2)
                .coutLogistiqueReservee(60f)
                .build();
    }

    private GlobalStatsDTO buildGlobal() {
        return GlobalStatsDTO.builder()
                .nbEvents(6)
                .nbEventsAVenir(4)
                .nbParticipants(12)
                .nbLogistics(20)
                .nbLogisticsReservees(15)
                .tauxReservation(75f)
                .coutTotalLogistiqueReservee(9876.5f)
                .build();
    }

    @BeforeEach
    void setUp() {
        fakeService = new FakeEventStatsService(buildStats(), buildGlobal());
        EventStatsController controller = new EventStatsController(fakeService);
        // Dates des chemins au format ISO (yyyy-MM-dd), comme dans l'application
        DefaultFormattingConversionService conversion = new DefaultFormattingConversionService();
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(conversion);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setConversionService(conversion).build();
    }

    @Test
    void testGetEventStats() throws Exception {
        mockMvc.perform(get("/stats/events/{id}", "e-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEvent").value("e-1"))
                .andExpect(jsonPath("$.nbParticipants").value(3))
                .andExpect(jsonPath("$.participantsParTache.INVITE").value(2))
                .andExpect(jsonPath("$.nbLogisticsReservees").value(2))
                .andExpect(jsonPath("$.coutLogistiqueReservee").value(60.0));
        assertEquals(1, fakeService.getEventStatsCalls);
        assertEquals("e-1", fakeService.lastRequestedId);
    }

    @Test
    void testGetUpcomingEventsStats() throws Exception {
        mockMvc.perform(get("/stats/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Journée Portes Ouvertes"));
        assertEquals(1, fakeService.getUpcomingCalls);
    }

    @Test
    void testGetEventsStatsBetween() throws Exception {
        mockMvc.perform(get("/stats/between/{d1}/{d2}", "2025-05-01", "2025-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEvent").value("e-1"));
        assertEquals(1, fakeService.getBetweenCalls);
        assertEquals(LocalDate.of(2025, 5, 1), fakeService.lastFrom);
        assertEquals(LocalDate.of(2025, 5, 31), fakeService.lastTo);
    }

    @Test
    void testGetAllEventsStats() throws Exception {
        mockMvc.perform(get("/stats/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nbLogistics").value(3));
        assertEquals(1, fakeService.getAllCalls);
    }

    @Test
    void testGetGlobalStats() throws Exception {
        mockMvc.perform(get("/stats/summary"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Stats-Source", "fausse-source"))
                .andExpect(jsonPath("$.nbEvents").value(6))
                .andExpect(jsonPath("$.nbEventsAVenir").value(4))
                .andExpect(jsonPath("$.tauxReservation").value(75.0));
        assertEquals(1, fakeService.getGlobalCalls);
    }
}
