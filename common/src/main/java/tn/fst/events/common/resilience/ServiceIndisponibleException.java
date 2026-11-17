package tn.fst.events.common.resilience;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Résultat d'un FALLBACK explicite : l'appel vers un autre service a échoué et n'a pas été
 * retenté (ou plus), pour une raison nommée (circuit ouvert, délai dépassé, trop d'appels,
 * réponse incorrecte, réseau). Hérite de ResponseStatusException : le GlobalExceptionHandler
 * commun répond avec ce code et ce message, et les « catch (ResponseStatusException) » existants
 * (calculCout planifié, par exemple) la traitent comme toute erreur HTTP.
 */
public class ServiceIndisponibleException extends ResponseStatusException {

    private final String service;
    private final String raison;

    public ServiceIndisponibleException(HttpStatus status, String service, String raison, Throwable cause) {
        super(status, service + " : " + raison, cause);
        this.service = service;
        this.raison = raison;
    }

    public String getService() {
        return service;
    }

    public String getRaison() {
        return raison;
    }
}
