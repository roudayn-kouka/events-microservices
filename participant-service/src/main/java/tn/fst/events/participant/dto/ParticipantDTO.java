package tn.fst.events.participant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.fst.events.participant.entities.Tache;

/**
 * Représentation exposée par l'API (aucune entité JPA n'est exposée).
 * Monolithe : contenait aussi {@code Set<EventDTO> events}, retiré (relation portée par event-service).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParticipantDTO {

    private Integer idPart;

    @NotBlank(message = "le nom est obligatoire")
    private String nom;

    @NotBlank(message = "le prénom est obligatoire")
    private String prenom;

    @NotNull(message = "la tâche est obligatoire")
    private Tache tache;
}
