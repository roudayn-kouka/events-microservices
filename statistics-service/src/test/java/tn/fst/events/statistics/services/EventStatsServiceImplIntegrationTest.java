package tn.fst.events.statistics.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tn.fst.events.statistics.clients.Clients.EventClient;
import tn.fst.events.statistics.clients.Clients.FeedbackClient;
import tn.fst.events.statistics.clients.Clients.LogisticsClient;
import tn.fst.events.statistics.clients.Clients.ParticipantClient;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.clients.FeedbackAverageDTO;
import tn.fst.events.statistics.clients.LogisticsDTO;
import tn.fst.events.statistics.clients.ParticipantDTO;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.dto.Tache;

/**
 * Repris de feature/event-statistics (4 tests SANS Mockito sur le service : vrai service + vraie
 * source + contexte Spring complet). Adaptés : AVANT CQRS, il n'y a plus de base locale ; les
 * données viennent des quatre clients Feign, remplacés par des « services distants » en mémoire
 * (@MockitoBean). Le calcul et la source réelle (FeignStatsSource) sont donc testés ensemble ;
 * les tests gardent leurs scénarios (événement, à venir, intervalle, global avant / après).
 */
@SpringBootTest
class EventStatsServiceImplIntegrationTest {

    @Autowired IEventStatsService eventStatsService;

    @MockitoBean EventClient eventClient;
    @MockitoBean ParticipantClient participantClient;
    @MockitoBean LogisticsClient logisticsClient;
    @MockitoBean FeedbackClient feedbackClient;

    // « bases » des services distants, en mémoire
    final List<EventDTO> events = new ArrayList<>();
    final List<ParticipantDTO> participants = new ArrayList<>();
    final List<LogisticsDTO> logistics = new ArrayList<>();
    int sequence;

    @BeforeEach
    void servicesDistants() {
        when(eventClient.getEvents()).thenAnswer(i -> List.copyOf(events));
        when(eventClient.getEvent(org.mockito.ArgumentMatchers.anyString())).thenAnswer(i ->
                events.stream().filter(e -> e.idEvent().equals(i.getArgument(0))).findFirst().orElseThrow());
        when(participantClient.getParticipants()).thenAnswer(i -> List.copyOf(participants));
        when(participantClient.getParticipants(anyCollection())).thenAnswer(i -> participants.stream()
                .filter(p -> i.<Collection<Integer>>getArgument(0).contains(p.idPart())).toList());
        when(logisticsClient.getLogistics()).thenAnswer(i -> List.copyOf(logistics));
        when(logisticsClient.getLogistics(anyCollection())).thenAnswer(i -> logistics.stream()
                .filter(l -> i.<Collection<Integer>>getArgument(0).contains(l.idLog())).toList());
        when(feedbackClient.getMoyennes()).thenReturn(List.of());
    }

    private int saveLogistics(String description, boolean reserve, float prixUnit, int quantite) {
        logistics.add(new LogisticsDTO(++sequence, description, reserve, prixUnit, quantite));
        return sequence;
    }

    private int saveParticipant(String nom, Tache tache) {
        participants.add(new ParticipantDTO(++sequence, nom, "Test", tache.name()));
        return sequence;
    }

    private EventDTO saveEvent(String description, LocalDate debut, LocalDate fin, Set<Integer> participantIds) {
        Set<Integer> logs = Set.of(
                saveLogistics("Chaises", true, 12.5f, 40),  // 500
                saveLogistics("Tables", true, 50f, 2),      // 100
                saveLogistics("Sono", false, 300f, 1));     // non réservée
        EventDTO e = new EventDTO("ev-" + (++sequence), description, debut, fin, 0f, participantIds, logs);
        events.add(e);
        return e;
    }

    @Test
    void testGetEventStats() {
        Set<Integer> inscrits = Set.of(saveParticipant("Tounsi", Tache.ORGANISATEUR),
                saveParticipant("Ben Ali", Tache.ANIMATEUR), saveParticipant("Trabelsi", Tache.ANIMATEUR));
        EventDTO event = saveEvent("Hackathon", LocalDate.of(2025, 5, 10), LocalDate.of(2025, 5, 11), inscrits);
        when(feedbackClient.getMoyennes()).thenReturn(List.of(new FeedbackAverageDTO(event.idEvent(), 4.0, 3)));

        EventStatsDTO stats = eventStatsService.getEventStats(event.idEvent());

        assertEquals("Hackathon", stats.getDescription());
        assertEquals(2, stats.getDureeJours());
        assertEquals(3, stats.getNbParticipants());
        assertEquals(Long.valueOf(2), stats.getParticipantsParTache().get(Tache.ANIMATEUR));
        assertEquals(Long.valueOf(1), stats.getParticipantsParTache().get(Tache.ORGANISATEUR));
        assertEquals(3, stats.getNbLogistics());
        assertEquals(2, stats.getNbLogisticsReservees());
        assertEquals(600f, stats.getCoutLogistiqueReservee(), 0.001);
        assertEquals(4.0, stats.getAverageRating(), 0.001);
    }

    @Test
    void testGetUpcomingEventsStats() {
        LocalDate today = LocalDate.now();
        EventDTO passe = saveEvent("Event passé", today.minusDays(10), today.minusDays(9), Set.of());
        EventDTO proche = saveEvent("Event proche", today.plusDays(2), today.plusDays(3), Set.of());
        EventDTO lointain = saveEvent("Event lointain", today.plusDays(30), today.plusDays(31), Set.of());

        List<String> ids = eventStatsService.getUpcomingEventsStats().stream().map(EventStatsDTO::getIdEvent).toList();

        assertFalse(ids.contains(passe.idEvent()));
        assertTrue(ids.contains(proche.idEvent()));
        assertTrue(ids.contains(lointain.idEvent()));
        // tri par date de début croissante
        assertTrue(ids.indexOf(proche.idEvent()) < ids.indexOf(lointain.idEvent()));
    }

    @Test
    void testGetEventsStatsBetween() {
        EventDTO mai = saveEvent("Event mai", LocalDate.of(2025, 5, 15), LocalDate.of(2025, 5, 16), Set.of());
        EventDTO juillet = saveEvent("Event juillet", LocalDate.of(2025, 7, 15), LocalDate.of(2025, 7, 16), Set.of());

        List<String> ids = eventStatsService.getEventsStatsBetween(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31))
                .stream().map(EventStatsDTO::getIdEvent).toList();

        assertTrue(ids.contains(mai.idEvent()));
        assertFalse(ids.contains(juillet.idEvent()));
    }

    @Test
    void testGetGlobalStats() {
        GlobalStatsDTO avant = eventStatsService.getGlobalStats();
        int gharbi = saveParticipant("Gharbi", Tache.SERVEUR);
        saveEvent("Forum", LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), Set.of(gharbi));

        GlobalStatsDTO apres = eventStatsService.getGlobalStats();

        assertEquals(avant.getNbEvents() + 1, apres.getNbEvents());
        assertEquals(avant.getNbEventsAVenir() + 1, apres.getNbEventsAVenir());
        assertEquals(avant.getNbParticipants() + 1, apres.getNbParticipants());
        assertEquals(avant.getNbLogistics() + 3, apres.getNbLogistics());
        assertEquals(avant.getNbLogisticsReservees() + 2, apres.getNbLogisticsReservees());
        assertEquals(avant.getCoutTotalLogistiqueReservee() + 600f, apres.getCoutTotalLogistiqueReservee(), 0.01);
        assertEquals(Long.valueOf(avant.getParticipantsParTache().get(Tache.SERVEUR) + 1),
                apres.getParticipantsParTache().get(Tache.SERVEUR));
    }
}
