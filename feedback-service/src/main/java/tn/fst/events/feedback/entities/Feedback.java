package tn.fst.events.feedback.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Repris de feature/feedback (Emna, ouemnaa). Les relations @ManyToOne Event et Participant (autres
 * bases) deviennent des identifiants ; la contrainte d'unicité (un avis par participant et par
 * événement) est conservée telle quelle, sur les mêmes colonnes.
 */
@Entity
@Table(name = "feedback", uniqueConstraints = @UniqueConstraint(
        name = "uk_feedback_event_participant", columnNames = {"event_id", "participant_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer note;

    @Lob
    private String commentaire;

    @Column(nullable = false, updatable = false)
    private Instant date;

    /** UUID de l'événement (event-service). */
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    /** Identifiant du participant (participant-service). */
    @Column(name = "participant_id", nullable = false)
    private Integer participantId;

    @PrePersist
    private void timestamp() {
        if (date == null) {
            date = Instant.now();
        }
    }
}
