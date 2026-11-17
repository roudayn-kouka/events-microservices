package tn.fst.events.statistics.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Résumé statistique d'un événement (GET /stats/events…). Repris de feature/event-statistics ;
 * idEvent devient l'UUID, et la note moyenne des avis (variante de feature/feedback) est ajoutée.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventStatsDTO implements Serializable {

    String idEvent;
    String description;
    LocalDate dateDebut;
    LocalDate dateFin;

    /** Nombre de jours de l'événement (date de début et date de fin incluses). */
    long dureeJours;

    /** Nombre de participants inscrits (connus de participant-service). */
    int nbParticipants;

    /** Répartition des participants par tâche (INVITE, ORGANISATEUR, ...). */
    Map<Tache, Long> participantsParTache;

    /** Nombre de logistiques liées à l'événement. */
    int nbLogistics;

    /** Nombre de logistiques réservées. */
    int nbLogisticsReservees;

    /** Coût des logistiques réservées : somme de prixUnit * quantite. */
    float coutLogistiqueReservee;

    /** Note moyenne des avis (0 sans avis), lue dans feedback-service. */
    double averageRating;

    /** Nombre d'avis reçus. */
    long nbFeedbacks;
}
