package tn.fst.events.gateway;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Ajouté pour la revue du Security Hotspot java:S3752 (CSRF) : le fallback de la Gateway accepte
 * TOUTES les méthodes HTTP, car les routes y redirigent (forward) les GET comme les POST, PUT et
 * DELETE. Ce test prouve qu'il ne fait que répondre 503 en JSON, à l'identique pour chaque méthode,
 * sans cookie ni session : aucune action à déclencher par CSRF. Hotspot marqué « Safe » (THEORIE Q31).
 */
class IndisponibleControllerTest {

    private final WebTestClient client = WebTestClient.bindToController(new IndisponibleController()).build();

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH"})
    void chaqueMethodeRecoitLeMeme503SansEffetDeBordNiCookie(String methode) {
        client.method(HttpMethod.valueOf(methode)).uri("/indisponible/participant-service")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().doesNotExist("Set-Cookie")
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.message").isEqualTo("participant-service indisponible (Gateway) : aucune réponse");
    }
}
