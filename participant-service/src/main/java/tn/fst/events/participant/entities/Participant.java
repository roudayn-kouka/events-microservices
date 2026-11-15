package tn.fst.events.participant.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Participant (repris de feature/participant).
 * Monolithe : {@code @ManyToMany Set<Event> events} (propriétaire de la table de jointure).
 * Microservices : plus aucune référence aux événements. C'est event-service qui conserve
 * les identifiants des participants inscrits (table event_participant dans db_event).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Participant implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer idPart;

    String nom;

    String prenom;

    @Enumerated(EnumType.STRING)
    Tache tache;
}
