package tn.fst.events.statistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Statistiques des événements — mon module (repris de feature/event-statistics).
 *
 * <p><b>VERSION AVANT CQRS (phase 2).</b> Aucune donnée propre : chaque requête lit les quatre
 * services métier par OpenFeign (voir sources.FeignStatsSource). À la phase 6 (CQRS Axon), la
 * source devient les projections de db_stats ; le calcul (services.EventStatsServiceImpl) ne
 * change pas.
 */
@SpringBootApplication
public class StatisticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatisticsServiceApplication.class, args);
    }
}
