package tn.fst.events.feedback.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.feedback.clients.EventClient;
import tn.fst.events.feedback.clients.EventDTO;
import tn.fst.events.feedback.clients.FeignErreurs;
import tn.fst.events.feedback.clients.ParticipantClient;
import tn.fst.events.feedback.clients.ParticipantDTO;
import tn.fst.events.feedback.dto.FeedbackAverageResponse;
import tn.fst.events.feedback.entities.Feedback;
import tn.fst.events.feedback.repositories.FeedbackRepository;

/**
 * Repris de feature/feedback (20 méthodes, 24 exécutions avec les tests paramétrés), toutes
 * adaptées : EventRepository et ParticipantRepository simulés deviennent les clients Feign
 * simulés ; « événement introuvable » = 404 renvoyé par event-service ; l'appartenance du
 * participant se lit dans event.participantIds ; getStatus() → getStatusCode() ; l'événement a
 * un UUID. 4 tests ajoutés : panne d'event-service et de participant-service (503), moyennes
 * de tous les événements, vérification distante faite une seule fois par lecture.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    static final String EV = "11111111-1111-1111-1111-111111111111";
    static final String EV2 = "33333333-3333-3333-3333-333333333333";

    @Mock FeedbackRepository feedbackRepository;
    @Mock EventClient eventClient;
    @Mock ParticipantClient participantClient;
    @Mock org.axonframework.eventhandling.gateway.EventGateway eventGateway;
    FeedbackService service;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneId.of("Africa/Tunis"));
    LocalDate hier;

    @BeforeEach
    void setup() {
        service = new FeedbackService(feedbackRepository, eventClient, participantClient, eventGateway, clock);
        hier = LocalDate.now(clock).minusDays(1);
    }

    static EventDTO event(String id, LocalDate dateFin, Set<Integer> inscrits) {
        return new EventDTO(id, "Gala", null, dateFin, inscrits);
    }

    void eligible(LocalDate dateFin, Set<Integer> inscrits) {
        when(eventClient.getEvent(EV)).thenReturn(event(EV, dateFin, inscrits));
        when(participantClient.getParticipant(2)).thenReturn(new ParticipantDTO(2, "Doe", "Jane", "INVITE"));
    }

    void eligible() {
        eligible(hier, Set.of(2));
    }

    void assertStatus(HttpStatus status, Runnable operation) {
        assertEquals(status, assertThrows(ResponseStatusException.class, operation::run).getStatusCode());
    }

    @Test
    void createsWithServerTimestamp() {
        eligible();
        when(feedbackRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Feedback result = service.createFeedback(EV, 2, 5, "Great");
        assertEquals(5, result.getNote());
        assertEquals("Great", result.getCommentaire());
        assertEquals(clock.instant(), result.getDate());
        assertEquals(EV, result.getEventId());
        assertEquals(2, result.getParticipantId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, 6})
    void rejectsInvalidRating(Integer note) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createFeedback(EV, 2, note, null));
        verifyNoInteractions(eventClient, participantClient, feedbackRepository);
    }

    @Test
    void rejectsMissingEvent() {
        when(eventClient.getEvent(EV)).thenThrow(FeignErreurs.introuvable("/events/" + EV));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.createFeedback(EV, 2, 5, null));
    }

    @Test
    void rejectsMissingParticipant() {
        when(eventClient.getEvent(EV)).thenReturn(event(EV, hier, Set.of(2)));
        when(participantClient.getParticipant(2)).thenThrow(FeignErreurs.introuvable("/participants/2"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.createFeedback(EV, 2, 5, null));
    }

    @Test
    void rejectsUnassociatedParticipant() {
        eligible(hier, Collections.emptySet());
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createFeedback(EV, 2, 5, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsEventsEndingTodayOrLater(int days) {
        eligible(LocalDate.now(clock).plusDays(days), Set.of(2));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createFeedback(EV, 2, 5, null));
    }

    @Test
    void rejectsUnknownEndDate() {
        eligible(null, Set.of(2));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createFeedback(EV, 2, 5, null));
    }

    @Test
    void rejectsDuplicate() {
        eligible();
        when(feedbackRepository.existsByEventIdAndParticipantId(EV, 2)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.createFeedback(EV, 2, 5, null));
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletesExistingFeedback() {
        Feedback feedback = new Feedback();
        when(feedbackRepository.findById(3)).thenReturn(java.util.Optional.of(feedback));
        service.deleteFeedback(3);
        verify(feedbackRepository).delete(feedback);
    }

    @Test
    void rejectsDeletingMissingFeedback() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteFeedback(3));
    }

    @Test
    void retrievesFeedbackAndAverage() {
        when(eventClient.getEvent(EV)).thenReturn(event(EV, hier, Set.of()));
        Feedback feedback = new Feedback();
        when(feedbackRepository.findByEventIdOrderByDateAscIdAsc(EV)).thenReturn(Collections.singletonList(feedback));
        when(feedbackRepository.averageRatingByEvent(EV)).thenReturn(4.5);
        when(feedbackRepository.countByEventId(EV)).thenReturn(2L);
        assertSame(feedback, service.getFeedbackByEvent(EV).get(0));
        assertEquals(4.5, service.getAverageRatingByEvent(EV).getAverageRating());
        assertEquals(2, service.getAverageRatingByEvent(EV).getTotalFeedbacks());
    }

    @Test
    void emptyEventHasEmptyListAndZeroAverage() {
        when(eventClient.getEvent(EV)).thenReturn(event(EV, hier, Set.of()));
        assertTrue(service.getFeedbackByEvent(EV).isEmpty());
        assertEquals(0.0, service.getAverageRatingByEvent(EV).getAverageRating());
        assertEquals(0, service.getAverageRatingByEvent(EV).getTotalFeedbacks());
    }

    @Test
    void readsRejectMissingEvent() {
        when(eventClient.getEvent(EV)).thenThrow(FeignErreurs.introuvable("/events/" + EV));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getFeedbackByEvent(EV));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getAverageRatingByEvent(EV));
    }

    @Test
    void translatesConcurrentDatabaseDuplicateIntoConflict() {
        eligible();
        ConstraintViolationException constraint = new ConstraintViolationException("duplicate",
                new SQLException("duplicate", "23505"), "PUBLIC.UK_FEEDBACK_EVENT_PARTICIPANT_INDEX_1");
        when(feedbackRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", constraint));
        assertStatus(HttpStatus.CONFLICT, () -> service.createFeedback(EV, 2, 5, null));
    }

    @Test
    void doesNotMislabelOtherDatabaseErrorsAsDuplicates() {
        eligible();
        DataIntegrityViolationException error = new DataIntegrityViolationException("other constraint",
                new ConstraintViolationException("not null", new SQLException(), "nn_feedback_note"));
        when(feedbackRepository.saveAndFlush(any())).thenThrow(error);
        assertSame(error, assertThrows(DataIntegrityViolationException.class,
                () -> service.createFeedback(EV, 2, 5, null)));
    }

    @Test
    void doesNotMislabelUnknownDatabaseErrors() {
        eligible();
        DataIntegrityViolationException error = new DataIntegrityViolationException("unknown");
        when(feedbackRepository.saveAndFlush(any())).thenThrow(error);
        assertSame(error, assertThrows(DataIntegrityViolationException.class,
                () -> service.createFeedback(EV, 2, 5, null)));
    }

    @Test
    void rejectsNullMembershipCollection() {
        eligible(hier, null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createFeedback(EV, 2, 5, null));
    }

    @Test
    void sameParticipantCanReviewDifferentEvents() {
        eligible();
        when(eventClient.getEvent(EV2)).thenReturn(event(EV2, hier, Set.of(2)));
        when(feedbackRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(EV, service.createFeedback(EV, 2, 1, null).getEventId());
        assertEquals(EV2, service.createFeedback(EV2, 2, 5, null).getEventId());
    }

    @Test
    void multipleParticipantsCanReviewSameEvent() {
        eligible(hier, Set.of(2, 3));
        when(participantClient.getParticipant(3)).thenReturn(new ParticipantDTO(3, "Roe", "Max", "INVITE"));
        when(feedbackRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(2, service.createFeedback(EV, 2, 1, null).getParticipantId());
        assertEquals(3, service.createFeedback(EV, 3, 5, null).getParticipantId());
    }

    @Test
    void inclusiveEndDateChangesAtLocalMidnight() {
        eligible(LocalDate.of(2026, 10, 26), Set.of(2));
        Clock boundary = Clock.fixed(Instant.parse("2026-10-26T23:00:00Z"), ZoneId.of("Africa/Tunis"));
        FeedbackService atMidnight = new FeedbackService(feedbackRepository, eventClient, participantClient, eventGateway, boundary);
        FeedbackService beforeMidnight = new FeedbackService(feedbackRepository, eventClient,
                participantClient, eventGateway, Clock.offset(boundary, Duration.ofNanos(-1)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> beforeMidnight.createFeedback(EV, 2, 5, null));
        when(feedbackRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(boundary.instant(), atMidnight.createFeedback(EV, 2, 5, null).getDate());
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void eventServiceEnPanneRepond503SansRienEnregistrer() {
        when(eventClient.getEvent(anyString())).thenThrow(FeignErreurs.injoignable("/events/" + EV));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.createFeedback(EV, 2, 5, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals("event-service indisponible (pas de réponse)", e.getReason());
        verifyNoInteractions(participantClient, feedbackRepository);
    }

    @Test
    void participantServiceEnPanneRepond503() {
        when(eventClient.getEvent(EV)).thenReturn(event(EV, hier, Set.of(2)));
        when(participantClient.getParticipant(anyInt())).thenThrow(FeignErreurs.erreurServeur("/participants/2"));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.createFeedback(EV, 2, 5, null));
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    void moyennesDeTousLesEvenementsSansAppelDistant() {
        List<FeedbackAverageResponse> moyennes = List.of(new FeedbackAverageResponse(EV, 4.5, 2));
        when(feedbackRepository.averageRatings()).thenReturn(moyennes);
        assertSame(moyennes, service.getAverageRatings());
        verifyNoInteractions(eventClient, participantClient);
    }

    @Test
    void lectureDesAvisVerifieLEvenementUneSeuleFois() {
        when(eventClient.getEvent(EV)).thenReturn(event(EV, hier, Set.of()));
        service.getFeedbackByEvent(EV);
        verify(eventClient).getEvent(EV);
        verifyNoInteractions(participantClient);
    }

    // ---------------------------------------------------------------- ajoutés (phase 3, CQRS)

    @Test
    void unAvisEnregistrePublieFeedbackSubmitted() {
        eligible();
        when(feedbackRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Feedback f = invocation.getArgument(0);
            f.setId(11);
            return f;
        });

        service.createFeedback(EV, 2, 4, "Bien");

        verify(eventGateway).publish(new tn.fst.events.api.feedback.FeedbackEvents.FeedbackSubmitted(
                11, EV, 2, 4, clock.instant()));
    }

    @Test
    void unAvisRefuseNePublieRien() {
        eligible();
        when(feedbackRepository.existsByEventIdAndParticipantId(EV, 2)).thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> service.createFeedback(EV, 2, 5, null));

        verifyNoInteractions(eventGateway);
    }

    @Test
    void uneSuppressionPublieFeedbackDeleted() {
        Feedback feedback = new Feedback();
        feedback.setEventId(EV);
        when(feedbackRepository.findById(3)).thenReturn(java.util.Optional.of(feedback));

        service.deleteFeedback(3);

        verify(eventGateway).publish(new tn.fst.events.api.feedback.FeedbackEvents.FeedbackDeleted(3, EV));
    }
}
