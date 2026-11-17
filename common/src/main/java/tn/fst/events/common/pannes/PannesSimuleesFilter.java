package tn.fst.events.common.pannes;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PANNES SIMULÉES (phase 4), pilotées à chaud par le Config Server : une propriété posée dans
 * config-overrides/&lt;service&gt;.yml puis POST /actuator/refresh sur le service, sans redémarrage.
 * <ul>
 *   <li>panne 2 : panne.latence-ms = N → chaque réponse est retardée de N ms ;</li>
 *   <li>panne 3 : panne.reponse-corrompue = true → réponse 200 au JSON tronqué (illisible).</li>
 * </ul>
 * /actuator/** n'est jamais touché (santé, rafraîchissement, métriques). Valeurs relues à chaque
 * requête dans l'Environment, que /actuator/refresh met à jour.
 */
public class PannesSimuleesFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PannesSimuleesFilter.class);

    public static final String LATENCE = "panne.latence-ms";
    public static final String CORROMPUE = "panne.reponse-corrompue";

    private final Environment environment;

    public PannesSimuleesFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long latence = environment.getProperty(LATENCE, Long.class, 0L);
        if (latence > 0) {
            log.warn("Panne simulée : latence de {} ms sur {} {}", latence, request.getMethod(), request.getRequestURI());
            try {
                Thread.sleep(latence);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (environment.getProperty(CORROMPUE, Boolean.class, false)) {
            log.warn("Panne simulée : réponse corrompue sur {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write("{\"idPart\": 1, \"nom\": \"Tou".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
