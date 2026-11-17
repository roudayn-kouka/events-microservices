package tn.fst.events.statistics.queries;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;
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
import tn.fst.events.statistics.services.EventStatsServiceImpl;

/**
 * CÔTÉ REQUÊTES CQRS : les @QueryHandler, appelés par le bus de requêtes d'Axon. Ils délèguent au
 * calcul (EventStatsServiceImpl, inchangé depuis la phase 2) ; une erreur métier (404, 400, 503)
 * repart avec son code dans les « détails » de la QueryExecutionException.
 */
@Component
@RequiredArgsConstructor
public class StatsQueryHandlers {

    private final EventStatsServiceImpl calcul;

    @QueryHandler
    public ListeStats handle(StatsEvenementsQuery query) {
        return new ListeStats(executer(calcul::getAllEventsStats));
    }

    @QueryHandler
    public EventStatsDTO handle(StatsEvenementQuery query) {
        return executer(() -> calcul.getEventStats(query.idEvent()));
    }

    @QueryHandler
    public ListeStats handle(StatsAVenirQuery query) {
        return new ListeStats(executer(calcul::getUpcomingEventsStats));
    }

    @QueryHandler
    public ListeStats handle(StatsPeriodeQuery query) {
        return new ListeStats(executer(() -> calcul.getEventsStatsBetween(query.d1(), query.d2())));
    }

    @QueryHandler
    public GlobalStatsDTO handle(StatsResumeQuery query) {
        return executer(calcul::getGlobalStats);
    }

    private static <R> R executer(Supplier<R> calculer) {
        try {
            return calculer.get();
        } catch (ResponseStatusException e) {
            throw new QueryExecutionException(e.getReason(), e, new ErreurRequete(e.getStatusCode().value(), e.getReason()));
        }
    }
}
