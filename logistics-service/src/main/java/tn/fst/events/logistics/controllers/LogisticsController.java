package tn.fst.events.logistics.controllers;

import jakarta.validation.Valid;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tn.fst.events.logistics.dto.LogisticsDTO;
import tn.fst.events.logistics.dto.Mapper;
import tn.fst.events.logistics.services.ILogisticsService;

/**
 * API logistique, chemins repris de feature/logistics. Via la Gateway : /api/logistics/**.
 * Expose des DTO validés (le contrôleur d'origine exposait l'entité JPA).
 */
@RestController
@RequestMapping("/logistics")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LogisticsController {

    ILogisticsService logisticsService;

    // POST /logistics
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LogisticsDTO add(@Valid @RequestBody LogisticsDTO logistics) {
        return Mapper.toDto(logisticsService.addLogistics(Mapper.toEntity(logistics)));
    }

    // PUT /logistics
    @PutMapping
    public LogisticsDTO update(@Valid @RequestBody LogisticsDTO logistics) {
        return Mapper.toDto(logisticsService.updateLogistics(Mapper.toEntity(logistics)));
    }

    // DELETE /logistics/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") int idLog) {
        logisticsService.deleteLogistics(idLog);
    }

    // GET /logistics/{id}
    @GetMapping("/{id}")
    public LogisticsDTO getById(@PathVariable("id") int idLog) {
        return Mapper.toDto(logisticsService.getLogisticsById(idLog));
    }

    // GET /logistics, ou lot d'identifiants : GET /logistics?ids=1,2 (appels Feign)
    @GetMapping
    public List<LogisticsDTO> getAll(@RequestParam(name = "ids", required = false) List<Integer> ids) {
        return Mapper.toLogisticsDtos(ids == null
                ? logisticsService.getAllLogistics()
                : logisticsService.getLogisticsByIds(ids));
    }

    // GET /logistics/reserved
    @GetMapping("/reserved")
    public List<LogisticsDTO> getReserved() {
        return Mapper.toLogisticsDtos(logisticsService.getReservedLogistics());
    }

    // GET /logistics/{id}/cout
    @GetMapping("/{id}/cout")
    public float getCoutTotal(@PathVariable("id") int idLog) {
        return logisticsService.calculerCoutTotal(idLog);
    }

    // GET /logistics/cout-reserve?ids=1,2 : coût réservé d'un ensemble (calculCout d'event-service)
    @GetMapping("/cout-reserve")
    public float getCoutReserve(@RequestParam("ids") List<Integer> ids) {
        return logisticsService.calculerCoutReserve(ids);
    }
}
