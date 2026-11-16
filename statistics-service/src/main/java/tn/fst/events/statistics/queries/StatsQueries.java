package tn.fst.events.statistics.queries;

import java.time.LocalDate;

/**
 * Requêtes Axon du côté lecture (QueryGateway → @QueryHandler). Internes à statistics-service :
 * l'API HTTP reste la même, c'est son implémentation qui passe par le bus de requêtes.
 */
public final class StatsQueries {

    private StatsQueries() {
    }

    public record StatsEvenementsQuery() {
    }

    public record StatsEvenementQuery(String idEvent) {
    }

    public record StatsAVenirQuery() {
    }

    public record StatsPeriodeQuery(LocalDate d1, LocalDate d2) {
    }

    public record StatsResumeQuery() {
    }

    /** « Détails » d'une QueryExecutionException : code HTTP et message, transportés par Axon Server. */
    public record ErreurRequete(int status, String message) {
    }
}
