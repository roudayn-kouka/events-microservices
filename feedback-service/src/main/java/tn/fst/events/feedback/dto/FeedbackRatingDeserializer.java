package tn.fst.events.feedback.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * Repris tel quel de feature/feedback : empêche Jackson de tronquer une note décimale (1.5 → 1)
 * ou d'accepter une chaîne (« 5 ») ; seule une valeur entière JSON est acceptée.
 */
public class FeedbackRatingDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Integer.class, "Rating must be an integer between 1 and 5");
        }
        return parser.getIntValue();
    }
}
