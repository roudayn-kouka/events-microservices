package tn.fst.events.api.feedback;

import java.time.Instant;

/** Publiés par feedback-service après l'enregistrement ou la suppression d'un avis. */
public final class FeedbackEvents {

    private FeedbackEvents() {
    }

    public record FeedbackSubmitted(Integer id, String eventId, Integer participantId, Integer note, Instant date) {
    }

    public record FeedbackDeleted(Integer id, String eventId) {
    }
}
