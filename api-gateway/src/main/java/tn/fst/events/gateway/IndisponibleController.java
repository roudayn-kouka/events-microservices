package tn.fst.events.gateway;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * FALLBACK de la Gateway (phase 4) : appelé par le filtre CircuitBreaker d'une route quand le
 * service ne répond pas (instance arrêtée, connexion refusée, délai dépassé, circuit ouvert).
 * Réponse 503 au même format JSON que les services (ApiError), avec la cause.
 */
@RestController
public class IndisponibleController {

    @RequestMapping("/indisponible/{service}")
    public ResponseEntity<Map<String, Object>> indisponible(@PathVariable("service") String service,
                                                            ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("timestamp", Instant.now().toString());
        corps.put("status", 503);
        corps.put("error", "Service Unavailable");
        corps.put("message", service + " indisponible (Gateway) : " + raison(cause));
        corps.put("path", exchange.getRequest().getPath().value());
        corps.put("details", List.of());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(corps);
    }

    private static String raison(Throwable cause) {
        if (cause == null) {
            return "aucune réponse";
        }
        String nom = cause.getClass().getSimpleName();
        if (nom.contains("CallNotPermitted")) {
            return "circuit ouvert";
        }
        if (nom.contains("Timeout")) {
            return "délai dépassé";
        }
        if (nom.contains("NotFound") || String.valueOf(cause.getMessage()).contains("Unable to find instance")) {
            return "aucune instance enregistrée dans Eureka";
        }
        return "connexion impossible (" + nom + ")";
    }
}
