package tn.fst.events.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Teste la VRAIE configuration de la Gateway (config-repo/application.yml et api-gateway.yml,
 * importées par src/test/resources/application.yml), sans Config Server ni Eureka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "server.port=0"
})
class ApiGatewayRoutesTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RouteLocator routes;

    private WebTestClient client;

    /** Vraies requêtes HTTP sur le port du serveur : le contrôle CORS a besoin de l'hôte réel. */
    @BeforeEach
    void client() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void declareUneRouteLbParMicroservice() {
        var uris = routes.getRoutes().map(r -> r.getId() + "=" + r.getUri()).collectList().block();
        assertThat(uris).contains(
                "event-service=lb://event-service",
                "participant-service=lb://participant-service",
                "logistics-service=lb://logistics-service",
                "feedback-service=lb://feedback-service",
                "statistics-service=lb://statistics-service");
    }

    @Test
    void repond503QuandAucuneInstanceNEstEnregistree() {
        client.get().uri("/api/participants").exchange().expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("participant-service indisponible (Gateway)"));
    }

    @Test
    void autoriseLeFrontendAngularEnCors() {
        client.method(HttpMethod.OPTIONS).uri("/api/participants")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200");
    }

    @Test
    void refuseUneOrigineInconnueEnCors() {
        client.method(HttpMethod.OPTIONS).uri("/api/participants")
                .header(HttpHeaders.ORIGIN, "http://pirate.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }
}
