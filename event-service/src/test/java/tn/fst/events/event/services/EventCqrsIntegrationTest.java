package tn.fst.events.event.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.api.event.EventEvents;
import tn.fst.events.event.clients.LogisticsClient;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.clients.ParticipantClient;
import tn.fst.events.event.clients.ParticipantDTO;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Ajouté (phase 3) : la chaîne CQRS complète dans un vrai contexte Spring — service → commande →
 * EventAggregate (event store JPA sur H2, Axon Server désactivé en test) → événements →
 * EventProjection → table event. Seuls les clients Feign sont simulés.
 */
@SpringBootTest
class EventCqrsIntegrationTest {

    @Autowired IEventServices service;
    @Autowired EventRepository eventRepository;
    @Autowired EventStore eventStore;

    @MockitoBean ParticipantClient participantClient;
    @MockitoBean LogisticsClient logisticsClient;

    private Event nouvelEvenement(String description) {
        Event e = new Event();
        e.setDescription(description);
        e.setDateDebut(LocalDate.of(2026, 12, 10));
        e.setDateFin(LocalDate.of(2026, 12, 11));
        e.setParticipantIds(new HashSet<>());
        return service.addEvent(e);
    }

    private List<Object> historique(String idEvent) {
        return eventStore.readEvents(idEvent).asStream().map(m -> (Object) m.getPayload()).toList();
    }

    @Test
    void lesAffectationsEtLeCoutPassentParLAgregatPuisLaProjection() {
        String id = nouvelEvenement("Forum CQRS").getIdEvent();
        when(participantClient.getParticipant(1)).thenReturn(new ParticipantDTO(1, "Tounsi", "Ahmed", "ORGANISATEUR"));
        when(logisticsClient.addLogistics(new LogisticsDTO(0, "Chaises", true, 2.5f, 100)))
                .thenReturn(new LogisticsDTO(7, "Chaises", true, 2.5f, 100));
        when(participantClient.search("Tounsi", "Ahmed", "ORGANISATEUR"))
                .thenReturn(List.of(new ParticipantDTO(1, "Tounsi", "Ahmed", "ORGANISATEUR")));
        when(logisticsClient.coutReserve(anyCollection())).thenReturn(250f);

        service.affecterParticipant(id, 1);
        service.affecterParticipant(id, 1); // idempotent : aucun second événement
        service.affecterLogistique(id, new LogisticsDTO(0, "Chaises", true, 2.5f, 100));
        service.calculCout();
        service.calculCout();               // coût inchangé : aucun second EventCostUpdated

        Event lu = eventRepository.findById(id).orElseThrow();
        assertEquals(Set.of(1), lu.getParticipantIds());
        assertEquals(Set.of(7), lu.getLogisticsIds());
        assertEquals(250f, lu.getCout(), 0.001);
        assertEquals(List.of(EventEvents.EventCreated.class, EventEvents.ParticipantAffecte.class,
                        EventEvents.LogistiqueAffectee.class, EventEvents.EventCostUpdated.class),
                historique(id).stream().map(Object::getClass).toList());
    }

    @Test
    void unEvenementSupprimeRefuseLesCommandesAvecUn404Explicite() {
        String id = nouvelEvenement("Atelier supprimé").getIdEvent();

        service.deleteEvent(id);

        assertNull(service.retrieveEvent(id)); // projection : ligne supprimée
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.deleteEvent(id));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode()); // règle de l'agrégat, transportée par AxonConfig
        assertEquals("Event not found with id " + id, e.getReason());
    }

    @Test
    void lEtatEstReconstruitDepuisLEventStore() {
        Event cree = nouvelEvenement("Hackathon");
        cree.setDescription("Hackathon IA");
        service.updateEvent(cree);

        List<Object> historique = historique(cree.getIdEvent());

        assertEquals(2, historique.size());
        assertEquals("Hackathon IA", ((EventEvents.EventUpdated) historique.get(1)).description());
    }
}
