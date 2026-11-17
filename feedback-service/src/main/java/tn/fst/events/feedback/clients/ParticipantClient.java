package tn.fst.events.feedback.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** participant-service, résolu par Eureka (lb://participant-service). */
@FeignClient(name = "participant-service")
public interface ParticipantClient {

    /** 404 si le participant n'existe pas. */
    @GetMapping("/participants/{id}")
    ParticipantDTO getParticipant(@PathVariable("id") int idPart);
}
