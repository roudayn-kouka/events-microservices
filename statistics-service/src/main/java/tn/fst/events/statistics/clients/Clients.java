package tn.fst.events.statistics.clients;

import java.util.Collection;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * VERSION AVANT CQRS : les quatre clients OpenFeign lus par statistics-service, tous résolus par
 * Eureka (lb://). Regroupés ici car chacun ne déclare que des lectures.
 */
public final class Clients {

    private Clients() {
    }

    @FeignClient(name = "event-service")
    public interface EventClient {
        @GetMapping("/events")
        List<EventDTO> getEvents();

        @GetMapping("/events/{id}")
        EventDTO getEvent(@PathVariable("id") String idEvent);
    }

    @FeignClient(name = "participant-service")
    public interface ParticipantClient {
        @GetMapping("/participants")
        List<ParticipantDTO> getParticipants();

        @GetMapping("/participants")
        List<ParticipantDTO> getParticipants(@RequestParam("ids") Collection<Integer> ids);
    }

    @FeignClient(name = "logistics-service")
    public interface LogisticsClient {
        @GetMapping("/logistics")
        List<LogisticsDTO> getLogistics();

        @GetMapping("/logistics")
        List<LogisticsDTO> getLogistics(@RequestParam("ids") Collection<Integer> ids);
    }

    @FeignClient(name = "feedback-service")
    public interface FeedbackClient {
        @GetMapping("/feedback/moyennes")
        List<FeedbackAverageDTO> getMoyennes();
    }
}
