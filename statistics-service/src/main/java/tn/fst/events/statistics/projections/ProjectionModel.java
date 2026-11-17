package tn.fst.events.statistics.projections;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Modèle de lecture CQRS de statistics-service (db_stats) : quatre tables alimentées UNIQUEMENT par
 * les événements Axon (StatistiquesProjection). Elles ne sont jamais écrites par une API : elles se
 * reconstruisent à l'identique par replay de l'event store. Classes imbriquées : nom d'entité
 * explicite (sinon Hibernate les nomme « ProjectionModel$StatsAvis » en JPQL).
 */
public final class ProjectionModel {

    private ProjectionModel() {
    }

    @Entity(name = "StatsEvenement")
    @Table(name = "stats_evenement")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StatsEvenement {
        @Id
        @Column(length = 36)
        private String idEvent;
        private String description;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private float cout;

        @ElementCollection(fetch = FetchType.EAGER)
        @CollectionTable(name = "stats_evenement_participant", joinColumns = @JoinColumn(name = "event_id"))
        @Column(name = "participant_id")
        private Set<Integer> participantIds = new HashSet<>();

        @ElementCollection(fetch = FetchType.EAGER)
        @CollectionTable(name = "stats_evenement_logistique", joinColumns = @JoinColumn(name = "event_id"))
        @Column(name = "logistics_id")
        private Set<Integer> logisticsIds = new HashSet<>();
    }

    @Entity(name = "StatsParticipant")
    @Table(name = "stats_participant")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StatsParticipant {
        @Id
        private Integer idPart;
        private String nom;
        private String prenom;
        private String tache;
    }

    @Entity(name = "StatsLogistique")
    @Table(name = "stats_logistique")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StatsLogistique {
        @Id
        private Integer idLog;
        private String description;
        private boolean reserve;
        private float prixUnit;
        private int quantite;
    }

    @Entity(name = "StatsAvis")
    @Table(name = "stats_avis")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StatsAvis {
        @Id
        private Integer id;
        @Column(length = 36)
        private String eventId;
        private Integer participantId;
        private Integer note;
        private Instant date;
    }
}
