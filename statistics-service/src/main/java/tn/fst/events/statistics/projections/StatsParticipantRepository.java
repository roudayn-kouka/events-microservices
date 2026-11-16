package tn.fst.events.statistics.projections;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsParticipant;

/** Table StatsParticipant de db_stats (projection CQRS). */
public interface StatsParticipantRepository extends JpaRepository<StatsParticipant, Integer> {
}
