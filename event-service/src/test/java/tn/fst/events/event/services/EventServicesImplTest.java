package tn.fst.events.event.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.fst.events.event.entities.Event;

/**
 * Repris de feature/event (4 tests, SANS Mockito : contexte Spring complet et vraie base).
 * Adaptations : JUnit 4 → JUnit 5 ; base H2 (src/test/resources/application.yml) au lieu de la
 * MySQL du monolithe ; « id > 0 » devient « id est un UUID ».
 */
@Slf4j
@SpringBootTest
class EventServicesImplTest {

    @Autowired
    IEventServices eventServices;

    private Event buildEvent() {
        Event e = new Event();
        e.setDescription("Journée Portes Ouvertes");
        e.setDateDebut(LocalDate.of(2025, 5, 10));
        e.setDateFin(LocalDate.of(2025, 5, 12));
        e.setCout(1500f);
        return e;
    }

    @Test
    void testAddEvent() {
        Event saved = eventServices.addEvent(buildEvent());
        log.info("event added : {}", saved);

        assertNotNull(saved);
        assertNotNull(UUID.fromString(saved.getIdEvent()));
        assertEquals("Journée Portes Ouvertes", saved.getDescription());
        assertEquals(1500f, saved.getCout(), 0.001);

        eventServices.deleteEvent(saved.getIdEvent());
    }

    @Test
    void testRetrieveEvent() {
        Event saved = eventServices.addEvent(buildEvent());

        Event found = eventServices.retrieveEvent(saved.getIdEvent());

        assertNotNull(found);
        assertEquals(saved.getIdEvent(), found.getIdEvent());
        assertEquals(LocalDate.of(2025, 5, 10), found.getDateDebut());

        eventServices.deleteEvent(saved.getIdEvent());
    }

    @Test
    void testUpdateEvent() {
        Event saved = eventServices.addEvent(buildEvent());

        saved.setDescription("Hackathon");
        saved.setCout(3000f);
        Event updated = eventServices.updateEvent(saved);

        assertEquals(saved.getIdEvent(), updated.getIdEvent());
        assertEquals("Hackathon", updated.getDescription());
        assertEquals(3000f, updated.getCout(), 0.001);

        eventServices.deleteEvent(saved.getIdEvent());
    }

    @Test
    void testDeleteEvent() {
        Event saved = eventServices.addEvent(buildEvent());
        String id = saved.getIdEvent();

        eventServices.deleteEvent(id);

        assertNull(eventServices.retrieveEvent(id));
    }
}
