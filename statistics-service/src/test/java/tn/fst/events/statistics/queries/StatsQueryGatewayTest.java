package tn.fst.events.statistics.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.queries.StatsQueries.ErreurRequete;

/** Ajouté : traduction des erreurs reçues du bus de requêtes en réponses HTTP. */
class StatsQueryGatewayTest {

    private static HttpStatus statut(RuntimeException e) {
        return HttpStatus.valueOf(assertInstanceOf(ResponseStatusException.class, e).getStatusCode().value());
    }

    @Test
    void detailsErreurRequeteGardentLeCodeEtLeMessage() {
        RuntimeException e = StatsQueryGateway.traduire(new QueryExecutionException("x", null,
                new ErreurRequete(404, "Event not found with id x")));
        assertEquals(HttpStatus.NOT_FOUND, statut(e));
        assertEquals("Event not found with id x", ((ResponseStatusException) e).getReason());
    }

    @Test
    void detailsRecusSousFormeDeMap() {
        RuntimeException e = StatsQueryGateway.traduire(new QueryExecutionException("x", null,
                Map.of("status", 400, "message", "Intervalle de dates invalide")));
        assertEquals(HttpStatus.BAD_REQUEST, statut(e));
    }

    @Test
    void sansDetailsUneErreurInterne() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, statut(StatsQueryGateway.traduire(new QueryExecutionException("x", null))));
    }

    @Test
    void aucuneInstanceDonne503() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, statut(StatsQueryGateway.traduire(new NoHandlerForQueryException("aucun"))));
    }

    @Test
    void uneAutreErreurEstPropagee() {
        IllegalStateException autre = new IllegalStateException("autre");
        assertSame(autre, StatsQueryGateway.traduire(autre));
    }
}
