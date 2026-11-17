package tn.fst.events.statistics.sources;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.clients.Clients.EventClient;
import tn.fst.events.statistics.clients.Clients.FeedbackClient;
import tn.fst.events.statistics.clients.Clients.LogisticsClient;
import tn.fst.events.statistics.clients.Clients.ParticipantClient;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.clients.FeedbackAverageDTO;
import tn.fst.events.statistics.clients.LogisticsDTO;
import tn.fst.events.statistics.clients.ParticipantDTO;
import tn.fst.events.statistics.clients.RemoteCall;

/**
 * ===== VERSION AVANT CQRS =====
 * Les statistiques sont recalculées à chaque requête à partir des quatre services métier, lus par
 * OpenFeign. Conséquences, mesurées et comparées à la phase 3 (captures C40 et C41) :
 * <ul>
 *   <li>4 appels réseau par requête (lots d'ID : le nombre d'appels ne dépend pas du nombre d'événements) ;</li>
 *   <li>une panne de n'importe lequel des quatre services rend les statistiques indisponibles (503) ;</li>
 *   <li>la charge de lecture des statistiques retombe sur les bases des services d'écriture.</li>
 * </ul>
 * Phase 3 : ProjectionStatsSource (projections de db_stats) est la source par défaut ; cette classe
 * n'est créée qu'avec stats.source=feign, pour comparer AVANT / APRÈS CQRS en démonstration.
 */
@Component
@ConditionalOnProperty(name = "stats.source", havingValue = "feign")
@RequiredArgsConstructor
public class FeignStatsSource implements StatsSource {

    public static final String NOM = "feign-avant-cqrs";

    private final EventClient eventClient;
    private final ParticipantClient participantClient;
    private final LogisticsClient logisticsClient;
    private final FeedbackClient feedbackClient;

    @Override
    public String nom() {
        return NOM;
    }

    @Override
    public List<EventDTO> evenements() {
        return RemoteCall.call("event-service", "Événements introuvables", eventClient::getEvents);
    }

    @Override
    public Optional<EventDTO> evenement(String idEvent) {
        try {
            return Optional.of(RemoteCall.call("event-service", "Event not found with id " + idEvent,
                    () -> eventClient.getEvent(idEvent)));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public List<ParticipantDTO> participants() {
        return RemoteCall.call("participant-service", "Participants introuvables", participantClient::getParticipants);
    }

    /** Un seul appel pour tout le lot ; aucun appel si le lot est vide. */
    @Override
    public List<ParticipantDTO> participants(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return RemoteCall.call("participant-service", "Participants introuvables",
                () -> participantClient.getParticipants(ids));
    }

    @Override
    public List<LogisticsDTO> logistique() {
        return RemoteCall.call("logistics-service", "Logistique introuvable", logisticsClient::getLogistics);
    }

    @Override
    public List<LogisticsDTO> logistique(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return RemoteCall.call("logistics-service", "Logistique introuvable", () -> logisticsClient.getLogistics(ids));
    }

    @Override
    public Map<String, FeedbackAverageDTO> moyennes() {
        return RemoteCall.call("feedback-service", "Moyennes introuvables", feedbackClient::getMoyennes).stream()
                .collect(Collectors.toMap(FeedbackAverageDTO::eventId, Function.identity(), (a, b) -> a));
    }
}
