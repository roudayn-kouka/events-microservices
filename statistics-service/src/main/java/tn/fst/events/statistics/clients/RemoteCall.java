package tn.fst.events.statistics.clients;

import feign.FeignException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Traduit les erreurs d'un appel Feign : 404 distant → 404 avec le message fourni, panne → 503.
 * AVANT CQRS, une panne de l'un des quatre services rend donc les statistiques indisponibles :
 * c'est le couplage que la phase 3 (CQRS) supprime.
 */
public final class RemoteCall {

    private RemoteCall() {
    }

    public static <T> T call(String service, String introuvable, Supplier<T> appel) {
        try {
            return appel.get();
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, introuvable);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    service + " indisponible (" + (e.status() > 0 ? "HTTP " + e.status() : "pas de réponse") + ")");
        }
    }
}
