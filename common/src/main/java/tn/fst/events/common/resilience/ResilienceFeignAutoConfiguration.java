package tn.fst.events.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Chargée seulement dans les services qui ont OpenFeign ET Resilience4j (event, feedback,
 * statistics). events.resilience.enabled=false la désactive (tests unitaires des règles métier).
 */
@AutoConfiguration
@ConditionalOnClass({FeignClient.class, CircuitBreakerRegistry.class})
@ConditionalOnProperty(name = "events.resilience.enabled", havingValue = "true", matchIfMissing = true)
public class ResilienceFeignAutoConfiguration {

    /** static : un BeanPostProcessor doit exister avant les beans qu'il transforme. */
    @Bean
    static ClientsResilients clientsResilients(ObjectProvider<CircuitBreakerRegistry> circuitBreakers,
                                               ObjectProvider<RetryRegistry> retries,
                                               ObjectProvider<RateLimiterRegistry> rateLimiters,
                                               ObjectProvider<TimeLimiterRegistry> timeLimiters) {
        return new ClientsResilients(circuitBreakers, retries, rateLimiters, timeLimiters);
    }

    @Bean
    UrlForceeInterceptor urlForceeInterceptor(Environment environment) {
        return new UrlForceeInterceptor(environment);
    }
}
