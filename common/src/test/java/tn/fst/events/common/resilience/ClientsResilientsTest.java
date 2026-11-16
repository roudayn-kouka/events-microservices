package tn.fst.events.common.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import feign.codec.DecodeException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Ajouté (phase 4) : la résilience appliquée à un client @FeignClient, avec de vrais registres
 * Resilience4j réglés comme en production (délais raccourcis pour le test).
 */
class ClientsResilientsTest {

    @FeignClient(name = "participant-service")
    interface ClientDeTest {
        @GetMapping("/participants/{id}")
        String lire(@PathVariable("id") int id);

        @PostMapping("/participants")
        String creer();
    }

    /** Faux service distant : son comportement est choisi par chaque test. */
    static class Distant implements ClientDeTest {
        final AtomicInteger appels = new AtomicInteger();
        Supplier<String> reponse = () -> "ok";

        @Override
        public String lire(int id) {
            appels.incrementAndGet();
            return reponse.get();
        }

        @Override
        public String creer() {
            appels.incrementAndGet();
            return reponse.get();
        }
    }

    static final Request REQ = Request.create(Request.HttpMethod.GET, "/participants/1", Map.of(), null,
            StandardCharsets.UTF_8, null);

    CircuitBreakerRegistry circuitBreakers;
    Distant distant;
    ClientDeTest client;

    @BeforeEach
    void setUp() {
        circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(300)).permittedNumberOfCallsInHalfOpenState(1)
                .ignoreExceptions(FeignException.NotFound.class).build());
        RetryRegistry retries = RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(10))
                .ignoreExceptions(FeignException.NotFound.class,
                        io.github.resilience4j.circuitbreaker.CallNotPermittedException.class).build());
        RateLimiterRegistry rateLimiters = RateLimiterRegistry.of(RateLimiterConfig.custom().limitForPeriod(50)
                .limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(Duration.ZERO).build());
        TimeLimiterRegistry timeLimiters = TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(200)).cancelRunningFuture(true).build());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("cb", circuitBreakers);
        beans.addBean("retry", retries);
        beans.addBean("rl", rateLimiters);
        beans.addBean("tl", timeLimiters);
        ClientsResilients resilients = new ClientsResilients(beans.getBeanProvider(CircuitBreakerRegistry.class),
                beans.getBeanProvider(RetryRegistry.class), beans.getBeanProvider(RateLimiterRegistry.class),
                beans.getBeanProvider(TimeLimiterRegistry.class));
        distant = new Distant();
        client = (ClientDeTest) resilients.postProcessAfterInitialization(distant, "clientDeTest");
    }

    private ServiceIndisponibleException repli(Runnable appel) {
        return assertInstanceOf(ServiceIndisponibleException.class, assertThrows(RuntimeException.class, appel::run));
    }

    @Test
    void leClientFeignEstEnveloppeEtUnBeanOrdinaireNon() {
        assertNotSame(distant, client);
        Object ordinaire = new Object();
        assertSame(ordinaire, new ClientsResilients(null, null, null, null).postProcessAfterInitialization(ordinaire, "x"));
        assertTrue(client.toString().contains("Distant")); // méthodes d'Object non décorées
    }

    @Test
    void unAppelReussiPasseTelQuel() {
        assertEquals("ok", client.lire(1));
        assertEquals(1, distant.appels.get());
    }

    @Test
    void uneLectureEnErreurReseauEstRetenteeTroisFoisPuisFallback() {
        distant.reponse = () -> {
            throw new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, REQ);
        };
        ServiceIndisponibleException e = repli(() -> client.lire(1));
        assertEquals(3, distant.appels.get());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), e.getStatusCode().value());
        assertEquals("participant-service : injoignable (erreur réseau) après 3 tentatives", e.getReason());
    }

    @Test
    void uneEcritureNEstJamaisRetentee() {
        distant.reponse = () -> {
            throw new RetryableException(-1, "Connection refused", Request.HttpMethod.POST, (Long) null, REQ);
        };
        ServiceIndisponibleException e = repli(() -> client.creer());
        assertEquals(1, distant.appels.get()); // un POST qui a peut-être abouti ne doit pas être rejoué
        assertEquals("participant-service : injoignable (erreur réseau)", e.getReason());
    }

    @Test
    void unNotFoundMetierNEstNiRetenteNiCompteCommeUnePanne() {
        distant.reponse = () -> {
            throw new FeignException.NotFound("404", REQ, null, Map.of());
        };
        for (int i = 0; i < 6; i++) {
            assertThrows(FeignException.NotFound.class, () -> client.lire(1));
        }
        assertEquals(6, distant.appels.get());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakers.circuitBreaker("participant-service").getState());
    }

    @Test
    void apresPlusieursEchecsLeCircuitSOuvrePuisSeReferme() throws InterruptedException {
        distant.reponse = () -> {
            throw new FeignException.InternalServerError("500", REQ, null, Map.of());
        };
        assertEquals("participant-service : en erreur (HTTP 500) après 3 tentatives", repli(() -> client.lire(1)).getReason());
        repli(() -> client.lire(1)); // 6 échecs ≥ 4 appels minimum, taux 100 % → OPEN
        CircuitBreaker cb = circuitBreakers.circuitBreaker("participant-service");
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        int avant = distant.appels.get();
        ServiceIndisponibleException ouvert = repli(() -> client.lire(1));
        assertEquals(avant, distant.appels.get()); // appel non tenté
        assertEquals("participant-service : circuit ouvert, service considéré en panne (appel non tenté)", ouvert.getReason());

        Thread.sleep(350);                  // fin de l'attente → HALF_OPEN au prochain appel
        distant.reponse = () -> "rétabli";
        assertEquals("rétabli", client.lire(1));
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void unServiceTropLentEstAbandonneParLeTimeLimiter() {
        distant.reponse = () -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "trop tard";
        };
        long debut = System.currentTimeMillis();
        ServiceIndisponibleException e = repli(() -> client.creer());
        assertTrue(System.currentTimeMillis() - debut < 1_500);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT.value(), e.getStatusCode().value());
        assertEquals("participant-service : pas de réponse en 200 ms (TimeLimiter)", e.getReason());
    }

    @Test
    void uneReponseIncorrecteEstRejetee() {
        distant.reponse = () -> {
            throw new DecodeException(200, "JSON tronqué", REQ);
        };
        ServiceIndisponibleException e = repli(() -> client.creer());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), e.getStatusCode().value());
        assertEquals("participant-service : réponse incorrecte, rejetée", e.getReason());
    }

    @Test
    void auDelaDeLaLimiteLeRateLimiterRefuseAvecUn429() {
        RateLimiterRegistry strict = RateLimiterRegistry.of(RateLimiterConfig.custom().limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(10)).timeoutDuration(Duration.ZERO).build());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("cb", circuitBreakers);
        beans.addBean("retry", RetryRegistry.ofDefaults());
        beans.addBean("rl", strict);
        beans.addBean("tl", TimeLimiterRegistry.ofDefaults());
        ClientDeTest limite = (ClientDeTest) new ClientsResilients(beans.getBeanProvider(CircuitBreakerRegistry.class),
                beans.getBeanProvider(RetryRegistry.class), beans.getBeanProvider(RateLimiterRegistry.class),
                beans.getBeanProvider(TimeLimiterRegistry.class)).postProcessAfterInitialization(new Distant(), "c");
        limite.creer();
        limite.creer();
        ServiceIndisponibleException e = repli(limite::creer);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), e.getStatusCode().value());
        assertEquals("participant-service : trop d'appels (RateLimiter : 2 par 10000 ms)", e.getReason());
    }
}
