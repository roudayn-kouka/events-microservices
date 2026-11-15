package tn.fst.events.event.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.fst.events.event.clients.LogisticsClient;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.clients.ParticipantClient;
import tn.fst.events.event.clients.ParticipantDTO;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Repris de feature/event-statistics (10 tests AVEC Mockito : affectations, logistique, coût).
 * 9 adaptés, 1 supprimé : testAddParticipant (la création d'un participant appartient
 * désormais à participant-service, où elle est testée).
 * Adaptations : les repositories Participant et Logistics deviennent les clients Feign simulés ;
 * les relations JPA deviennent les ensembles d'ID participantIds et logisticsIds de l'événement ;
 * - addAffectLog sauvegarde TOUJOURS l'événement : les ID de logistique sont stockés dans
 *   db_event (dans le monolithe, la table de jointure était écrite par la sauvegarde de la
 *   logistique, d'où le « never save » quand l'ensemble existait déjà) ;
 * - événement sans logistique : liste vide au lieu de null (correction de feature/feedback) ;
 * - calculCout : la somme du réservé est calculée par logistics-service (GET /cout-reserve),
 *   le filtre « réservé seulement » y est testé ; ici, on vérifie l'appel et la remise à zéro.
 */
@ExtendWith(MockitoExtension.class)
class EventServicesAffectationMockTest {

    @Mock
    EventRepository eventRepository;
    @Mock
    ParticipantClient participantClient;
    @Mock
    LogisticsClient logisticsClient;

    @InjectMocks
    EventServicesImpl eventServices;

    private Event buildEvent(String description) {
        Event e = new Event();
        e.setIdEvent(description.toLowerCase());
        e.setDescription(description);
        e.setDateDebut(LocalDate.of(2025, 5, 10));
        e.setDateFin(LocalDate.of(2025, 5, 12));
        e.setParticipantIds(null);
        e.setLogisticsIds(null);
        return e;
    }

    private ParticipantDTO buildParticipant(int id) {
        return new ParticipantDTO(id, "Tounsi", "Ahmed", "ORGANISATEUR");
    }

    private LogisticsDTO buildLogistics(int id, String description, boolean reserve, float prixUnit, int quantite) {
        return new LogisticsDTO(id, description, reserve, prixUnit, quantite);
    }

    @Test
    void testAddAffectEvenParticipantSansEvenementsExistants() {
        Event event = buildEvent("Hackathon"); // participantIds == null
        when(participantClient.getParticipant(1)).thenReturn(buildParticipant(1));
        when(eventRepository.save(event)).thenReturn(event);

        Event result = eventServices.addAffectEvenParticipant(event, 1);

        assertSame(event, result);
        assertEquals(Set.of(1), event.getParticipantIds());
    }

    @Test
    void testAddAffectEvenParticipantAvecEvenementsExistants() {
        Event event = buildEvent("Hackathon");
        event.setParticipantIds(new HashSet<>(Collections.singletonList(2))); // déjà un inscrit
        when(participantClient.getParticipant(1)).thenReturn(buildParticipant(1));
        when(eventRepository.save(event)).thenReturn(event);

        eventServices.addAffectEvenParticipant(event, 1);

        assertEquals(2, event.getParticipantIds().size());
        assertTrue(event.getParticipantIds().containsAll(Set.of(1, 2)));
    }

    @Test
    void testAddAffectEvenParticipantPourTousLesParticipants() {
        Event event = buildEvent("Hackathon");
        event.setParticipantIds(new HashSet<>(Arrays.asList(1, 2)));
        when(participantClient.getParticipants(event.getParticipantIds()))
                .thenReturn(List.of(buildParticipant(1), buildParticipant(2)));
        when(eventRepository.save(event)).thenReturn(event);

        Event result = eventServices.addAffectEvenParticipant(event);

        assertSame(event, result);
        assertEquals(Set.of(1, 2), event.getParticipantIds());
        verify(participantClient, times(1)).getParticipants(Set.of(1, 2)); // un seul appel pour le lot
        verify(eventRepository, times(1)).save(event);
    }

    @Test
    void testAddAffectLogSurEvenementSansLogistique() {
        Event event = buildEvent("Hackathon"); // logisticsIds == null
        LogisticsDTO chaises = buildLogistics(0, "Chaises", true, 2.5f, 100);
        LogisticsDTO creee = buildLogistics(7, "Chaises", true, 2.5f, 100);
        when(eventRepository.findFirstByDescription("Hackathon")).thenReturn(Optional.of(event));
        when(logisticsClient.addLogistics(chaises)).thenReturn(creee);

        LogisticsDTO result = eventServices.addAffectLog(chaises, "Hackathon");

        assertSame(creee, result);
        assertEquals(Collections.singleton(7), event.getLogisticsIds());
        verify(eventRepository).save(event); // l'événement est sauvegardé avec son nouvel ensemble
    }

    @Test
    void testAddAffectLogSurEvenementAvecLogistique() {
        Event event = buildEvent("Hackathon");
        event.setLogisticsIds(new HashSet<>(Collections.singletonList(3))); // stands
        LogisticsDTO chaises = buildLogistics(0, "Chaises", true, 2.5f, 100);
        when(eventRepository.findFirstByDescription("Hackathon")).thenReturn(Optional.of(event));
        when(logisticsClient.addLogistics(chaises)).thenReturn(buildLogistics(7, "Chaises", true, 2.5f, 100));

        eventServices.addAffectLog(chaises, "Hackathon");

        assertEquals(Set.of(3, 7), event.getLogisticsIds());
        verify(eventRepository).save(event); // adapté : les ID vivent dans db_event
    }

    @Test
    void testGetLogisticsDatesNeRetourneQueLaLogistiqueReservee() {
        LocalDate debut = LocalDate.of(2025, 5, 1);
        LocalDate fin = LocalDate.of(2025, 5, 31);
        LogisticsDTO reservee = buildLogistics(1, "Chaises", true, 2.5f, 100);
        LogisticsDTO nonReservee = buildLogistics(2, "Goodies", false, 4f, 50);
        Event event = buildEvent("Hackathon");
        event.setLogisticsIds(new HashSet<>(Arrays.asList(1, 2)));
        when(eventRepository.findByDateDebutBetween(debut, fin)).thenReturn(Collections.singletonList(event));
        when(logisticsClient.getLogistics(Set.of(1, 2))).thenReturn(List.of(reservee, nonReservee));

        List<LogisticsDTO> result = eventServices.getLogisticsDates(debut, fin);

        assertEquals(Collections.singletonList(reservee), result);
    }

    @Test
    void testGetLogisticsDatesEvenementSansLogistique() {
        LocalDate debut = LocalDate.of(2025, 5, 1);
        LocalDate fin = LocalDate.of(2025, 5, 31);
        Event event = buildEvent("Hackathon");
        event.setLogisticsIds(new HashSet<>());
        when(eventRepository.findByDateDebutBetween(debut, fin)).thenReturn(Collections.singletonList(event));

        // adapté : liste vide (et aucun appel distant) au lieu de null
        assertTrue(eventServices.getLogisticsDates(debut, fin).isEmpty());
        verifyNoInteractions(logisticsClient);
    }

    @Test
    void testGetLogisticsDatesAucunEvenement() {
        LocalDate debut = LocalDate.of(2025, 5, 1);
        LocalDate fin = LocalDate.of(2025, 5, 31);
        when(eventRepository.findByDateDebutBetween(debut, fin)).thenReturn(Collections.emptyList());

        assertTrue(eventServices.getLogisticsDates(debut, fin).isEmpty());
    }

    @Test
    void testCalculCoutNeCompteQueLaLogistiqueReservee() {
        Event avecLogistique = buildEvent("Hackathon");
        avecLogistique.setLogisticsIds(new HashSet<>(Arrays.asList(1, 2, 3))); // chaises, stands, goodies
        Event sansLogistique = buildEvent("Forum");                           // logisticsIds == null
        sansLogistique.setCout(999f);
        when(participantClient.search("Tounsi", "Ahmed", "ORGANISATEUR")).thenReturn(List.of(buildParticipant(1)));
        when(eventRepository.findDistinctByParticipantIdsIn(Set.of(1)))
                .thenReturn(Arrays.asList(avecLogistique, sansLogistique));
        // logistics-service : 250 (chaises) + 240 (stands) ; goodies non réservés ignorés
        when(logisticsClient.coutReserve(Set.of(1, 2, 3))).thenReturn(490f);

        eventServices.calculCout();

        assertEquals(490f, avecLogistique.getCout(), 0.001);
        assertEquals(0f, sansLogistique.getCout(), 0.001); // somme remise à zéro par événement
        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, times(2)).save(saved.capture());
        assertEquals(Arrays.asList(avecLogistique, sansLogistique), saved.getAllValues());
        verify(logisticsClient, times(1)).coutReserve(Set.of(1, 2, 3)); // aucun appel pour « Forum »
        verify(logisticsClient, never()).coutReserve(Set.of());
    }
}
