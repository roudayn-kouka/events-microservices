package tn.fst.events.statistics.dto;

import java.io.Serializable;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Indicateurs globaux du tableau de bord (GET /stats/summary). Repris de feature/event-statistics. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GlobalStatsDTO implements Serializable {

    long nbEvents;
    long nbEventsAVenir;
    long nbParticipants;
    long nbLogistics;
    long nbLogisticsReservees;

    /** Pourcentage de logistiques réservées (0 -> 100). */
    float tauxReservation;

    /** Somme prixUnit * quantite de toutes les logistiques réservées. */
    float coutTotalLogistiqueReservee;

    /** Répartition de tous les participants par tâche. */
    Map<Tache, Long> participantsParTache;

    /** Nombre total d'avis (ajouté). */
    long nbFeedbacks;

    /** Note moyenne de tous les avis, pondérée par le nombre d'avis de chaque événement (ajouté). */
    double noteMoyenne;
}
