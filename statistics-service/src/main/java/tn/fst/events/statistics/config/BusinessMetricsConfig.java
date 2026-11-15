package tn.fst.events.statistics.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.function.ToDoubleFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.fst.events.statistics.clients.LogisticsDTO;
import tn.fst.events.statistics.sources.StatsSource;

/**
 * Métriques métier exposées sur /actuator/prometheus (tableau de bord Grafana, phase 7).
 * Repris de feature/event-statistics : mêmes noms de jauges. Les valeurs viennent de la
 * {@link StatsSource} au lieu des repositories. AVANT CQRS, chaque lecture d'une jauge appelle un
 * service ; s'il est en panne, la jauge vaut NaN (trou dans la courbe) au lieu de faire échouer
 * l'exposition de toutes les métriques.
 */
@Slf4j
@Configuration
public class BusinessMetricsConfig {

    @Bean
    public MeterBinder businessMetrics(StatsSource source) {
        return registry -> {
            Gauge.builder("fst.events.count", source, sansPanne(s -> s.evenements().size()))
                    .description("Nombre d'événements")
                    .register(registry);
            Gauge.builder("fst.participants.count", source, sansPanne(s -> s.participants().size()))
                    .description("Nombre de participants")
                    .register(registry);
            Gauge.builder("fst.logistics.reserved.cost", source, sansPanne(s -> {
                        double total = 0;
                        for (LogisticsDTO l : s.logistique()) {
                            if (l.reserve()) {
                                total += l.prixUnit() * l.quantite();
                            }
                        }
                        return total;
                    }))
                    .description("Coût total de la logistique réservée (DT)")
                    .register(registry);
        };
    }

    private static ToDoubleFunction<StatsSource> sansPanne(ToDoubleFunction<StatsSource> lecture) {
        return s -> {
            try {
                return lecture.applyAsDouble(s);
            } catch (RuntimeException e) {
                log.debug("Métrique métier indisponible : {}", e.getMessage());
                return Double.NaN;
            }
        };
    }
}
