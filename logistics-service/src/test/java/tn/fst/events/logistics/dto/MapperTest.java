package tn.fst.events.logistics.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tn.fst.events.logistics.entities.Logistics;

/** Repris de feature/participant (MapperTest, partie Logistics) ; 1 test ajouté (aller-retour). */
class MapperTest {

    @Test
    void nullLogisticsListStaysNull() {
        assertNull(Mapper.toLogisticsDtos(null));
    }

    @Test
    void logisticsRoundTripKeepsFields() {
        Logistics l = new Logistics(4, "Sonorisation", true, 250f, 2);
        LogisticsDTO d = Mapper.toDto(l);
        assertEquals("Sonorisation", d.getDescription());
        assertTrue(d.isReserve());
        Logistics back = Mapper.toEntity(d);
        assertEquals(4, back.getIdLog());
        assertEquals(250f, back.getPrixUnit(), 0.001f);
        assertEquals(2, back.getQuantite());
    }
}
