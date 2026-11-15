package tn.fst.events.logistics.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.logistics.entities.Logistics;

/**
 * Repris de feature/logistics (4 tests SANS Mockito : vrai service, vraie base ; MySQL dans le
 * monolithe, H2 ici). Adaptation de testDeleteLogistics : « introuvable » lève désormais une
 * ResponseStatusException 404 au lieu d'une IllegalArgumentException (HTTP 500). 1 test ajouté.
 */
@SpringBootTest
@Slf4j
class LogisticsServiceImplTest {

    @Autowired
    ILogisticsService logisticsService;

    private Logistics buildLogistics() {
        Logistics l = new Logistics();
        l.setDescription("Chaises");
        l.setReserve(true);
        l.setPrixUnit(12.5f);
        l.setQuantite(40);
        return l;
    }

    @Test
    void testAddLogistics() {
        Logistics saved = logisticsService.addLogistics(buildLogistics());
        log.info("Logistics ajoutée : {}", saved);
        assertNotEquals(0, saved.getIdLog());
        assertEquals("Chaises", saved.getDescription());
        assertTrue(saved.isReserve());
        logisticsService.deleteLogistics(saved.getIdLog()); // nettoyage
    }

    @Test
    void testRetrieveLogistics() {
        Logistics saved = logisticsService.addLogistics(buildLogistics());
        Logistics found = logisticsService.getLogisticsById(saved.getIdLog());
        assertNotNull(found);
        assertEquals(saved.getIdLog(), found.getIdLog());
        assertEquals(40, found.getQuantite());
        logisticsService.deleteLogistics(saved.getIdLog());
    }

    @Test
    void testUpdateLogistics() {
        Logistics saved = logisticsService.addLogistics(buildLogistics());
        saved.setDescription("Tables");
        saved.setQuantite(10);
        Logistics updated = logisticsService.updateLogistics(saved);
        assertEquals(saved.getIdLog(), updated.getIdLog());
        assertEquals("Tables", updated.getDescription());
        assertEquals(10, updated.getQuantite());
        logisticsService.deleteLogistics(saved.getIdLog());
    }

    @Test
    void testDeleteLogistics() {
        Logistics saved = logisticsService.addLogistics(buildLogistics());
        int id = saved.getIdLog();
        logisticsService.deleteLogistics(id);
        // Le service lance une exception 404 quand l'id n'existe plus
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> logisticsService.getLogisticsById(id));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ---- Ajouté

    @Test
    void coutReserve_shouldReadTheDatabase() {
        Logistics reserve = logisticsService.addLogistics(buildLogistics());                       // 500
        Logistics libre = logisticsService.addLogistics(new Logistics(0, "Tables", false, 30f, 10)); // ignoré
        assertEquals(500f, logisticsService.calculerCoutReserve(List.of(reserve.getIdLog(), libre.getIdLog())), 0.001f);
        logisticsService.deleteLogistics(reserve.getIdLog());
        logisticsService.deleteLogistics(libre.getIdLog());
    }
}
