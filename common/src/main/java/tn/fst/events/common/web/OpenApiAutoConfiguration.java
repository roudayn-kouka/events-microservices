package tn.fst.events.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Phase 7 : description OpenAPI commune des services (springdoc). Le seul serveur annoncé est
 * « /api », relatif à la page Swagger : depuis le Swagger agrégé de la Gateway
 * (http://localhost:8088/swagger-ui.html), « Try it out » appelle /api/participants… via la Gateway,
 * comme le frontend et Postman — jamais l'adresse interne du conteneur.
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openApiEvents(@Value("${spring.application.name:service}") String application) {
        return new OpenAPI()
                .info(new Info().title(application).version("1.0.0")
                        .description("Events FST — " + application + " (microservices Spring Cloud), appelé via l'API Gateway"))
                .servers(List.of(new Server().url("/api").description("API Gateway")));
    }
}
