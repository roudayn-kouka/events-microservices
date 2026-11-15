package tn.fst.events.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ajoute l'en-tête X-Instance-Id à chaque réponse : l'identifiant Eureka de l'instance qui a
 * traité la requête (ex. participant-service:participant-service-2). Preuve de l'équilibrage
 * de charge entre plusieurs instances d'un même service.
 */
public class InstanceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Instance-Id";

    private final String instanceId;

    public InstanceIdFilter(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, instanceId);
        chain.doFilter(request, response);
    }
}
