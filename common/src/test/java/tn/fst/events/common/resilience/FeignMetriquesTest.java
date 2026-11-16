package tn.fst.events.common.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/** Ajouté (phase 6) : les mesures Feign sont renommées, celles du client HTTP de Spring non. */
class FeignMetriquesTest {

    @Test
    void lesMesuresFeignSontRenommeesEtLesAutresGardees() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new FeignMetriquesAutoConfiguration().feignClientRequests());

        Timer.builder("http.client.requests").tag("clientName", "ParticipantClient").register(registry);
        Timer.builder("http.client.requests").tag("uri", "/eureka/apps/").register(registry);
        Timer.builder("http.client.requests.active").tag("clientName", "ParticipantClient").register(registry);

        assertNotNull(registry.find("feign.client.requests").tag("clientName", "ParticipantClient").timer());
        assertNotNull(registry.find("feign.client.requests.active").timer());
        assertEquals(1, registry.find("http.client.requests").timers().size()); // celle de Spring (Eureka)
    }
}
