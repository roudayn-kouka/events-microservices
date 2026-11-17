package tn.fst.events.event.repositories;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.fst.events.event.entities.Event;

public interface EventRepository extends JpaRepository<Event, String> {

    Optional<Event> findFirstByDescription(String description);

    List<Event> findByDateDebutBetween(LocalDate date1, LocalDate date2);

    /**
     * Remplace findByParticipants_NomAndParticipants_PrenomAndParticipants_Tache du monolithe
     * (jointure vers la table participant, désormais dans une autre base) : on cherche les
     * événements qui contiennent l'un des identifiants fournis par participant-service.
     */
    List<Event> findDistinctByParticipantIdsIn(Collection<Integer> participantIds);

    // Événements à venir (feature statistiques)
    List<Event> findByDateDebutGreaterThanEqualOrderByDateDebutAsc(LocalDate date);
}
