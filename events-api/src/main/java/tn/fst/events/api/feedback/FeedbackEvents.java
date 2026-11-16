package tn.fst.events.api.feedback;

import java.time.Instant;

/** Publiés par feedback-service après l'enregistrement ou la suppression d'un avis. */
public final class FeedbackEvents {

    private FeedbackEvents() {
    }

    public record FeedbackSubmitted(int id, String eventId, int participantId, int note, Instant date) {
    }

    public record FeedbackDeleted(int id, String eventId) {
    }
}
