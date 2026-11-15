package tn.fst.events.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Le serveur Eureka démarre avec sa VRAIE configuration (config-repo/application.yml et
 * eureka-server.yml, importées par src/test/resources/application.yml, sans Config Server)
 * et expose son registre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.port=0")
class EurekaServerApplicationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void exposeLeRegistreDesApplications() {
        String apps = rest.getForObject("/eureka/apps", String.class);
        assertThat(apps).contains("applications");
    }

    @Test
    void exposeSaSante() {
        assertThat(rest.getForObject("/actuator/health", String.class)).contains("UP");
    }
}
