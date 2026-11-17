package tn.fst.events.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Le Config Server sert la configuration d'un service, et la surcharge (1re source du profil
 * « composite », comme en production) l'emporte sur la configuration de référence.
 * Les deux sources sont ici des dossiers du classpath pour ne pas dépendre de Git.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=composite",
        "spring.cloud.config.server.composite[0].type=native",
        "spring.cloud.config.server.composite[0].search-locations=classpath:/test-overrides",
        "spring.cloud.config.server.composite[1].type=native",
        "spring.cloud.config.server.composite[1].search-locations=classpath:/test-config",
        "eureka.client.enabled=false"
})
class ConfigServerApplicationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void sertLaConfigurationDUnServiceAvecSurchargePrioritaire() {
        Map<String, Object> env = rest.getForObject("/demo-service/default", Map.class);

        assertThat(env).containsEntry("name", "demo-service");
        var sources = (java.util.List<Map<String, Object>>) env.get("propertySources");
        assertThat(sources).hasSize(2);
        // La première source (la plus prioritaire) est la surcharge
        assertThat(sources.get(0).get("name").toString()).contains("test-overrides");
        assertThat((Map<String, Object>) sources.get(0).get("source")).containsEntry("demo.faults.latency-ms", 6000);
        assertThat((Map<String, Object>) sources.get(1).get("source")).containsEntry("demo.faults.latency-ms", 0);
    }

    @Test
    void exposeSaSante() {
        assertThat(rest.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    }
}
