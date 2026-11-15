package tn.fst.events.logistics.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.logistics.entities.Logistics;
import tn.fst.events.logistics.repositories.LogisticsRepository;

/**
 * Repris de feature/logistics (4 tests AVEC Mockito : repository simulé, aucune base).
 * Adaptation : JUnit 4 (MockitoJUnitRunner) → JUnit 5 (MockitoExtension). 4 tests ajoutés.
 */
@ExtendWith(MockitoExtension.class)
class LogisticsServiceImplMockTest {

    @Mock
    LogisticsRepository logisticsRepository;

    @InjectMocks
    LogisticsServiceImpl logisticsService;

    private Logistics buildLogistics() {
        Logistics l = new Logistics();
        l.setIdLog(1);
        l.setDescription("Chaises");
        l.setReserve(true);
        l.setPrixUnit(12.5f);
        l.setQuantite(40);
        return l;
    }

    @Test
    void testAddLogistics() {
        Logistics l = buildLogistics();
        when(logisticsRepository.save(l)).thenReturn(l);
        Logistics result = logisticsService.addLogistics(l);
        assertNotNull(result);
        assertEquals("Chaises", result.getDescription());
        verify(logisticsRepository, times(1)).save(l);
    }

    @Test
    void testRetrieveLogistics() {
        Logistics l = buildLogistics();
        when(logisticsRepository.findById(1)).thenReturn(Optional.of(l));
        Logistics result = logisticsService.getLogisticsById(1);
        assertNotNull(result);
        assertEquals(1, result.getIdLog());
        assertEquals(500f, logisticsService.calculerCoutTotal(1), 0.001f); // 12.5 x 40
        verify(logisticsRepository, atLeastOnce()).findById(1);
    }

    @Test
    void testUpdateLogistics() {
        Logistics l = buildLogistics();
        l.setDescription("Tables");
        when(logisticsRepository.save(l)).thenReturn(l);
        Logistics result = logisticsService.updateLogistics(l);
        assertEquals("Tables", result.getDescription());
        verify(logisticsRepository).save(l);
    }

    @Test
    void testDeleteLogistics() {
        logisticsService.deleteLogistics(1);
        verify(logisticsRepository, times(1)).deleteById(1);
    }

    // ---- Ajoutés

    @Test
    void retrieveUnknown_shouldThrow404() {
        when(logisticsRepository.findById(9)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> logisticsService.getLogisticsById(9));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void coutReserve_shouldSumOnlyReservedLogistics() {
        Logistics chaises = buildLogistics();                                  // réservé : 12.5 x 40 = 500
        Logistics tables = new Logistics(2, "Tables", false, 30f, 10);         // non réservé : ignoré
        Logistics sono = new Logistics(3, "Sonorisation", true, 250f, 2);      // réservé : 500
        when(logisticsRepository.findByIdLogIn(List.of(1, 2, 3))).thenReturn(List.of(chaises, tables, sono));
        assertEquals(1000f, logisticsService.calculerCoutReserve(List.of(1, 2, 3)), 0.001f);
    }

    @Test
    void coutReserve_emptyIds_shouldBeZeroWithoutQuery() {
        assertEquals(0f, logisticsService.calculerCoutReserve(List.of()), 0.001f);
        verifyNoInteractions(logisticsRepository);
    }

    @Test
    void getLogisticsByIds_null_shouldReturnEmpty() {
        assertTrue(logisticsService.getLogisticsByIds(null).isEmpty());
    }
}
