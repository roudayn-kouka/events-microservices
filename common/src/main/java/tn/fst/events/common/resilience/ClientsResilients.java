package tn.fst.events.common.resilience;

import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * RÉSILIENCE (phase 4) de TOUS les appels OpenFeign, sans toucher au code métier : chaque client
 * @FeignClient est enveloppé, et chaque appel passe dans cet ordre par
 * Retry( RateLimiter( CircuitBreaker( TimeLimiter( appel ) ) ) ),
 * avec des instances nommées comme le service appelé (« participant-service »…), réglées dans
 * config-repo/application.yml (Retry 3 × 500 ms, RateLimiter 5/s, CircuitBreaker, TimeLimiter 5 s).
 *
 * <ul>
 *   <li>Le Retry ne s'applique qu'aux lectures (@GetMapping) : rejouer un POST qui a peut-être
 *       abouti créerait un doublon (par exemple deux logistiques).</li>
 *   <li>Une erreur métier (404, 400, 409) n'est ni retentée ni comptée comme une panne
 *       (ignore-exceptions) : elle remonte telle quelle (RemoteCall la traduit).</li>
 *   <li>Toute autre erreur déclenche le FALLBACK : une ServiceIndisponibleException qui nomme la
 *       cause (circuit ouvert, délai, trop d'appels, réponse incorrecte, réseau).</li>
 *   <li>ISOLATION PAR CAS D'USAGE (bulkhead) : un traitement de fond (cron) appelle à l'intérieur de
 *       {@link #enTraitementDeFond(Supplier)}. Ses appels passent par des instances distinctes,
 *       « &lt;service&gt;-batch » (CircuitBreaker et RateLimiter), sans Retry : ses échecs n'ouvrent
 *       jamais le circuit des requêtes des utilisateurs, et il ne consomme pas leur quota d'appels.</li>
 * </ul>
 */
public class ClientsResilients implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ClientsResilients.class);

    /** Suffixe des instances Resilience4j réservées aux traitements de fond. */
    public static final String SUFFIXE_FOND = "-batch";

    /** Vrai pendant un traitement de fond, dans le thread appelant (lu avant le TimeLimiter). */
    private static final ThreadLocal<Boolean> FOND = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Exécute un traitement de fond (cron, batch) : les appels Feign qu'il fait utilisent le circuit
     * « &lt;service&gt;-batch », sans Retry, et non celui des utilisateurs.
     */
    public static <T> T enTraitementDeFond(Supplier<T> traitement) {
        boolean avant = FOND.get();
        FOND.set(Boolean.TRUE);
        try {
            return traitement.get();
        } finally {
            if (avant) {
                FOND.set(Boolean.TRUE);
            } else {
                FOND.remove();
            }
        }
    }

    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakers;
    private final ObjectProvider<RetryRegistry> retries;
    private final ObjectProvider<RateLimiterRegistry> rateLimiters;
    private final ObjectProvider<TimeLimiterRegistry> timeLimiters;
    /**
     * Threads du TimeLimiter : l'appel s'exécute à part pour pouvoir l'abandonner au bout du délai.
     * Phase 6 : si context-propagation est présent (Micrometer Tracing), le contexte de l'appelant
     * (trace en cours) est recopié dans ce thread ; sinon la trace Zipkin serait coupée avant Feign.
     */
    private final ExecutorService executor = avecContexte(Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "feign-timelimiter");
        t.setDaemon(true);
        return t;
    }));

    private static ExecutorService avecContexte(ExecutorService executor) {
        if (!org.springframework.util.ClassUtils.isPresent("io.micrometer.context.ContextExecutorService",
                ClientsResilients.class.getClassLoader())) {
            return executor;
        }
        return Propagation.envelopper(executor);
    }

    /** Isolé dans une classe à part : chargé seulement si context-propagation est sur le classpath. */
    private static final class Propagation {
        static ExecutorService envelopper(ExecutorService executor) {
            io.micrometer.context.ContextSnapshotFactory instantanes = io.micrometer.context.ContextSnapshotFactory.builder().build();
            return io.micrometer.context.ContextExecutorService.wrap(executor, instantanes::captureAll);
        }
    }

    public ClientsResilients(ObjectProvider<CircuitBreakerRegistry> circuitBreakers, ObjectProvider<RetryRegistry> retries,
                             ObjectProvider<RateLimiterRegistry> rateLimiters, ObjectProvider<TimeLimiterRegistry> timeLimiters) {
        this.circuitBreakers = circuitBreakers;
        this.retries = retries;
        this.rateLimiters = rateLimiters;
        this.timeLimiters = timeLimiters;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        for (Class<?> interfaceClient : ClassUtils.getAllInterfacesForClassAsSet(bean.getClass())) {
            FeignClient feign = AnnotatedElementUtils.findMergedAnnotation(interfaceClient, FeignClient.class);
            if (feign != null) {
                String service = StringUtils.hasText(feign.name()) ? feign.name() : feign.value();
                log.info("Client Feign {} ({}) : Retry, RateLimiter, CircuitBreaker et TimeLimiter « {} »",
                        interfaceClient.getSimpleName(), beanName, service);
                return Proxy.newProxyInstance(interfaceClient.getClassLoader(), new Class<?>[] {interfaceClient},
                        new Appel(bean, service));
            }
        }
        return bean;
    }

    /** Applique les quatre protections à chaque méthode du client (sauf celles d'Object). */
    private final class Appel implements InvocationHandler {
        private final Object cible;
        private final String service;

        Appel(Object cible, String service) {
            this.cible = cible;
            this.service = service;
        }

        @Override
        public Object invoke(Object proxy, Method methode, Object[] args) throws Throwable {
            if (methode.getDeclaringClass() == Object.class) {
                return invoquer(methode, args);
            }
            boolean lecture = AnnotatedElementUtils.hasAnnotation(methode, GetMapping.class);
            return executer(service, lecture, () -> {
                try {
                    return invoquer(methode, args);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        private Object invoquer(Method methode, Object[] args) throws Throwable {
            try {
                return methode.invoke(cible, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /** Point d'entrée commun (aussi testé directement). */
    <T> T executer(String service, boolean lecture, Supplier<T> appel) {
        boolean fond = FOND.get();
        String instance = fond ? service + SUFFIXE_FOND : service;
        CircuitBreaker cb = circuitBreakers.getObject().circuitBreaker(instance);
        RateLimiter rl = rateLimiters.getObject().rateLimiter(instance);
        TimeLimiter tl = timeLimiters.getObject().timeLimiter(service);
        Retry retry = retries.getObject().retry(service);
        boolean retente = lecture && !fond; // un batch réessaiera à son prochain passage

        Supplier<T> protege = () -> limiterLeTemps(tl, appel);
        protege = CircuitBreaker.decorateSupplier(cb, protege);
        protege = RateLimiter.decorateSupplier(rl, protege);
        if (retente) {
            protege = Retry.decorateSupplier(retry, protege);
        }
        try {
            return protege.get();
        } catch (RuntimeException e) {
            throw repli(instance, retente, e, retry, rl, tl);
        }
    }

    private <T> T limiterLeTemps(TimeLimiter tl, Supplier<T> appel) {
        try {
            return tl.executeFutureSupplier(() -> CompletableFuture.supplyAsync(appel, executor));
        } catch (TimeoutException e) {
            throw new DelaiDepasseException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException r ? r : new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** FALLBACK explicite : la cause est nommée ; une erreur métier remonte telle quelle. */
    static RuntimeException repli(String service, boolean lecture, RuntimeException e, Retry retry, RateLimiter rl,
                                  TimeLimiter tl) {
        Throwable cause = e instanceof DelaiDepasseException ? e : racine(e);
        String essais = lecture ? " après " + retry.getRetryConfig().getMaxAttempts() + " tentatives" : "";
        if (cause instanceof FeignException f && f.status() >= 400 && f.status() < 500 && f.status() != 429) {
            return e; // erreur métier (404, 400, 409) : pas une panne
        }
        ServiceIndisponibleException repli;
        if (cause instanceof CallNotPermittedException) {
            repli = new ServiceIndisponibleException(HttpStatus.SERVICE_UNAVAILABLE, service,
                    "circuit ouvert, service considéré en panne (appel non tenté)", e);
        } else if (cause instanceof RequestNotPermitted) {
            repli = new ServiceIndisponibleException(HttpStatus.TOO_MANY_REQUESTS, service,
                    "trop d'appels (RateLimiter : " + rl.getRateLimiterConfig().getLimitForPeriod() + " par "
                            + rl.getRateLimiterConfig().getLimitRefreshPeriod().toMillis() + " ms)", e);
        } else if (cause instanceof DelaiDepasseException) {
            repli = new ServiceIndisponibleException(HttpStatus.GATEWAY_TIMEOUT, service,
                    "pas de réponse en " + tl.getTimeLimiterConfig().getTimeoutDuration().toMillis() + " ms (TimeLimiter)" + essais, e);
        } else if (cause instanceof DecodeException) {
            repli = new ServiceIndisponibleException(HttpStatus.BAD_GATEWAY, service,
                    "réponse incorrecte, rejetée" + essais, e);
        } else if (cause instanceof RetryableException || cause instanceof IOException) {
            repli = new ServiceIndisponibleException(HttpStatus.SERVICE_UNAVAILABLE, service,
                    "injoignable (erreur réseau)" + essais, e);
        } else if (cause instanceof FeignException f) {
            repli = new ServiceIndisponibleException(HttpStatus.SERVICE_UNAVAILABLE, service,
                    "en erreur (HTTP " + f.status() + ")" + essais, e);
        } else {
            return e;
        }
        log.warn("Fallback {} : {}", service, repli.getRaison());
        return repli;
    }

    private static Throwable racine(Throwable e) {
        Throwable t = e;
        while (!(t instanceof FeignException) && !(t instanceof CallNotPermittedException)
                && !(t instanceof RequestNotPermitted) && t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t instanceof FeignException || t instanceof CallNotPermittedException || t instanceof RequestNotPermitted
                ? t : e;
    }

    /** Délai du TimeLimiter dépassé (non vérifiée, pour traverser les décorateurs). */
    static class DelaiDepasseException extends RuntimeException {
        DelaiDepasseException(Throwable cause) {
            super("délai dépassé", cause);
        }
    }
}
