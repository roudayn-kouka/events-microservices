package tn.fst.events.event.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Événement (repris de feature/event).
 * Monolithe :
 *   {@code @ManyToMany(mappedBy = "events") Set<Participant> participants}
 *   {@code @OneToMany(fetch = EAGER) Set<Logistics> logistics}
 * Microservices : participants et logistique vivent dans d'autres services et d'autres bases.
 * L'événement ne garde que leurs IDENTIFIANTS (tables event_participant et event_logistics de
 * db_event) ; leur existence est vérifiée et leurs données sont lues par OpenFeign.
 * Identifiant : UUID (String), futur identifiant de l'agrégat Axon (phase 3).
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Event implements Serializable {

    @Id
    @Column(length = 36)
    String idEvent;

    String description;

    LocalDate dateDebut;

    LocalDate dateFin;

    float cout;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_participant", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "participant_id")
    Set<Integer> participantIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_logistics", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "logistics_id")
    Set<Integer> logisticsIds = new HashSet<>();
}
