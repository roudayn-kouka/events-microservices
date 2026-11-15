package tn.fst.events.event.clients;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Fabrique d'erreurs Feign pour simuler un service distant (404, panne) dans les tests unitaires. */
public final class FeignErreurs {

    private FeignErreurs() {
    }

    private static Request requete(String chemin) {
        return Request.create(Request.HttpMethod.GET, chemin, Map.of(), null, StandardCharsets.UTF_8, null);
    }

    /** Le service distant a répondu 404. */
    public static FeignException.NotFound introuvable(String chemin) {
        return new FeignException.NotFound("404 Not Found", requete(chemin), null, Map.of());
    }

    /** Le service distant a répondu 500. */
    public static FeignException.InternalServerError erreurServeur(String chemin) {
        return new FeignException.InternalServerError("500 Internal Server Error", requete(chemin), null, Map.of());
    }

    /** Aucune réponse (connexion refusée, délai dépassé) : status() vaut -1. */
    public static RetryableException injoignable(String chemin) {
        return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, requete(chemin));
    }
}
