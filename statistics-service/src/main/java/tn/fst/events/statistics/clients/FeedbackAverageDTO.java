package tn.fst.events.statistics.clients;

/** Moyenne des avis d'un événement, telle que la renvoie feedback-service (GET /feedback/moyennes). */
public record FeedbackAverageDTO(String eventId, double averageRating, long totalFeedbacks) {
}
