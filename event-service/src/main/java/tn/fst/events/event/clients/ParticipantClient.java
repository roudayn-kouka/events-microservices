package tn.fst.events.event.clients;

import java.util.Collection;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Client OpenFeign vers participant-service. « name » est le nom Eureka : l'adresse réelle est
 * résolue à chaque appel et répartie entre les instances par Spring Cloud LoadBalancer.
 */
@FeignClient(name = "participant-service")
public interface ParticipantClient {

    @GetMapping("/participants/{id}")
    ParticipantDTO getParticipant(@PathVariable("id") int idPart);

    @GetMapping("/participants")
    List<ParticipantDTO> getParticipants(@RequestParam("ids") Collection<Integer> ids);

    @GetMapping("/participants/search")
    List<ParticipantDTO> search(@RequestParam("nom") String nom, @RequestParam("prenom") String prenom,
                                @RequestParam("tache") String tache);
}
