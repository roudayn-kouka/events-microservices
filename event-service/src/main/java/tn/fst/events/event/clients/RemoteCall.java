package tn.fst.events.event.clients;

import feign.FeignException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Traduit les erreurs d'un appel Feign en erreurs HTTP claires pour l'appelant :
 * 404 distant → 404 (« participant 7 introuvable »), indisponibilité → 503.
 * Version phase 2 : à la phase 4, Resilience4j (Retry, CircuitBreaker, TimeLimiter) et des
 * méthodes fallback explicites prennent le relais pour les indisponibilités.
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
