package tn.fst.events.logistics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO repris de feature/participant (le contrôleur d'origine exposait l'entité JPA), plus la validation. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsDTO {

    private int idLog;

    @NotBlank(message = "la description est obligatoire")
    private String description;

    private boolean reserve;

    @PositiveOrZero(message = "le prix unitaire ne peut pas être négatif")
    private float prixUnit;

    @PositiveOrZero(message = "la quantité ne peut pas être négative")
    private int quantite;
}
