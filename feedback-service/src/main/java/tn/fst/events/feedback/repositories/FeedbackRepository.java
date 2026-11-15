package tn.fst.events.feedback.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.fst.events.feedback.dto.FeedbackAverageResponse;
import tn.fst.events.feedback.entities.Feedback;

/**
 * Repris de feature/feedback : les chemins de relation (Event_IdEvent, Participant_IdPart)
 * deviennent des colonnes simples (EventId, ParticipantId).
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Integer> {

    List<Feedback> findByEventIdOrderByDateAscIdAsc(String eventId);

    boolean existsByEventIdAndParticipantId(String eventId, Integer participantId);

    long countByEventId(String eventId);

    @Query("select coalesce(avg(f.note), 0.0) from Feedback f where f.eventId = :eventId")
    double averageRatingByEvent(@Param("eventId") String eventId);

    /** Nouveau, pour statistics-service : moyenne et nombre d'avis de chaque événement noté. */
    @Query("select new tn.fst.events.feedback.dto.FeedbackAverageResponse(f.eventId, avg(f.note), count(f)) "
            + "from Feedback f group by f.eventId order by f.eventId")
    List<FeedbackAverageResponse> averageRatings();
}
