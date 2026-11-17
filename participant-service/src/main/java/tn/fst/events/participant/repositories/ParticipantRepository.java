package tn.fst.events.participant.repositories;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.fst.events.participant.entities.Participant;
import tn.fst.events.participant.entities.Tache;

public interface ParticipantRepository extends JpaRepository<Participant, Integer> {

    /** Remplace la requête du monolithe qui traversait la jointure Event↔Participant (calculCout). */
    List<Participant> findByNomAndPrenomAndTache(String nom, String prenom, Tache tache);

    /** Lecture par lot : event-service et statistics-service récupèrent N participants en un appel Feign. */
    List<Participant> findByIdPartIn(Collection<Integer> ids);
}
