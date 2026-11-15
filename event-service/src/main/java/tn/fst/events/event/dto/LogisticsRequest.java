package tn.fst.events.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import tn.fst.events.event.clients.LogisticsDTO;

/** Logistique à créer (dans logistics-service) puis à rattacher à un événement. */
public record LogisticsRequest(
        @NotBlank(message = "la description est obligatoire") String description,
        boolean reserve,
        @PositiveOrZero(message = "le prix unitaire ne peut pas être négatif") float prixUnit,
        @PositiveOrZero(message = "la quantité ne peut pas être négative") int quantite) {

    public LogisticsDTO toDto() {
        return new LogisticsDTO(0, description, reserve, prixUnit, quantite);
    }
}
