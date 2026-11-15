package tn.fst.events.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Création ou modification d'un événement (inspiré de EventWriteRequest de feature/feedback).
 * Les participants sont désignés par leur identifiant : participantIds.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventRequest {

    @NotBlank(message = "la description est obligatoire")
    private String description;

    @NotNull(message = "la date de début est obligatoire")
    private LocalDate dateDebut;

    @NotNull(message = "la date de fin est obligatoire")
    private LocalDate dateFin;

    private Set<Integer> participantIds;
}
