package tn.fst.events.common.resilience;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;

/**
 * Phase 6 : Feign (feign-micrometer) et le client HTTP de Spring (appels vers Eureka) publient
 * tous deux « http.client.requests », avec des étiquettes différentes. Prometheus exige les mêmes
 * étiquettes pour un même nom : les mesures Feign étaient rejetées. Elles sont renommées
 * « feign.client.requests » (les traces Zipkin ne sont pas concernées).
 */
@AutoConfiguration
@ConditionalOnClass({FeignClient.class, MeterFilter.class})
public class FeignMetriquesAutoConfiguration {

    static final String SPRING = "http.client.requests";
    static final String FEIGN = "feign.client.requests";

    @Bean
    MeterFilter feignClientRequests() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (id.getName().startsWith(SPRING) && id.getTag("clientName") != null) {
                    return id.withName(FEIGN + id.getName().substring(SPRING.length()));
                }
                return id;
            }
        };
    }
}
