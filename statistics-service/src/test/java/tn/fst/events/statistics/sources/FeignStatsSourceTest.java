package tn.fst.events.statistics.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.clients.Clients.EventClient;
import tn.fst.events.statistics.clients.Clients.FeedbackClient;
import tn.fst.events.statistics.clients.Clients.LogisticsClient;
import tn.fst.events.statistics.clients.Clients.ParticipantClient;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.clients.FeedbackAverageDTO;
import tn.fst.events.statistics.clients.FeignErreurs;
import tn.fst.events.statistics.clients.ParticipantDTO;

/** Ajouté : la source AVANT CQRS (Feign) — lots, absence d'appel inutile, 404, panne. */
@ExtendWith(MockitoExtension.class)
class FeignStatsSourceTest {

    @Mock EventClient eventClient;
    @Mock ParticipantClient participantClient;
    @Mock LogisticsClient logisticsClient;
    @Mock FeedbackClient feedbackClient;

    @InjectMocks FeignStatsSource source;

    @Test
    void sonNomIndiqueLaVersionAvantCqrs() {
        assertEquals("feign-avant-cqrs", source.nom());
    }

    @Test
    void evenementInconnuDonneUnOptionalVide() {
        when(eventClient.getEvent("x")).thenThrow(FeignErreurs.introuvable("/events/x"));
        assertTrue(source.evenement("x").isEmpty());
    }

    @Test
    void evenementAvecEventServiceEnPanneDonne503() {
        when(eventClient.getEvent("x")).thenThrow(FeignErreurs.injoignable("/events/x"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> source.evenement("x"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test
    void lotVideNAppellePasLesServices() {
        assertTrue(source.participants(Set.of()).isEmpty());
        assertTrue(source.logistique(Set.of()).isEmpty());
        verifyNoInteractions(participantClient, logisticsClient);
    }

    @Test
    void lotDeParticipantsEnUnSeulAppel() {
        when(participantClient.getParticipants(Set.of(1, 2)))
                .thenReturn(List.of(new ParticipantDTO(1, "A", "a", "INVITE"), new ParticipantDTO(2, "B", "b", "SERVEUR")));
        assertEquals(2, source.participants(Set.of(1, 2)).size());
    }

    @Test
    void moyennesIndexeesParEvenement() {
        when(feedbackClient.getMoyennes()).thenReturn(List.of(new FeedbackAverageDTO("e1", 4.5, 2)));
        Map<String, FeedbackAverageDTO> moyennes = source.moyennes();
        assertEquals(4.5, moyennes.get("e1").averageRating());
    }

    @Test
    void listesCompletesLuesDansChaqueService() {
        when(eventClient.getEvents()).thenReturn(List.of(new EventDTO("e1", "Gala", null, null, 0f, Set.of(), Set.of())));
        when(participantClient.getParticipants()).thenReturn(List.of());
        when(logisticsClient.getLogistics()).thenReturn(List.of());
        assertEquals(1, source.evenements().size());
        assertTrue(source.participants().isEmpty());
        assertTrue(source.logistique().isEmpty());
    }
}
