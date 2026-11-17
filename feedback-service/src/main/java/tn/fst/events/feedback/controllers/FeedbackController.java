package tn.fst.events.feedback.controllers;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tn.fst.events.feedback.dto.FeedbackAverageResponse;
import tn.fst.events.feedback.dto.FeedbackRequest;
import tn.fst.events.feedback.dto.FeedbackResponse;
import tn.fst.events.feedback.services.FeedbackService;

/**
 * Repris de feature/feedback ; mêmes chemins (/feedback/...). Via la Gateway : /api/feedback/**.
 * Les erreurs sont mises en forme par le GlobalExceptionHandler commun (module common), qui
 * remplace le FeedbackExceptionHandler d'origine.
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/{idEvent}/{idPart}")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse create(@PathVariable("idEvent") String idEvent, @PathVariable("idPart") int idPart,
                                   @Valid @RequestBody FeedbackRequest request) {
        return FeedbackResponse.from(feedbackService.createFeedback(idEvent, idPart,
                request.getNote(), request.getCommentaire()));
    }

    @GetMapping("/event/{id}")
    public List<FeedbackResponse> getByEvent(@PathVariable("id") String idEvent) {
        return feedbackService.getFeedbackByEvent(idEvent).stream().map(FeedbackResponse::from).toList();
    }

    @GetMapping("/event/{id}/moyenne")
    public FeedbackAverageResponse average(@PathVariable("id") String idEvent) {
        return feedbackService.getAverageRatingByEvent(idEvent);
    }

    /** Nouveau : moyennes de tous les événements notés (lu par statistics-service). */
    @GetMapping("/moyennes")
    public List<FeedbackAverageResponse> averages() {
        return feedbackService.getAverageRatings();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") int id) {
        feedbackService.deleteFeedback(id);
    }
}
