package tn.fst.events.feedback.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.feedback.clients.EventClient;
import tn.fst.events.feedback.clients.EventDTO;
import tn.fst.events.feedback.clients.ParticipantClient;
import tn.fst.events.feedback.clients.RemoteCall;
import tn.fst.events.feedback.dto.FeedbackAverageResponse;
import tn.fst.events.feedback.entities.Feedback;
import tn.fst.events.feedback.repositories.FeedbackRepository;

/**
 * Repris de feature/feedback (Emna, ouemnaa). Les règles et leur ordre sont inchangés :
 * note 1 à 5 → événement existant → participant existant et inscrit → événement terminé → pas de doublon.
 * Changements : EventRepository et ParticipantRepository (autres bases) deviennent des appels
 * OpenFeign ; « participant associé » se lit dans les inscrits de l'événement (participantIds),
 * car c'est désormais event-service qui porte l'inscription.
 */
@Service
@Transactional(readOnly = true)
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final EventClient eventClient;
    private final ParticipantClient participantClient;
    private final Clock clock;

    /** Fuseau de la règle « événement terminé » : réglable (Config Server), Africa/Tunis par défaut. */
    @Autowired
    public FeedbackService(FeedbackRepository feedbackRepository, EventClient eventClient,
                           ParticipantClient participantClient,
                           @Value("${feedback.fuseau-horaire:Africa/Tunis}") String fuseauHoraire) {
        this(feedbackRepository, eventClient, participantClient, Clock.system(ZoneId.of(fuseauHoraire)));
    }

    public FeedbackService(FeedbackRepository feedbackRepository, EventClient eventClient,
                           ParticipantClient participantClient, Clock clock) {
        this.feedbackRepository = feedbackRepository;
        this.eventClient = eventClient;
        this.participantClient = participantClient;
        this.clock = clock;
    }

    @Transactional
    public Feedback createFeedback(String eventId, int participantId, Integer note, String commentaire) {
        validateRating(note);
        EventDTO event = loadEvent(eventId);
        requireAssociatedParticipant(event, participantId);
        requireFinishedEvent(event);
        if (feedbackRepository.existsByEventIdAndParticipantId(eventId, participantId)) {
            throw duplicateFeedback();
        }
        return saveFeedback(eventId, participantId, note, commentaire);
    }

    public List<Feedback> getFeedbackByEvent(String eventId) {
        loadEvent(eventId);
        return feedbackRepository.findByEventIdOrderByDateAscIdAsc(eventId);
    }

    public FeedbackAverageResponse getAverageRatingByEvent(String eventId) {
        loadEvent(eventId);
        return new FeedbackAverageResponse(eventId, feedbackRepository.averageRatingByEvent(eventId),
                feedbackRepository.countByEventId(eventId));
    }

    /** Nouveau, pour statistics-service : aucune vérification distante (lecture de db_feedback seule). */
    public List<FeedbackAverageResponse> getAverageRatings() {
        return feedbackRepository.averageRatings();
    }

    @Transactional
    public void deleteFeedback(int feedbackId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
        feedbackRepository.delete(feedback);
    }

    // ------------------------------------------------------------------ règles

    private void validateRating(Integer note) {
        if (note == null || note < 1 || note > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be an integer between 1 and 5");
        }
    }

    /** Monolithe : eventRepository.findById ; ici : GET /events/{id} sur event-service. */
    private EventDTO loadEvent(String eventId) {
        return RemoteCall.call("event-service", "Event not found", () -> eventClient.getEvent(eventId));
    }

    /** Monolithe : participantRepository.findById puis participant.getEvents() ; ici : Feign + inscrits. */
    private void requireAssociatedParticipant(EventDTO event, int participantId) {
        RemoteCall.call("participant-service", "Participant not found",
                () -> participantClient.getParticipant(participantId));
        boolean associated = event.participantIds() != null && event.participantIds().contains(participantId);
        if (!associated) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participant is not associated with this event");
        }
    }

    private void requireFinishedEvent(EventDTO event) {
        // dateFin est incluse : un événement qui se termine aujourd'hui n'est pas encore terminé.
        if (event.dateFin() == null || !event.dateFin().isBefore(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event has not finished");
        }
    }

    private Feedback saveFeedback(String eventId, int participantId, Integer note, String commentaire) {
        Feedback feedback = new Feedback();
        feedback.setEventId(eventId);
        feedback.setParticipantId(participantId);
        feedback.setNote(note);
        feedback.setCommentaire(commentaire);
        feedback.setDate(clock.instant());
        try {
            // flush dans la transaction : un doublon concurrent est traduit avant de répondre
            return feedbackRepository.saveAndFlush(feedback);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateFeedbackConstraint(exception)) {
                throw duplicateFeedback();
            }
            throw exception;
        }
    }

    private boolean isDuplicateFeedbackConstraint(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                return constraint != null && constraint.toLowerCase(Locale.ROOT).contains("uk_feedback_event_participant");
            }
        }
        return false;
    }

    private ResponseStatusException duplicateFeedback() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Participant already reviewed this event");
    }
}
