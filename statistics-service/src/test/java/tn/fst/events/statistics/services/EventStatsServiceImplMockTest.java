package tn.fst.events.statistics.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.clients.FeedbackAverageDTO;
import tn.fst.events.statistics.clients.LogisticsDTO;
import tn.fst.events.statistics.clients.ParticipantDTO;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.dto.Tache;
import tn.fst.events.statistics.sources.StatsSource;

/**
 * Repris de DEUX variantes d'EventStatsServiceImplMockTest (9 tests chacune, AVEC Mockito) :
 * feature/event-statistics et feature/feedback (qui ajoute la note moyenne). Fusionnées (9 + 9 → 9) :
 * testGetEventStats garde l'assertion averageRating de la variante feedback.
 * Adaptations : JUnit 4 → 5 ; les trois repositories simulés deviennent la StatsSource simulée
 * (AVANT CQRS : Feign) ; les relations deviennent des ID ; findByDateDebut… (requêtes JPA) devient
 * un filtre sur la liste des événements ; identifiant UUID ; getStatus() → getStatusCode().
 * 5 tests ajoutés : lots (un appel pour toute la liste), ID orphelin, tâche inconnue, note
 * moyenne globale pondérée, panne d'un service.
 */
@ExtendWith(MockitoExtension.class)
class EventStatsServiceImplMockTest {

    @Mock
    StatsSource source;

    @InjectMocks
    EventStatsServiceImpl eventStatsService;

    private ParticipantDTO participant(int id, Tache tache) {
        return new ParticipantDTO(id, "Nom" + id, "Prenom" + id, tache.name());
    }

    private LogisticsDTO logistics(int id, boolean reserve, float prixUnit, int quantite) {
        return new LogisticsDTO(id, "Log" + id, reserve, prixUnit, quantite);
    }

    private EventDTO buildEvent(String id) {
        return buildEvent(id, LocalDate.of(2025, 5, 10));
    }

    private EventDTO buildEvent(String id, LocalDate debut) {
        return new EventDTO(id, "Journée Portes Ouvertes", debut, debut.plusDays(2), 0f,
                new HashSet<>(Arrays.asList(1, 2, 3)), new HashSet<>(Arrays.asList(1, 2, 3)));
    }

    /** Participants 1 (organisateur), 2 et 3 (invités) ; logistique 50 + 10 réservée, 100 non réservée. */
    private void donneesLiees() {
        when(source.participants(anyCollection())).thenReturn(Arrays.asList(
                participant(1, Tache.ORGANISATEUR), participant(2, Tache.INVITE), participant(3, Tache.INVITE)));
        when(source.logistique(anyCollection())).thenReturn(Arrays.asList(
                logistics(1, true, 10f, 5),       // 50
                logistics(2, true, 2.5f, 4),      // 10
                logistics(3, false, 100f, 1)));   // non réservée -> ignorée
    }

    @Test
    void testGetEventStats() {
        when(source.evenement("e1")).thenReturn(Optional.of(buildEvent("e1")));
        donneesLiees();
        when(source.moyennes()).thenReturn(Map.of("e1", new FeedbackAverageDTO("e1", 4.5, 2)));

        EventStatsDTO stats = eventStatsService.getEventStats("e1");

        assertEquals("e1", stats.getIdEvent());
        assertEquals("Journée Portes Ouvertes", stats.getDescription());
        assertEquals(3, stats.getDureeJours());
        assertEquals(3, stats.getNbParticipants());
        assertEquals(Long.valueOf(1), stats.getParticipantsParTache().get(Tache.ORGANISATEUR));
        assertEquals(Long.valueOf(2), stats.getParticipantsParTache().get(Tache.INVITE));
        assertEquals(Long.valueOf(0), stats.getParticipantsParTache().get(Tache.SERVEUR));
        assertEquals(3, stats.getNbLogistics());
        assertEquals(2, stats.getNbLogisticsReservees());
        assertEquals(60f, stats.getCoutLogistiqueReservee(), 0.001);
        assertEquals(4.5, stats.getAverageRating(), 0.001); // variante feature/feedback
        verify(source, times(1)).evenement("e1");
    }

    @Test
    void testGetEventStatsSansParticipantsNiLogistics() {
        EventDTO e = new EventDTO("e2", "Event vide", null, null, 0f, null, null);
        when(source.evenement("e2")).thenReturn(Optional.of(e));
        when(source.participants(anyCollection())).thenReturn(List.of());
        when(source.logistique(anyCollection())).thenReturn(List.of());
        when(source.moyennes()).thenReturn(Map.of());

        EventStatsDTO stats = eventStatsService.getEventStats("e2");

        assertEquals(0, stats.getNbParticipants());
        assertEquals(0, stats.getNbLogistics());
        assertEquals(0, stats.getDureeJours());
        assertEquals(0f, stats.getCoutLogistiqueReservee(), 0.001);
        assertEquals(0.0, stats.getAverageRating(), 0.001);
    }

    @Test
    void testGetEventStatsNotFound() {
        when(source.evenement("inconnu")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> eventStatsService.getEventStats("inconnu"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void testGetUpcomingEventsStats() {
        LocalDate today = LocalDate.now();
        when(source.evenements()).thenReturn(Arrays.asList(
                buildEvent("lointain", today.plusDays(30)), buildEvent("passe", today.minusDays(5)),
                buildEvent("aujourdhui", today)));
        donneesLiees();
        when(source.moyennes()).thenReturn(Map.of());

        List<EventStatsDTO> result = eventStatsService.getUpcomingEventsStats();

        assertEquals(2, result.size());
        assertEquals("aujourdhui", result.get(0).getIdEvent()); // dateDebut >= aujourd'hui, triés
        assertEquals("lointain", result.get(1).getIdEvent());
    }

    @Test
    void testGetEventsStatsBetween() {
        LocalDate d1 = LocalDate.of(2025, 5, 1);
        LocalDate d2 = LocalDate.of(2025, 5, 31);
        when(source.evenements()).thenReturn(Arrays.asList(
                buildEvent("e1"), buildEvent("juin", LocalDate.of(2025, 6, 1))));
        donneesLiees();
        when(source.moyennes()).thenReturn(Map.of());

        List<EventStatsDTO> result = eventStatsService.getEventsStatsBetween(d1, d2);

        assertEquals(1, result.size());
        assertEquals(60f, result.get(0).getCoutLogistiqueReservee(), 0.001);
        verify(source, times(1)).evenements();
    }

    @Test
    void testGetEventsStatsBetweenIntervalleInvalide() {
        LocalDate d1 = LocalDate.of(2025, 6, 1);
        LocalDate d2 = LocalDate.of(2025, 5, 1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> eventStatsService.getEventsStatsBetween(d1, d2));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(source);
    }

    @Test
    void testGetAllEventsStatsTrieParDate() {
        when(source.evenements()).thenReturn(Arrays.asList(
                buildEvent("tard", LocalDate.of(2025, 9, 1)), buildEvent("tot", LocalDate.of(2025, 3, 1))));
        donneesLiees();
        when(source.moyennes()).thenReturn(Map.of());

        List<EventStatsDTO> result = eventStatsService.getAllEventsStats();

        assertEquals(2, result.size());
        assertEquals("tot", result.get(0).getIdEvent()); // le plus tôt en premier
        assertEquals("tard", result.get(1).getIdEvent());
    }

    @Test
    void testGetGlobalStats() {
        LocalDate today = LocalDate.now();
        when(source.evenements()).thenReturn(Arrays.asList(
                buildEvent("a", today.plusDays(1)), buildEvent("b", today.minusDays(1)),
                buildEvent("c", today.minusDays(2)), buildEvent("d", today.minusDays(3))));
        when(source.participants()).thenReturn(Arrays.asList(
                participant(1, Tache.ORGANISATEUR), participant(2, Tache.INVITE), participant(3, Tache.INVITE)));
        when(source.logistique()).thenReturn(Arrays.asList(
                logistics(1, true, 10f, 5),     // 50
                logistics(2, true, 2.5f, 4),    // 10
                logistics(3, false, 1f, 1),
                logistics(4, false, 1f, 1)));
        when(source.moyennes()).thenReturn(Map.of());

        GlobalStatsDTO stats = eventStatsService.getGlobalStats();

        assertEquals(4, stats.getNbEvents());
        assertEquals(1, stats.getNbEventsAVenir());
        assertEquals(3, stats.getNbParticipants());
        assertEquals(4, stats.getNbLogistics());
        assertEquals(2, stats.getNbLogisticsReservees());
        assertEquals(50f, stats.getTauxReservation(), 0.001);
        assertEquals(60f, stats.getCoutTotalLogistiqueReservee(), 0.001);
        assertEquals(Long.valueOf(2), stats.getParticipantsParTache().get(Tache.INVITE));
        assertEquals(Long.valueOf(0), stats.getParticipantsParTache().get(Tache.SERVEUR));
    }

    @Test
    void testGetGlobalStatsBaseVide() {
        when(source.evenements()).thenReturn(List.of());
        when(source.participants()).thenReturn(List.of());
        when(source.logistique()).thenReturn(Collections.emptyList());
        when(source.moyennes()).thenReturn(Map.of());

        GlobalStatsDTO stats = eventStatsService.getGlobalStats();

        assertEquals(0, stats.getNbEvents());
        assertEquals(0f, stats.getTauxReservation(), 0.001); // pas de division par zéro
        assertEquals(0f, stats.getCoutTotalLogistiqueReservee(), 0.001);
        assertEquals(0.0, stats.getNoteMoyenne(), 0.001);
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void lesObjetsLiesSontLusEnUnLotPourToutesLesStatistiques() {
        when(source.evenements()).thenReturn(Arrays.asList(
                new EventDTO("a", "A", LocalDate.of(2025, 1, 1), null, 0f, Set.of(1, 2), Set.of(7)),
                new EventDTO("b", "B", LocalDate.of(2025, 2, 1), null, 0f, Set.of(2, 3), Set.of())));
        when(source.participants(Set.of(1, 2, 3))).thenReturn(List.of());
        when(source.logistique(Set.of(7))).thenReturn(List.of());
        when(source.moyennes()).thenReturn(Map.of());

        eventStatsService.getAllEventsStats();

        verify(source, times(1)).participants(Set.of(1, 2, 3)); // union des ID, une seule fois
        verify(source, times(1)).logistique(Set.of(7));
        verify(source, times(1)).moyennes();
    }

    @Test
    void unIdOrphelinEstIgnore() {
        when(source.evenement("e1")).thenReturn(Optional.of(
                new EventDTO("e1", "Gala", null, null, 0f, Set.of(1, 99), Set.of())));
        when(source.participants(anyCollection())).thenReturn(List.of(participant(1, Tache.INVITE)));
        when(source.logistique(anyCollection())).thenReturn(List.of());
        when(source.moyennes()).thenReturn(Map.of());

        assertEquals(1, eventStatsService.getEventStats("e1").getNbParticipants()); // 99 supprimé ailleurs
    }

    @Test
    void uneTacheInconnueNeFaitPasEchouerLesStatistiques() {
        when(source.evenements()).thenReturn(List.of());
        when(source.participants()).thenReturn(List.of(new ParticipantDTO(1, "X", "Y", "PILOTE"),
                new ParticipantDTO(2, "X", "Y", null), participant(3, Tache.SERVEUR)));
        when(source.logistique()).thenReturn(List.of());
        when(source.moyennes()).thenReturn(Map.of());

        GlobalStatsDTO stats = eventStatsService.getGlobalStats();

        assertEquals(3, stats.getNbParticipants());
        assertEquals(Long.valueOf(1), stats.getParticipantsParTache().get(Tache.SERVEUR));
        assertEquals(Long.valueOf(0), stats.getParticipantsParTache().get(Tache.INVITE));
    }

    @Test
    void laNoteMoyenneGlobaleEstPondereeParLeNombreDAvis() {
        when(source.evenements()).thenReturn(List.of());
        when(source.participants()).thenReturn(List.of());
        when(source.logistique()).thenReturn(List.of());
        when(source.moyennes()).thenReturn(Map.of(
                "a", new FeedbackAverageDTO("a", 5.0, 3), "b", new FeedbackAverageDTO("b", 1.0, 1)));

        GlobalStatsDTO stats = eventStatsService.getGlobalStats();

        assertEquals(4, stats.getNbFeedbacks());
        assertEquals(4.0, stats.getNoteMoyenne(), 0.001); // (5×3 + 1×1) / 4, et non (5 + 1) / 2
    }

    @Test
    void unServiceEnPannePropageLe503() {
        when(source.evenements()).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "event-service indisponible (pas de réponse)"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> eventStatsService.getAllEventsStats());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verify(source, never()).moyennes();
    }

    // ---------------------------------------------------------------- ajouté (phase 3)

    @Test
    void lesNotesMoyennesSontArrondiesADeuxDecimales() {
        when(source.evenements()).thenReturn(List.of());
        when(source.participants()).thenReturn(List.of());
        when(source.logistique()).thenReturn(List.of());
        // moyennes telles que MySQL les renvoie : 13/3 -> 4.3333 (4 décimales)
        when(source.moyennes()).thenReturn(Map.of(
                "a", new FeedbackAverageDTO("a", 4.3333, 3), "b", new FeedbackAverageDTO("b", 4.0, 2)));

        assertEquals(4.2, eventStatsService.getGlobalStats().getNoteMoyenne()); // et non 4.19998
        assertEquals(4.33, EventStatsServiceImpl.arrondi(4.3333));
    }
}
