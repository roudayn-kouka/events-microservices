package tn.fst.events.statistics.queries;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.queries.StatsQueries.ErreurRequete;
import tn.fst.events.statistics.queries.StatsQueries.ListeStats;
import tn.fst.events.statistics.queries.StatsQueries.StatsAVenirQuery;
import tn.fst.events.statistics.queries.StatsQueries.StatsEvenementQuery;
import tn.fst.events.statistics.queries.StatsQueries.StatsEvenementsQuery;
import tn.fst.events.statistics.queries.StatsQueries.StatsPeriodeQuery;
import tn.fst.events.statistics.queries.StatsQueries.StatsResumeQuery;
import tn.fst.events.statistics.services.IEventStatsService;
import tn.fst.events.statistics.sources.StatsSource;

/**
 * Implémentation PRINCIPALE de l'API statistiques (phase 3) : chaque appel devient une requête Axon
 * envoyée par QueryGateway (routée par Axon Server vers un @QueryHandler, sur cette instance ou
 * une autre). Le contrôleur ne change pas : il dépend toujours de IEventStatsService.
 */
@Primary
@Service
@RequiredArgsConstructor
public class StatsQueryGateway implements IEventStatsService {

    private final QueryGateway queryGateway;
    private final StatsSource source;

    @Override
    public EventStatsDTO getEventStats(String idEvent) {
        return attendre(queryGateway.query(new StatsEvenementQuery(idEvent), ResponseTypes.instanceOf(EventStatsDTO.class)));
    }

    @Override
    public List<EventStatsDTO> getAllEventsStats() {
        return liste(new StatsEvenementsQuery());
    }

    @Override
    public List<EventStatsDTO> getUpcomingEventsStats() {
        return liste(new StatsAVenirQuery());
    }

    @Override
    public List<EventStatsDTO> getEventsStatsBetween(LocalDate d1, LocalDate d2) {
        return liste(new StatsPeriodeQuery(d1, d2));
    }

    @Override
    public GlobalStatsDTO getGlobalStats() {
        return attendre(queryGateway.query(new StatsResumeQuery(), ResponseTypes.instanceOf(GlobalStatsDTO.class)));
    }

    /** Nom de la source de cette instance (en-tête X-Stats-Source) : lu localement, sans requête. */
    @Override
    public String source() {
        return source.nom();
    }

    private List<EventStatsDTO> liste(Object query) {
        return attendre(queryGateway.query(query, ResponseTypes.instanceOf(ListeStats.class))).elements();
    }

    private static <R> R attendre(CompletableFuture<R> reponse) {
        try {
            return reponse.join();
        } catch (CompletionException e) {
            throw traduire(e.getCause() == null ? e : e.getCause());
        }
    }

    static RuntimeException traduire(Throwable erreur) {
        if (erreur instanceof QueryExecutionException q) {
            Object details = q.getDetails().orElse(null);
            if (details instanceof ErreurRequete e) {
                return new ResponseStatusException(HttpStatus.valueOf(e.status()), e.message());
            }
            if (details instanceof Map<?, ?> champs && champs.get("status") instanceof Number status) {
                return new ResponseStatusException(HttpStatus.valueOf(status.intValue()), String.valueOf(champs.get("message")));
            }
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Requête statistique en échec : " + q.getMessage());
        }
        if (erreur instanceof NoHandlerForQueryException) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Aucune instance ne traite les requêtes statistiques");
        }
        return erreur instanceof RuntimeException r ? r : new IllegalStateException(erreur);
    }
}
