package tn.fst.events.logistics.services;

import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsCreated;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsDeleted;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsUpdated;
import tn.fst.events.logistics.entities.Logistics;
import tn.fst.events.logistics.repositories.LogisticsRepository;

/**
 * Repris de feature/logistics ; « introuvable » donne 404 (et non 500), plus le lot et le coût réservé.
 * Phase 3 (CQRS) : chaque écriture publie son événement dans Axon, dans la transaction (avant le
 * commit). LogisticsUpdated porte l'état complet, réservation comprise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LogisticsServiceImpl implements ILogisticsService {

    LogisticsRepository logisticsRepository;
    EventGateway eventGateway;

    @Override
    @Transactional
    public Logistics addLogistics(Logistics logistics) {
        Logistics creee = logisticsRepository.save(logistics);
        eventGateway.publish(new LogisticsCreated(creee.getIdLog(), creee.getDescription(), creee.isReserve(),
                creee.getPrixUnit(), creee.getQuantite()));
        return creee;
    }

    @Override
    @Transactional
    public Logistics updateLogistics(Logistics logistics) {
        Logistics l = logisticsRepository.save(logistics); // save() fait un update si l'id existe
        eventGateway.publish(new LogisticsUpdated(l.getIdLog(), l.getDescription(), l.isReserve(), l.getPrixUnit(),
                l.getQuantite()));
        return l;
    }

    @Override
    @Transactional
    public void deleteLogistics(int idLog) {
        logisticsRepository.deleteById(idLog);
        eventGateway.publish(new LogisticsDeleted(idLog));
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
