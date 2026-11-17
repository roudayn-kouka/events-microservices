package tn.fst.events.feedback.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Repris tel quel de feature/feedback : la date n'est jamais fournie par le client. */
@Getter
@Setter
public class FeedbackRequest {
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @JsonDeserialize(using = FeedbackRatingDeserializer.class)
    private Integer note;

    private String commentaire;
}
