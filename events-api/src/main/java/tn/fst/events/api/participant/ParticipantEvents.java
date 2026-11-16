package tn.fst.events.api.participant;

/**
 * Publiés par participant-service après chaque écriture réussie (EventGateway). Le participant
 * reste un CRUD classique dans db_participant : ces événements informent les autres services,
 * ils ne sont pas la source de vérité (pas d'event sourcing ici).
 */
public final class ParticipantEvents {

    private ParticipantEvents() {
    }

    /** Identifiants en Integer, comme les entités JPA (aucun déballage possible de null).
     *  tache : INVITE, ORGANISATEUR, SERVEUR ou ANIMATEUR (texte : le contrat ne dépend d'aucune énumération). */
    public record ParticipantCreated(Integer idPart, String nom, String prenom, String tache) {
    }

    public record ParticipantUpdated(Integer idPart, String nom, String prenom, String tache) {
    }

    public record ParticipantDeleted(Integer idPart) {
    }
}
