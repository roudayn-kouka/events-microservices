package tn.fst.events.event.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.event.clients.LogisticsClient;
import tn.fst.events.event.clients.ParticipantClient;
import tn.fst.events.event.commands.ErreurCommande;
import tn.fst.events.event.commands.EventCommands.CreateEvent;
import tn.fst.events.event.commands.EventCommands.DeleteEvent;
import tn.fst.events.event.commands.EventCommands.UpdateEvent;
import tn.fst.events.event.commands.EventCommands.UpdateEventCost;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Repris de feature/event (5 tests, AVEC Mockito : repository simulé, aucune base).
 * Adaptations phase 2 : JUnit 4 → JUnit 5 ; identifiant int → String (UUID) ; repositories
 * Participant et Logistics → clients Feign simulés. 2 tests ajoutés (UUID, dates incohérentes).
 * Adaptations phase 3 (CQRS) : les écritures envoient des commandes (CommandGateway simulée) au
 * lieu d'appeler eventRepository.save / deleteById ; la valeur renvoyée est relue dans le modèle
 * de lecture. 3 tests ajoutés : traduction des erreurs de l'agrégat (404, détails reçus sous forme
 * de Map, commande refusée sans détails → 409).
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
    @Mock
    CommandGateway commandGateway;

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
        when(eventRepository.findById(ID)).thenReturn(Optional.of(event));

        Event result = eventServices.addEvent(event);

        assertNotNull(result);
        assertEquals(ID, result.getIdEvent());
        assertEquals("Journée Portes Ouvertes", result.getDescription());
        verify(commandGateway, times(1)).sendAndWait(new CreateEvent(ID, "Journée Portes Ouvertes",
                LocalDate.of(2025, 5, 10), LocalDate.of(2025, 5, 12), Set.of()));
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
        when(eventRepository.findById(ID)).thenReturn(Optional.of(event));

        Event result = eventServices.updateEvent(event);

        assertEquals("Hackathon", result.getDescription());
        assertEquals(3000f, result.getCout(), 0.001);
        verify(commandGateway, times(1)).sendAndWait(
                new UpdateEvent(ID, "Hackathon", LocalDate.of(2025, 5, 10), LocalDate.of(2025, 5, 12)));
        verify(commandGateway, times(1)).sendAndWait(new UpdateEventCost(ID, 3000f));
    }

    @Test
    void testDeleteEvent() {
        eventServices.deleteEvent(ID);

        verify(commandGateway, times(1)).sendAndWait(new DeleteEvent(ID));
    }

    // ---------------------------------------------------------------- ajoutés (phase 2)

    @Test
    void addEventAttribueUnUuidQuandLIdentifiantEstAbsent() {
        Event event = buildEvent(null);
        when(eventRepository.findById(anyString())).thenReturn(Optional.of(event));

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
        verifyNoInteractions(commandGateway);
    }

    // ---------------------------------------------------------------- ajoutés (phase 3, CQRS)

    @Test
    void regleRefuseeParLAgregatRedevientUneErreurHttp() {
        when(commandGateway.sendAndWait(any())).thenThrow(new CommandExecutionException("supprimé", null,
                new ErreurCommande(404, "Event not found with id " + ID)));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventServices.deleteEvent(ID));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Event not found with id " + ID, e.getReason());
    }

    @Test
    void detailsRecusSousFormeDeMapSontAussiTraduits() {
        when(commandGateway.sendAndWait(any())).thenThrow(new CommandExecutionException("dates", null,
                Map.of("status", 400, "message", "La date de fin précède la date de début")));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventServices.deleteEvent(ID));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void commandeRefuseeSansDetailsDonne409() {
        when(commandGateway.sendAndWait(any())).thenThrow(new CommandExecutionException("état incompatible", null));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventServices.deleteEvent(ID));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }
}
