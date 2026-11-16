package tn.fst.events.statistics.projections;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsLogistique;

/** Table StatsLogistique de db_stats (projection CQRS). */
public interface StatsLogistiqueRepository extends JpaRepository<StatsLogistique, Integer> {
}
