package tn.fst.events.statistics.clients;

/** Logistique telle que la renvoie logistics-service. */
public record LogisticsDTO(int idLog, String description, boolean reserve, float prixUnit, int quantite) {
}
