package tn.fst.events.event.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.event.clients.FeignErreurs;
import tn.fst.events.event.clients.LogisticsClient;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.clients.ParticipantClient;
import tn.fst.events.event.clients.ParticipantDTO;
import tn.fst.events.event.commands.EventCommands.AffecterLogistique;
import tn.fst.events.event.commands.EventCommands.AffecterParticipant;
import tn.fst.events.event.commands.EventCommands.CreateEvent;
import tn.fst.events.event.commands.EventCommands.UpdateEventCost;
import tn.fst.events.event.dto.EventDetailsDTO;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Repris de feature/feedback (10 tests AVEC Mockito), tous adaptés en phase 2 (404 renvoyé par
 * Feign, participantIds, getStatusCode()). 7 tests ajoutés en phase 2.
 * Phase 3 (CQRS) : CommandGateway simulée ; « l'événement est sauvegardé » devient « la commande
 * est envoyée » ; « jamais sauvegardé » devient « aucune commande envoyée ».
 */
@ExtendWith(MockitoExtension.class)
class EventServicesImplMockitoTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ParticipantClient participantClient;

    @Mock
    private LogisticsClient logisticsClient;

    @Mock
    private CommandGateway commandGateway;

    @InjectMocks
    private EventServicesImpl service;

    private static Event event(String id) {
        Event e = new Event();
        e.setIdEvent(id);
        e.setParticipantIds(null);
        e.setLogisticsIds(null);
        return e;
    }

    private static Event event() {
        return event("e-1");
    }

    private static ParticipantDTO participant(int id) {
        return new ParticipantDTO(id, "Doe", "Jane", "INVITE");
    }

    private static LogisticsDTO logistics(int id, boolean reserve, float prixUnit, int quantite) {
        return new LogisticsDTO(id, "Projector", reserve, prixUnit, quantite);
    }

    @Test
    void associatesParticipantWhenEventsCollectionIsNull() {
        Event event = event();
        when(participantClient.getParticipant(3)).thenReturn(participant(3));
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(event));

        assertSame(event, service.addAffectEvenParticipant(event, 3));

        assertEquals(Set.of(3), event.getParticipantIds());
        verify(commandGateway).sendAndWait(new CreateEvent("e-1", null, null, null, Set.of(3)));
    }

    @Test
    void addsEventToExistingParticipantEvents() {
        Event added = event();
        added.setParticipantIds(new HashSet<>(Set.of(1)));
        when(participantClient.getParticipant(3)).thenReturn(participant(3));
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(added));

        service.addAffectEvenParticipant(added, 3);

        assertEquals(Set.of(1, 3), added.getParticipantIds());
    }

    @Test
    void rejectsMissingParticipantForSingleAssociation() {
        Event event = event();
        when(participantClient.getParticipant(3)).thenThrow(FeignErreurs.introuvable("/participants/3"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addAffectEvenParticipant(event, 3));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals("Participant not found with id 3", error.getReason());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void rejectsEventWithNullParticipantSet() {
        Event event = event();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addAffectEvenParticipant(event));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(participantClient, eventRepository, commandGateway);
    }

    @Test
    void associatesAllParticipantsForNullAndExistingEventCollections() {
        Event event = event();
        event.setParticipantIds(new HashSet<>(Set.of(1, 2)));
        when(participantClient.getParticipants(Set.of(1, 2))).thenReturn(List.of(participant(1), participant(2)));
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(event));

        assertSame(event, service.addAffectEvenParticipant(event));

        assertEquals(Set.of(1, 2), event.getParticipantIds());
    }

    @Test
    void rejectsMissingParticipantInEventAssociationSet() {
        Event event = event();
        event.setParticipantIds(new HashSet<>(Set.of(1, 42)));
        when(participantClient.getParticipants(Set.of(1, 42))).thenReturn(List.of(participant(1)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addAffectEvenParticipant(event));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals("Participant not found with id [42]", error.getReason());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void addsLogisticsAndSendsAffecterLogistiqueWhenCollectionIsNull() {
        LogisticsDTO logistics = logistics(0, true, 1f, 1);
        LogisticsDTO creee = logistics(9, true, 1f, 1);
        when(eventRepository.findFirstByDescription("Conference")).thenReturn(Optional.of(event()));
        when(logisticsClient.addLogistics(logistics)).thenReturn(creee);

        assertSame(creee, service.addAffectLog(logistics, "Conference"));

        verify(commandGateway).sendAndWait(new AffecterLogistique("e-1", 9));
    }

    @Test
    void addsLogisticsToExistingCollectionAndSendsAffecterLogistique() {
        LogisticsDTO added = logistics(0, true, 1f, 1);
        Event event = event();
        event.setLogisticsIds(new HashSet<>(Set.of(4)));
        when(eventRepository.findFirstByDescription("Conference")).thenReturn(Optional.of(event));
        when(logisticsClient.addLogistics(added)).thenReturn(logistics(9, true, 1f, 1));

        service.addAffectLog(added, "Conference");

        verify(commandGateway).sendAndWait(new AffecterLogistique("e-1", 9));
    }

    @Test
    void returnsOnlyReservedLogisticsAndHandlesEventsWithoutLogistics() {
        Event emptyEvent = event();
        emptyEvent.setLogisticsIds(new HashSet<>());
        Event event = event();
        event.setLogisticsIds(new HashSet<>(Set.of(1, 2)));
        LogisticsDTO reserved = logistics(1, true, 1f, 1);
        LogisticsDTO unreserved = logistics(2, false, 1f, 1);
        when(eventRepository.findByDateDebutBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(emptyEvent, event));
        when(logisticsClient.getLogistics(Set.of(1, 2))).thenReturn(List.of(reserved, unreserved));

        List<LogisticsDTO> result = service.getLogisticsDates(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(List.of(reserved), result);
    }

    @Test
    void calculatesReservedCostsIndependentlyAndTreatsNullLogisticsAsEmpty() {
        Event withoutLogistics = event("e-1");
        Event withLogistics = event("e-2");
        withLogistics.setLogisticsIds(new HashSet<>(Set.of(1, 2)));
        when(participantClient.search("Tounsi", "Ahmed", "ORGANISATEUR")).thenReturn(List.of(participant(5)));
        when(eventRepository.findDistinctByParticipantIdsIn(Set.of(5)))
                .thenReturn(List.of(withoutLogistics, withLogistics));
        when(logisticsClient.coutReserve(Set.of(1, 2))).thenReturn(50.0f); // 12,5 × 4 ; non réservé ignoré

        service.calculCout();

        assertEquals(0.0f, withoutLogistics.getCout());
        assertEquals(50.0f, withLogistics.getCout());
        verify(commandGateway).sendAndWait(new UpdateEventCost("e-1", 0f));
        verify(commandGateway).sendAndWait(new UpdateEventCost("e-2", 50f));
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void unEvenementRefuseParLAgregatNeBloquePasLeCalculDesAutres() {
        Event perdu = event("perdu");
        Event sain = event("sain");
        when(participantClient.search("Tounsi", "Ahmed", "ORGANISATEUR")).thenReturn(List.of(participant(5)));
        when(eventRepository.findDistinctByParticipantIdsIn(Set.of(5))).thenReturn(List.of(perdu, sain));
        when(commandGateway.sendAndWait(new UpdateEventCost("perdu", 0f))).thenThrow(
                new org.axonframework.commandhandling.CommandExecutionException("absent", null,
                        new tn.fst.events.event.commands.ErreurCommande(404, "Event not found with id perdu")));

        List<Event> calcules = service.calculCout();

        assertEquals(List.of(sain), calcules);
        verify(commandGateway).sendAndWait(new UpdateEventCost("sain", 0f));
    }

    @Test
    void calculCoutSansOrganisateurNeTouchePasAuxEvenements() {
        when(participantClient.search("Tounsi", "Ahmed", "ORGANISATEUR")).thenReturn(List.of());

        assertTrue(service.calculCout().isEmpty());

        verifyNoInteractions(eventRepository, logisticsClient, commandGateway);
    }

    @Test
    void calculCoutPlanifieNeLeveRienQuandParticipantServiceEstEnPanne() {
        when(participantClient.search(anyString(), anyString(), anyString()))
                .thenThrow(FeignErreurs.injoignable("/participants/search"));

        service.calculCoutPlanifie(); // journalise « reporté » et attend la prochaine minute

        verifyNoInteractions(eventRepository, commandGateway);
    }

    @Test
    void affecterParticipantEnvoieLaCommandePuisRelitLEvenement() {
        Event event = event();
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(event));
        when(participantClient.getParticipant(3)).thenReturn(participant(3));

        assertSame(event, service.affecterParticipant("e-1", 3));

        verify(commandGateway).sendAndWait(new AffecterParticipant("e-1", 3));
    }

    @Test
    void affecterParticipantSurEvenementInconnuRepond404SansAppelDistant() {
        when(eventRepository.findById("inconnu")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.affecterParticipant("inconnu", 3));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(participantClient, commandGateway);
    }

    @Test
    void affecterLogistiqueEnPanneRepond503SansCommande() {
        Event event = event();
        LogisticsDTO chaises = logistics(0, true, 2.5f, 100);
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(event));
        when(logisticsClient.addLogistics(chaises)).thenThrow(FeignErreurs.erreurServeur("/logistics"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.affecterLogistique("e-1", chaises));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void detailsComposeLEvenementAvecSesParticipantsEtSaLogistique() {
        Event event = event();
        event.setDescription("Gala");
        event.setParticipantIds(new HashSet<>(Set.of(1)));
        event.setLogisticsIds(new HashSet<>(Set.of(9)));
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(event));
        when(participantClient.getParticipants(Set.of(1))).thenReturn(List.of(participant(1)));
        when(logisticsClient.getLogistics(Set.of(9))).thenReturn(List.of(logistics(9, true, 1f, 1)));

        EventDetailsDTO details = service.details("e-1");

        assertEquals("Gala", details.description());
        assertEquals("Doe", details.participants().get(0).nom());
        assertEquals(9, details.logistics().get(0).idLog());
    }

    @Test
    void detailsSansParticipantNiLogistiqueNAppelleAucunService() {
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(event()));

        EventDetailsDTO details = service.details("e-1");

        assertTrue(details.participants().isEmpty());
        assertTrue(details.logistics().isEmpty());
        verifyNoInteractions(participantClient, logisticsClient);
    }
}
