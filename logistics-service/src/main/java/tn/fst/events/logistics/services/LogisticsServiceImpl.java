package tn.fst.events.logistics.services;

import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.logistics.entities.Logistics;
import tn.fst.events.logistics.repositories.LogisticsRepository;

/** Repris de feature/logistics ; « introuvable » donne 404 (et non 500), plus le lot et le coût réservé. */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LogisticsServiceImpl implements ILogisticsService {

    LogisticsRepository logisticsRepository;

    @Override
    public Logistics addLogistics(Logistics logistics) {
        return logisticsRepository.save(logistics);
    }

    @Override
    public Logistics updateLogistics(Logistics logistics) {
        return logisticsRepository.save(logistics); // save() fait un update si l'id existe
    }

    @Override
    public void deleteLogistics(int idLog) {
        logisticsRepository.deleteById(idLog);
    }

    /**
     * Monolithe : IllegalArgumentException, donc HTTP 500. Ici : 404, pour que les appelants
     * Feign distinguent « logistique inexistante » d'une panne de logistics-service.
     */
    @Override
    public Logistics getLogisticsById(int idLog) {
        return logisticsRepository.findById(idLog)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Logistics introuvable : " + idLog));
    }

    @Override
    public List<Logistics> getAllLogistics() {
        return logisticsRepository.findAll();
    }

    @Override
    public List<Logistics> getReservedLogistics() {
        return logisticsRepository.findByReserveTrue();
    }

    @Override
    public float calculerCoutTotal(int idLog) {
        Logistics l = getLogisticsById(idLog);
        float total = l.getPrixUnit() * l.getQuantite();
        log.info("Coût total de {} = {}", l.getDescription(), total);
        return total;
    }

    @Override
    public List<Logistics> getLogisticsByIds(Collection<Integer> ids) {
        return ids == null || ids.isEmpty() ? List.of() : logisticsRepository.findByIdLogIn(ids);
    }

    @Override
    public float calculerCoutReserve(Collection<Integer> ids) {
        return (float) getLogisticsByIds(ids).stream()
                .filter(Logistics::isReserve)
                .mapToDouble(l -> (double) l.getPrixUnit() * l.getQuantite())
                .sum();
    }
}
