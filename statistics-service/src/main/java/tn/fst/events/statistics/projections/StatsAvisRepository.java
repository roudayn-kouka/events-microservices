package tn.fst.events.statistics.projections;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.fst.events.statistics.clients.FeedbackAverageDTO;
import tn.fst.events.statistics.projections.ProjectionModel.StatsAvis;

/** Table StatsAvis de db_stats (projection CQRS). */
public interface StatsAvisRepository extends JpaRepository<StatsAvis, Integer> {
    /** Moyenne et nombre d'avis de chaque événement noté, calculés par la base locale. */
    @Query("select new tn.fst.events.statistics.clients.FeedbackAverageDTO(a.eventId, avg(a.note), count(a)) "
            + "from StatsAvis a group by a.eventId")
    List<FeedbackAverageDTO> moyennes();
}
