package tn.fst.events.event.projections;

import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import tn.fst.events.api.event.EventEvents.EventCostUpdated;
import tn.fst.events.api.event.EventEvents.EventCreated;
import tn.fst.events.api.event.EventEvents.EventDeleted;
import tn.fst.events.api.event.EventEvents.EventUpdated;
import tn.fst.events.api.event.EventEvents.LogistiqueAffectee;
import tn.fst.events.api.event.EventEvents.ParticipantAffecte;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Modèle de LECTURE local d'event-service : la table event de db_event, tenue à jour à partir des
 * événements de l'agrégat. Groupe « event-lecture » en mode subscribing (config-repo) : traité
 * dans l'unité de travail de la commande, donc à jour dès que sendAndWait rend la main. Les
 * lectures de l'API (GET /events…) et les vérifications Feign de feedback-service lisent ici.
 */
@Slf4j
@Component
@ProcessingGroup(EventProjection.GROUPE)
@RequiredArgsConstructor
public class EventProjection {

    public static final String GROUPE = "event-lecture";

    private final EventRepository eventRepository;

    @EventHandler
    void on(EventCreated e) {
        Event event = new Event();
        event.setIdEvent(e.idEvent());
        event.setDescription(e.description());
        event.setDateDebut(e.dateDebut());
        event.setDateFin(e.dateFin());
        event.setParticipantIds(new HashSet<>(e.participantIds()));
        event.setLogisticsIds(new HashSet<>());
        eventRepository.save(event);
    }

    @EventHandler
    void on(EventUpdated e) {
        eventRepository.findById(e.idEvent()).ifPresent(event -> {
            event.setDescription(e.description());
            event.setDateDebut(e.dateDebut());
            event.setDateFin(e.dateFin());
            eventRepository.save(event);
        });
    }

    @EventHandler
    void on(EventDeleted e) {
        eventRepository.deleteById(e.idEvent());
    }

    @EventHandler
    void on(ParticipantAffecte e) {
        eventRepository.findById(e.idEvent()).ifPresent(event -> {
            event.getParticipantIds().add(e.idPart());
            eventRepository.save(event);
        });
    }

    @EventHandler
    void on(LogistiqueAffectee e) {
        eventRepository.findById(e.idEvent()).ifPresent(event -> {
            event.getLogisticsIds().add(e.idLog());
            eventRepository.save(event);
        });
    }

    @EventHandler
    void on(EventCostUpdated e) {
        eventRepository.findById(e.idEvent()).ifPresent(event -> {
            event.setCout(e.cout());
            eventRepository.save(event);
            log.info("Cout de l'Event {} est {}", event.getDescription(), e.cout());
        });
    }
}
