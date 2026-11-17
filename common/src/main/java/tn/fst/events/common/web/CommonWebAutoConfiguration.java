package tn.fst.events.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Chargée automatiquement par chaque service métier (fichier AutoConfiguration.imports) :
 * aucun service n'a besoin de scanner le paquet tn.fst.events.common.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Pannes 2 et 3 simulées à chaud (latence, réponse corrompue) : sans effet tant que rien n'est configuré. */
    @Bean
    public tn.fst.events.common.pannes.PannesSimuleesFilter pannesSimuleesFilter(
            org.springframework.core.env.Environment environment) {
        return new tn.fst.events.common.pannes.PannesSimuleesFilter(environment);
    }

    @Bean
    public InstanceIdFilter instanceIdFilter(
            @Value("${eureka.instance.instance-id:${spring.application.name:application}}") String instanceId) {
        return new InstanceIdFilter(instanceId);
    }
}
