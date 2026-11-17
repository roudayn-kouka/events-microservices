package tn.fst.events.event.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import tn.fst.events.event.entities.Event;

/**
 * Ajouté : requêtes dérivées sur les collections d'ID (@ElementCollection), qui remplacent
 * findByParticipants_NomAndParticipants_PrenomAndParticipants_Tache du monolithe (jointure
 * impossible : les participants sont dans db_participant).
 */
@DataJpaTest(showSql = false)
class EventRepositoryTest {

    @Autowired
    EventRepository repository;

    private Event save(String description, LocalDate debut, Set<Integer> participants, Set<Integer> logistics) {
        Event e = new Event();
        e.setIdEvent(UUID.randomUUID().toString());
        e.setDescription(description);
        e.setDateDebut(debut);
        e.setDateFin(debut.plusDays(1));
        e.setParticipantIds(new java.util.HashSet<>(participants));
        e.setLogisticsIds(new java.util.HashSet<>(logistics));
        return repository.save(e);
    }

    @Test
    void findDistinctByParticipantIdsInRenvoieChaqueEvenementUneSeuleFois() {
        save("Gala", LocalDate.of(2026, 5, 1), Set.of(1, 2), Set.of(5));
        save("Forum", LocalDate.of(2026, 6, 1), Set.of(2), Set.of());
        save("Atelier", LocalDate.of(2026, 7, 1), Set.of(3), Set.of());

        List<Event> events = repository.findDistinctByParticipantIdsIn(Set.of(1, 2));

        assertEquals(2, events.size()); // Gala une seule fois malgré deux participants trouvés
        assertTrue(events.stream().map(Event::getDescription).toList().containsAll(List.of("Gala", "Forum")));
    }

    @Test
    void lesEnsemblesDIdSontRelusEtLaRechercheParDatesFonctionne() {
        Event gala = save("Gala", LocalDate.of(2026, 5, 1), Set.of(1), Set.of(5, 6));
        save("Hiver", LocalDate.of(2026, 12, 1), Set.of(), Set.of());

        Event relu = repository.findFirstByDescription("Gala").orElseThrow();
        List<Event> mai = repository.findByDateDebutBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertEquals(gala.getIdEvent(), relu.getIdEvent());
        assertEquals(Set.of(5, 6), relu.getLogisticsIds());
        assertEquals(List.of("Gala"), mai.stream().map(Event::getDescription).toList());
    }

    /** Monolithe : Event findByDescription → IncorrectResultSizeDataAccessException (HTTP 500) sur doublon. */
    @Test
    void deuxEvenementsDeMemeDescriptionNeProvoquentPasDErreur() {
        save("Gala", LocalDate.of(2026, 5, 1), Set.of(), Set.of());
        save("Gala", LocalDate.of(2026, 6, 1), Set.of(), Set.of());

        assertTrue(repository.findFirstByDescription("Gala").isPresent());
    }
}
