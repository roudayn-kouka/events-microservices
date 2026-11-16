package tn.fst.events.statistics.controllers;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.services.IEventStatsService;

/**
 * API statistiques (mon module). Via la Gateway : /api/stats/** (StripPrefix=1 → /stats/**).
 * Monolithe : /events/event-stats/** ; mêmes opérations, l'identifiant devient un UUID.
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventStatsController {

    public static final String HEADER_SOURCE = "X-Stats-Source";

    IEventStatsService eventStatsService;

    /** Chaque réponse indique sa source de données : « feign-avant-cqrs » ici, les projections à la phase 3. */
    @ModelAttribute
    void indiquerSource(HttpServletResponse response) {
        String source = eventStatsService.source();
        if (source != null) {
            response.setHeader(HEADER_SOURCE, source);
        }
    }

    // GET /stats/events
    @GetMapping("/events")
    public List<EventStatsDTO> getAllEventsStats() {
        return eventStatsService.getAllEventsStats();
    }

    // GET /stats/summary
    @GetMapping("/summary")
    public GlobalStatsDTO getGlobalStats() {
        return eventStatsService.getGlobalStats();
    }

    // GET /stats/events/{id}
    @GetMapping("/events/{id}")
    public EventStatsDTO getEventStats(@PathVariable("id") String idEvent) {
        return eventStatsService.getEventStats(idEvent);
    }

    // GET /stats/upcoming
    @GetMapping("/upcoming")
    public List<EventStatsDTO> getUpcomingEventsStats() {
        return eventStatsService.getUpcomingEventsStats();
    }

    // GET /stats/between/2025-05-01/2025-05-31
    @GetMapping("/between/{d1}/{d2}")
    public List<EventStatsDTO> getEventsStatsBetween(
            @PathVariable("d1") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate d1,
            @PathVariable("d2") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate d2) {
        return eventStatsService.getEventsStatsBetween(d1, d2);
    }
}
