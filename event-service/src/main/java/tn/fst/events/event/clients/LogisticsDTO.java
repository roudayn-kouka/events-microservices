package tn.fst.events.event.clients;

/** Vue d'une logistique telle que renvoyée par logistics-service (copie locale du contrat). */
public record LogisticsDTO(int idLog, String description, boolean reserve, float prixUnit, int quantite) {
}
