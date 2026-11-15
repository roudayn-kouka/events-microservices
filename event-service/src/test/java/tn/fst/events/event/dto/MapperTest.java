package tn.fst.events.event.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tn.fst.events.event.entities.Event;

/**
 * Partie « événement » du MapperTest de feature/participant : 1 test repris et adapté
 * (eventWithoutParticipantsGetsEmptySet : EventDTO vide → EventRequest vide). Les deux autres
 * tests (participant, logistique) sont portés dans participant-service et logistics-service.
 * 3 tests ajoutés.
 */
class MapperTest {

    @Test
    void eventWithoutParticipantsGetsEmptySet() {
        Event e = Mapper.toEntity(new EventRequest());
        assertNotNull(e.getParticipantIds());
        assertTrue(e.getParticipantIds().isEmpty());
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void toEntityCopieLesChampsEtLesIdentifiantsSansPartagerLEnsemble() {
        Set<Integer> ids = new HashSet<>(Set.of(1, 2));
        EventRequest r = new EventRequest("Gala", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), ids);

        Event e = Mapper.toEntity(r);

        assertNull(e.getIdEvent()); // attribué par le service (UUID)
        assertEquals("Gala", e.getDescription());
        assertEquals(Set.of(1, 2), e.getParticipantIds());
        assertNotSame(ids, e.getParticipantIds());
        assertTrue(e.getLogisticsIds().isEmpty());
    }

    @Test
    void toDtoExposeLesIdentifiants() {
        Event e = new Event();
        e.setIdEvent("e-1");
        e.setDescription("Gala");
        e.setCout(12.5f);
        e.setParticipantIds(Set.of(3));
        e.setLogisticsIds(Set.of(9));

        EventDTO d = Mapper.toDto(e);

        assertEquals("e-1", d.idEvent());
        assertEquals(12.5f, d.cout());
        assertEquals(Set.of(3), d.participantIds());
        assertEquals(Set.of(9), d.logisticsIds());
    }

    @Test
    void nullResteNull() {
        assertNull(Mapper.toDto(null));
        assertNull(Mapper.toDtos(null));
    }
}
