package tn.fst.events.statistics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.clients.LogisticsDTO;
import tn.fst.events.statistics.clients.ParticipantDTO;
import tn.fst.events.statistics.sources.StatsSource;

/**
 * Repris de feature/event-statistics (3 tests AVEC Mockito) : mêmes métriques, mêmes valeurs.
 * Adaptés : JUnit 4 → 5 ; les trois repositories simulés deviennent la StatsSource simulée ; le
 * coût ne compte que la logistique réservée (filtre fait ici, au lieu de findByReserveTrue).
 * 1 test ajouté : jauge NaN quand le service lu est en panne.
 */
@ExtendWith(MockitoExtension.class)
class BusinessMetricsConfigTest {

    @Mock
    StatsSource source;

    SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        new BusinessMetricsConfig().businessMetrics(source).bindTo(registry);
    }

    private LogisticsDTO logistique(boolean reserve, float prixUnit, int quantite) {
        return new LogisticsDTO(1, "L", reserve, prixUnit, quantite);
    }

    @Test
    void testCompteursEvenementsEtParticipants() {
        EventDTO e = new EventDTO("e", "E", null, null, 0f, null, null);
        when(source.evenements()).thenReturn(Collections.nCopies(6, e));
        when(source.participants()).thenReturn(Collections.nCopies(12, new ParticipantDTO(1, "N", "P", "INVITE")));
        assertEquals(6.0, registry.get("fst.events.count").gauge().value(), 0.001);
        assertEquals(12.0, registry.get("fst.participants.count").gauge().value(), 0.001);
    }

    @Test
    void testCoutDeLaLogistiqueReservee() {
        when(source.logistique()).thenReturn(Arrays.asList(
                logistique(true, 2.5f, 100), logistique(true, 120f, 2), logistique(false, 1000f, 1)));
        assertEquals(490.0, registry.get("fst.logistics.reserved.cost").gauge().value(), 0.001);
    }

    @Test
    void testCoutNulSansLogistiqueReservee() {
        when(source.logistique()).thenReturn(List.of());
        assertEquals(0.0, registry.get("fst.logistics.reserved.cost").gauge().value(), 0.001);
    }

    // ---------------------------------------------------------------- ajouté

    @Test
    void jaugeNaNQuandLeServiceLuEstEnPanne() {
        when(source.evenements()).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        assertTrue(Double.isNaN(registry.get("fst.events.count").gauge().value()));
    }
}
