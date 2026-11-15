package tn.fst.events.feedback.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import tn.fst.events.feedback.dto.FeedbackAverageResponse;
import tn.fst.events.feedback.entities.Feedback;

/**
 * Repris de feature/feedback (6 tests, H2), tous adaptés : l'avis référence l'événement (UUID)
 * et le participant par identifiant ; il n'y a plus d'Event ni de Participant à persister
 * (ils sont dans d'autres bases). 1 test ajouté : moyennes groupées par événement.
 */
@DataJpaTest(showSql = false)
class FeedbackRepositoryTest {

    static final String EV = "11111111-1111-1111-1111-111111111111";
    static final String EV2 = "22222222-2222-2222-2222-222222222222";

    @Autowired FeedbackRepository repository;
    @Autowired EntityManager entityManager;

    private Feedback review(String eventId, Integer participantId, int note) {
        Feedback feedback = new Feedback();
        feedback.setEventId(eventId);
        feedback.setParticipantId(participantId);
        feedback.setNote(note);
        feedback.setCommentaire("Useful event");
        return repository.saveAndFlush(feedback);
    }

    @Test
    void persistsRelationshipsTimestampAndCalculatesAverage() {
        Feedback feedback = review(EV, 1, 4);
        review(EV, 2, 5);
        entityManager.clear();
        Feedback loaded = repository.findById(feedback.getId()).orElseThrow();
        assertNotNull(loaded.getDate());
        assertEquals("Useful event", loaded.getCommentaire());
        assertEquals(EV, loaded.getEventId());
        assertEquals(1, loaded.getParticipantId());
        assertEquals(2, repository.findByEventIdOrderByDateAscIdAsc(EV).size());
        assertEquals(2, repository.countByEventId(EV));
        assertTrue(repository.existsByEventIdAndParticipantId(EV, 1));
        assertEquals(4.5, repository.averageRatingByEvent(EV));
    }

    @Test
    void emptyEventHasZeroAverage() {
        assertEquals(0.0, repository.averageRatingByEvent("inconnu"));
        assertTrue(repository.findByEventIdOrderByDateAscIdAsc("inconnu").isEmpty());
        assertFalse(repository.existsByEventIdAndParticipantId("inconnu", -1));
    }

    @Test
    void databaseRejectsDuplicateEventParticipantPair() {
        review(EV, 1, 3);
        assertThrows(DataIntegrityViolationException.class, () -> review(EV, 1, 5));
    }

    @Test
    void sameParticipantCanReviewMultipleEventsAndMultipleParticipantsCanReviewOneEvent() {
        review(EV, 1, 1);
        review(EV2, 1, 5);
        review(EV, 2, 4);
        assertEquals(2.5, repository.averageRatingByEvent(EV));
        assertEquals(5.0, repository.averageRatingByEvent(EV2));
    }

    @Test
    void databaseRejectsMissingRequiredRating() {
        Feedback feedback = new Feedback();
        feedback.setEventId(EV);
        feedback.setParticipantId(1);
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(feedback));
    }

    @Test
    void databaseRejectsMissingRequiredRelationship() {
        Feedback feedback = new Feedback();
        feedback.setNote(5);
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(feedback));
    }

    // ---------------------------------------------------------------- ajouté

    @Test
    void averageRatingsRegroupeParEvenement() {
        review(EV, 1, 4);
        review(EV, 2, 5);
        review(EV2, 1, 2);

        List<FeedbackAverageResponse> moyennes = repository.averageRatings();

        assertEquals(2, moyennes.size());
        assertEquals(EV, moyennes.get(0).getEventId());
        assertEquals(4.5, moyennes.get(0).getAverageRating());
        assertEquals(2, moyennes.get(0).getTotalFeedbacks());
        assertEquals(2.0, moyennes.get(1).getAverageRating());
    }
}
