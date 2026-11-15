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
import tn.fst.events.event.dto.EventDetailsDTO;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Repris de feature/feedback (10 tests AVEC Mockito), tous adaptés :
 * - « participant introuvable » : le repository vide devient un 404 renvoyé par Feign ;
 * - les ensembles d'événements du participant deviennent l'ensemble participantIds de l'événement ;
 * - getStatus() (Boot 2) devient getStatusCode() (Boot 3) ;
 * - addAffectLog sauvegarde toujours l'événement (les ID de logistique sont dans db_event).
 * 7 tests ajoutés : affecterParticipant, affecterLogistique, details, panne d'un service (503).
 */
@ExtendWith(MockitoExtension.class)
class EventServicesImplMockitoTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ParticipantClient participantClient;

    @Mock
    private LogisticsClient logisticsClient;

    @InjectMocks
    private EventServicesImpl service;

    private static Event event() {
        Event e = new Event();
        e.setIdEvent("e-1");
        e.setParticipantIds(null);
        e.setLogisticsIds(null);
        return e;
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
        when(eventRepository.save(event)).thenReturn(event);

        assertSame(event, service.addAffectEvenParticipant(event, 3));

        assertEquals(Set.of(3), event.getParticipantIds());
        verify(eventRepository).save(event);
    }

    @Test
    void addsEventToExistingParticipantEvents() {
        Event added = event();
        added.setParticipantIds(new HashSet<>(Set.of(1)));
        when(participantClient.getParticipant(3)).thenReturn(participant(3));
        when(eventRepository.save(added)).thenReturn(added);

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
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void rejectsEventWithNullParticipantSet() {
        Event event = event();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addAffectEvenParticipant(event));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(participantClient, eventRepository);
    }

    @Test
    void associatesAllParticipantsForNullAndExistingEventCollections() {
        Event event = event();
        event.setParticipantIds(new HashSet<>(Set.of(1, 2)));
        when(participantClient.getParticipants(Set.of(1, 2))).thenReturn(List.of(participant(1), participant(2)));
        when(eventRepository.save(event)).thenReturn(event);

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
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void addsLogisticsAndSavesEventWhenCollectionIsNull() {
        LogisticsDTO logistics = logistics(0, true, 1f, 1);
        LogisticsDTO creee = logistics(9, true, 1f, 1);
        Event event = event();
        when(eventRepository.findFirstByDescription("Conference")).thenReturn(Optional.of(event));
        when(logisticsClient.addLogistics(logistics)).thenReturn(creee);

        assertSame(creee, service.addAffectLog(logistics, "Conference"));

        assertEquals(Set.of(9), event.getLogisticsIds());
        verify(eventRepository).save(event);
    }

    @Test
    void addsLogisticsToExistingCollectionAndSavesEvent() {
        LogisticsDTO added = logistics(0, true, 1f, 1);
        Event event = event();
        event.setLogisticsIds(new HashSet<>(Set.of(4)));
        when(eventRepository.findFirstByDescription("Conference")).thenReturn(Optional.of(event));
        when(logisticsClient.addLogistics(added)).thenReturn(logistics(9, true, 1f, 1));

        service.addAffectLog(added, "Conference");

        assertEquals(Set.of(4, 9), event.getLogisticsIds());
        verify(eventRepository).save(event);
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
        Event withoutLogistics = event();
        Event withLogistics = event();
        withLogistics.setLogisticsIds(new HashSet<>(Set.of(1, 2)));
        when(participantClient.search("Tounsi", "Ahmed", "ORGANISATEUR")).thenReturn(List.of(participant(5)));
        when(eventRepository.findDistinctByParticipantIdsIn(Set.of(5)))
                .thenReturn(List.of(withoutLogistics, withLogistics));
        when(logisticsClient.coutReserve(Set.of(1, 2))).thenReturn(50.0f); // 12,5 × 4 ; non réservé ignoré
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.calculCout();

        assertEquals(0.0f, withoutLogistics.getCout());
        assertEquals(50.0f, withLogistics.getCout());
        verify(eventRepository).save(withoutLogistics);
        verify(eventRepository).save(withLogistics);
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void calculCoutSansOrganisateurNeTouchePasAuxEvenements() {
        when(participantClient.search("Tounsi", "Ahmed", "ORGANISATEUR")).thenReturn(List.of());

        assertTrue(service.calculCout().isEmpty());

        verifyNoInteractions(eventRepository, logisticsClient);
    }

    @Test
    void calculCoutPlanifieNeLeveRienQuandParticipantServiceEstEnPanne() {
        when(participantClient.search(anyString(), anyString(), anyString()))
                .thenThrow(FeignErreurs.injoignable("/participants/search"));

        service.calculCoutPlanifie(); // journalise « reporté » et attend la prochaine minute

        verifyNoInteractions(eventRepository);
    }

    @Test
    void affecterParticipantAjouteLIdALEvenementExistant() {
        Event event = event();
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(event));
        when(participantClient.getParticipant(3)).thenReturn(participant(3));
        when(eventRepository.save(event)).thenReturn(event);

        assertSame(event, service.affecterParticipant("e-1", 3));

        assertEquals(Set.of(3), event.getParticipantIds());
    }

    @Test
    void affecterParticipantSurEvenementInconnuRepond404SansAppelDistant() {
        when(eventRepository.findById("inconnu")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.affecterParticipant("inconnu", 3));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(participantClient);
    }

    @Test
    void affecterLogistiqueEnPanneRepond503SansModifierLEvenement() {
        Event event = event();
        LogisticsDTO chaises = logistics(0, true, 2.5f, 100);
        when(eventRepository.findById("e-1")).thenReturn(Optional.of(event));
        when(logisticsClient.addLogistics(chaises)).thenThrow(FeignErreurs.erreurServeur("/logistics"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.affecterLogistique("e-1", chaises));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(event.getLogisticsIds() == null || event.getLogisticsIds().isEmpty());
        verify(eventRepository, never()).save(any(Event.class));
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
