package tn.fst.events.common.resilience;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Panne 4 (erreur réseau) : si la propriété events.clients.&lt;service&gt;.url-forcee est définie
 * (config-overrides + /actuator/refresh), les appels vers ce service partent vers cette adresse au
 * lieu de l'instance trouvée par Eureka. Une adresse fausse provoque une vraie erreur réseau
 * (hôte inconnu) ; supprimer la propriété puis rafraîchir corrige « à chaud », sans redémarrage.
 * La propriété est relue à chaque appel dans l'Environment, que /actuator/refresh met à jour.
 */
public class UrlForceeInterceptor implements RequestInterceptor {

    private final Environment environment;

    public UrlForceeInterceptor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template.feignTarget() == null) {
            return;
        }
        String url = environment.getProperty("events.clients." + template.feignTarget().name() + ".url-forcee");
        if (StringUtils.hasText(url)) {
            template.target(url);
        }
    }
}
