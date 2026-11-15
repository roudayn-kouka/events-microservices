package tn.fst.events.feedback.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import tn.fst.events.feedback.entities.Feedback;

/** Repris de feature/feedback ; eventId devient l'UUID de l'événement. */
@Getter
@AllArgsConstructor
public class FeedbackResponse {
    private final Integer id;
    private final Integer note;
    private final String commentaire;
    private final Instant date;
    private final String eventId;
    private final int participantId;

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getNote(), feedback.getCommentaire(),
                feedback.getDate(), feedback.getEventId(), feedback.getParticipantId());
    }
}
