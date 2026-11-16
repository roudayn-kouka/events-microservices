package tn.fst.events.event.commands;

/**
 * Règle métier refusée par l'agrégat. Indépendante du web : elle porte seulement le code HTTP
 * proposé (400 donnée invalide, 404 événement supprimé), retraduit par EventServicesImpl.
 */
public class RegleMetierException extends RuntimeException {

    private final int status;

    public RegleMetierException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
