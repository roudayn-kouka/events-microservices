package tn.fst.events.participant.services;

import java.util.Collection;
import java.util.List;
import tn.fst.events.participant.entities.Participant;
import tn.fst.events.participant.entities.Tache;

public interface IParticipantServices {

    Participant addParticipant(Participant participant);

    List<Participant> getAllParticipants();

    Participant getParticipantById(int idPart);

    Participant updateParticipant(int idPart, Participant participant);

    void deleteParticipant(int idPart);

    Participant changeTache(int idPart, Tache tache);

    /** Nouveau : lecture par lot pour les appels Feign. */
    List<Participant> getParticipantsByIds(Collection<Integer> ids);

    /** Nouveau : recherche exacte utilisée par calculCout (event-service). */
    List<Participant> search(String nom, String prenom, Tache tache);
}
