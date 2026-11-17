package tn.fst.events.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gestionnaire d'exceptions global : chaque erreur devient un {@link ApiError} JSON avec le bon
 * code HTTP, au lieu de la page d'erreur par défaut.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Erreurs métier levées par les services (404 introuvable, 400 règle violée, 409 conflit…). */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> metier(ResponseStatusException ex, HttpServletRequest req) {
        return erreur(ex.getStatusCode(), ex.getReason(), req, List.of());
    }

    /** Corps JSON invalide au regard des annotations @Valid (@NotBlank, @Min…). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " : " + e.getDefaultMessage())
                .toList();
        return erreur(HttpStatus.BAD_REQUEST, "Données invalides", req, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> contraintes(ConstraintViolationException ex, HttpServletRequest req) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " : " + v.getMessage())
                .toList();
        return erreur(HttpStatus.BAD_REQUEST, "Données invalides", req, details);
    }

    /** Paramètre de chemin du mauvais type (ex. une tâche inconnue : /tache/NOT_A_TACHE). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeIncorrect(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return erreur(HttpStatus.BAD_REQUEST,
                "Valeur « " + ex.getValue() + " » invalide pour le paramètre " + ex.getName(), req, List.of());
    }

    /** JSON illisible (syntaxe, valeur d'énumération inconnue, nombre attendu…). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> illisible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return erreur(HttpStatus.BAD_REQUEST, "Corps de requête illisible", req, List.of());
    }

    private ResponseEntity<ApiError> erreur(HttpStatusCode status, String message, HttpServletRequest req,
                                            List<String> details) {
        HttpStatus connu = HttpStatus.resolve(status.value());
        String libelle = connu != null ? connu.getReasonPhrase() : String.valueOf(status.value());
        if (status.is5xxServerError()) {
            LOG.warn("{} {} -> {} : {}", req.getMethod(), req.getRequestURI(), status.value(), message);
        }
        ApiError corps = new ApiError(Instant.now(), status.value(), libelle, message, req.getRequestURI(), details);
        return ResponseEntity.status(status).body(corps);
    }
}
