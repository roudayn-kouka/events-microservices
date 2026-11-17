package tn.fst.events.logistics.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Logistique (reprise de feature/logistics, champs inchangés).
 * Monolithe : rattachée à un événement par {@code Event.logistics} (@OneToMany, table de jointure
 * dans la même base). Microservices : event-service conserve les identifiants (event_logistics).
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Logistics implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int idLog;

    String description;

    boolean reserve;

    float prixUnit;

    int quantite;
}
