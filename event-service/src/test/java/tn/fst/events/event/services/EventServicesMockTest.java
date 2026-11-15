package tn.fst.events.event.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.event.clients.LogisticsClient;
import tn.fst.events.event.clients.ParticipantClient;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Repris de feature/event (5 tests, AVEC Mockito : repository simulé, aucune base).
 * Adaptations : JUnit 4 → JUnit 5 ; identifiant int → String (UUID) ; les repositories
 * Participant et Logistics simulés deviennent les clients Feign simulés. 2 tests ajoutés
 * (UUID attribué à la création, dates incohérentes refusées).
 */
@ExtendWith(MockitoExtension.class)
class EventServicesMockTest {

    private static final String ID = "3f1c2a8e-6b4d-4e2a-9c1f-0a7b5d9e2c11";

    @Mock
    EventRepository eventRepository;
    @Mock
    ParticipantClient participantClient;
    @Mock
    LogisticsClient logisticsClient;

    @InjectMocks
    EventServicesImpl eventServices;

    private Event buildEvent(String id) {
        Event e = new Event();
        e.setIdEvent(id);
        e.setDescription("Journée Portes Ouvertes");
        e.setDateDebut(LocalDate.of(2025, 5, 10));
        e.setDateFin(LocalDate.of(2025, 5, 12));
        e.setCout(1500f);
        return e;
    }

    @Test
    void testAddEvent() {
        Event event = buildEvent(ID);
        when(eventRepository.save(event)).thenReturn(event);

        Event result = eventServices.addEvent(event);

        assertNotNull(result);
        assertEquals(ID, result.getIdEvent());
        assertEquals("Journée Portes Ouvertes", result.getDescription());
        verify(eventRepository, times(1)).save(event);
    }

    @Test
    void testRetrieveEvent() {
        Event event = buildEvent(ID);
        when(eventRepository.findById(ID)).thenReturn(Optional.of(event));

        Event result = eventServices.retrieveEvent(ID);

        assertNotNull(result);
        assertEquals(1500f, result.getCout(), 0.001);
        verify(eventRepository).findById(ID);
    }

    @Test
    void testRetrieveEventNotFound() {
        when(eventRepository.findById("inconnu")).thenReturn(Optional.empty());

        assertNull(eventServices.retrieveEvent("inconnu"));
    }

    @Test
    void testUpdateEvent() {
        Event event = buildEvent(ID);
        event.setDescription("Hackathon");
        event.setCout(3000f);
        when(eventRepository.save(event)).thenReturn(event);

        Event result = eventServices.updateEvent(event);

        assertEquals("Hackathon", result.getDescription());
        assertEquals(3000f, result.getCout(), 0.001);
        verify(eventRepository, times(1)).save(event);
    }

    @Test
    void testDeleteEvent() {
        doNothing().when(eventRepository).deleteById(ID);

        eventServices.deleteEvent(ID);

        verify(eventRepository, times(1)).deleteById(ID);
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void addEventAttribueUnUuidQuandLIdentifiantEstAbsent() {
        Event event = buildEvent(null);
        when(eventRepository.save(event)).thenReturn(event);

        Event result = eventServices.addEvent(event);

        assertNotNull(result.getIdEvent());
        assertEquals(result.getIdEvent(), UUID.fromString(result.getIdEvent()).toString());
    }

    @Test
    void addEventRefuseUneDateDeFinAnterieureALaDateDeDebut() {
        Event event = buildEvent(null);
        event.setDateFin(LocalDate.of(2025, 5, 9));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventServices.addEvent(event));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(eventRepository, never()).save(any(Event.class));
    }
}
