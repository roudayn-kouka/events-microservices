package tn.fst.events.feedback.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** event-service, résolu par Eureka (lb://event-service). */
@FeignClient(name = "event-service")
public interface EventClient {

    /** 404 si l'événement n'existe pas. */
    @GetMapping("/events/{id}")
    EventDTO getEvent(@PathVariable("id") String idEvent);
}
