package tn.fst.events.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Repris de feature/feedback ; eventId devient l'UUID de l'événement. */
@Getter
@AllArgsConstructor
public class FeedbackAverageResponse {
    private final String eventId;
    private final double averageRating;
    private final long totalFeedbacks;
}
