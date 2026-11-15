package tn.fst.events.event.clients;

import java.util.Collection;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Client OpenFeign vers logistics-service (nom Eureka). */
@FeignClient(name = "logistics-service")
public interface LogisticsClient {

    @PostMapping("/logistics")
    LogisticsDTO addLogistics(@RequestBody LogisticsDTO logistics);

    @GetMapping("/logistics")
    List<LogisticsDTO> getLogistics(@RequestParam("ids") Collection<Integer> ids);

    /** Somme prixUnit × quantité des logistiques RÉSERVÉES parmi ces identifiants. */
    @GetMapping("/logistics/cout-reserve")
    float coutReserve(@RequestParam("ids") Collection<Integer> ids);
}
