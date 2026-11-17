package tn.fst.events.logistics.repositories;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.fst.events.logistics.entities.Logistics;

@Repository
public interface LogisticsRepository extends JpaRepository<Logistics, Integer> {

    // Dérivée du nom de la méthode (pas de requête à écrire)
    List<Logistics> findByReserveTrue();

    List<Logistics> findByDescriptionContaining(String keyword);

    /** Lecture par lot : event-service récupère la logistique d'un événement en un appel Feign. */
    List<Logistics> findByIdLogIn(Collection<Integer> ids);
}
