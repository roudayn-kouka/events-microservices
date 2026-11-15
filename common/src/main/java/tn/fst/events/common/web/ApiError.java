package tn.fst.events.common.web;

import java.time.Instant;
import java.util.List;

/**
 * Format unique des erreurs renvoyées par tous les services (et relayées par la Gateway).
 *
 * @param details erreurs de validation champ par champ (vide sinon)
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       List<String> details) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }
}
