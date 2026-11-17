package tn.fst.events.feedback.clients;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Fabrique d'erreurs Feign pour simuler un service distant (404, panne) dans les tests. */
public final class FeignErreurs {

    private FeignErreurs() {
    }

    private static Request requete(String chemin) {
        return Request.create(Request.HttpMethod.GET, chemin, Map.of(), null, StandardCharsets.UTF_8, null);
    }

    public static FeignException.NotFound introuvable(String chemin) {
        return new FeignException.NotFound("404 Not Found", requete(chemin), null, Map.of());
    }

    public static FeignException.InternalServerError erreurServeur(String chemin) {
        return new FeignException.InternalServerError("500 Internal Server Error", requete(chemin), null, Map.of());
    }

    public static RetryableException injoignable(String chemin) {
        return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, requete(chemin));
    }
}
