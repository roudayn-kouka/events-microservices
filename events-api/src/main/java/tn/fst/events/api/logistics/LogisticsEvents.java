package tn.fst.events.api.logistics;

/**
 * Publiés par logistics-service après chaque écriture réussie. LogisticsUpdated couvre aussi le
 * changement de réservation (champ reserve) : un seul événement porte l'état complet.
 */
public final class LogisticsEvents {

    private LogisticsEvents() {
    }

    public record LogisticsCreated(int idLog, String description, boolean reserve, float prixUnit, int quantite) {
    }

    public record LogisticsUpdated(int idLog, String description, boolean reserve, float prixUnit, int quantite) {
    }

    public record LogisticsDeleted(int idLog) {
    }
}
