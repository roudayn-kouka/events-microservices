package tn.fst.events.statistics.projections;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsEvenement;

/** Table StatsEvenement de db_stats (projection CQRS). */
public interface StatsEvenementRepository extends JpaRepository<StatsEvenement, String> {
}
