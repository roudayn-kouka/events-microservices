package tn.fst.events.event.controllers;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.dto.EventDTO;
import tn.fst.events.event.dto.EventDetailsDTO;
import tn.fst.events.event.dto.EventRequest;
import tn.fst.events.event.dto.LogisticsRequest;
import tn.fst.events.event.dto.Mapper;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.services.IEventServices;

/**
 * API événements. Via la Gateway : /api/events/** (StripPrefix=1 → /events/**).
 * Monolithe : /events/event/addPart, /addEvent, /addEvent/{id}, /addAffectLog/{description},
 * /getLogs/{d1}/{d2} (aucune lecture d'un événement n'existait).
 */
@RequiredArgsConstructor
@RequestMapping("/events")
@RestController
public class EventRestController {

    private final IEventServices eventServices;

    @GetMapping
    public List<EventDTO> getEvents() {
        return Mapper.toDtos(eventServices.retrieveAllEvents());
    }

    @GetMapping("/{id}")
    public EventDTO getEvent(@PathVariable("id") String idEvent) {
        return Mapper.toDto(existant(idEvent));
    }

    /** Événement + participants + logistique, composés par Feign à partir de trois services. */
    @GetMapping("/{id}/details")
    public EventDetailsDTO getEventDetails(@PathVariable("id") String idEvent) {
        return eventServices.details(idEvent);
    }

    /** Ancien POST /event/addEvent : création, avec inscription de participantIds (vérifiés par Feign). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDTO addEvent(@Valid @RequestBody EventRequest event) {
        return Mapper.toDto(eventServices.addAffectEvenParticipant(Mapper.toEntity(event)));
    }

    /** Ancien POST /event/addEvent/{id} : création avec inscription d'un participant. */
    @PostMapping("/participant/{idPart}")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDTO addEventPart(@Valid @RequestBody EventRequest event, @PathVariable("idPart") int idPart) {
        return Mapper.toDto(eventServices.addAffectEvenParticipant(Mapper.toEntity(event), idPart));
    }

    @PutMapping("/{id}")
    public EventDTO updateEvent(@PathVariable("id") String idEvent, @Valid @RequestBody EventRequest changes) {
        Event event = existant(idEvent);
        event.setDescription(changes.getDescription());
        event.setDateDebut(changes.getDateDebut());
        event.setDateFin(changes.getDateFin());
        return Mapper.toDto(eventServices.updateEvent(event));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable("id") String idEvent) {
        existant(idEvent);
        eventServices.deleteEvent(idEvent);
    }

    /** Nouveau : inscrire un participant existant à un événement existant. */
    @PostMapping("/{id}/participants/{idPart}")
    public EventDTO affecterParticipant(@PathVariable("id") String idEvent, @PathVariable("idPart") int idPart) {
        return Mapper.toDto(eventServices.affecterParticipant(idEvent, idPart));
    }

    /** Nouveau : créer une logistique et la rattacher à l'événement. */
    @PostMapping("/{id}/logistics")
    @ResponseStatus(HttpStatus.CREATED)
    public LogisticsDTO affecterLogistique(@PathVariable("id") String idEvent,
                                           @Valid @RequestBody LogisticsRequest logistics) {
        return eventServices.affecterLogistique(idEvent, logistics.toDto());
    }

    /** Ancien PUT /event/addAffectLog/{description}. */
    @PutMapping("/by-description/{description}/logistics")
    public LogisticsDTO addAffectLog(@Valid @RequestBody LogisticsRequest logistics,
                                     @PathVariable("description") String descriptionEvent) {
        return eventServices.addAffectLog(logistics.toDto(), descriptionEvent);
    }

    /** Ancien GET /event/getLogs/{d1}/{d2} : logistique réservée des événements de la période. */
    @GetMapping("/logistics/{d1}/{d2}")
    public List<LogisticsDTO> getLogistiquesDates(
            @PathVariable("d1") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @PathVariable("d2") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return eventServices.getLogisticsDates(dateDebut, dateFin);
    }

    /** Nouveau : déclencher calculCout tout de suite (sinon toutes les 60 s). */
    @PostMapping("/cout/recalcul")
    public List<EventDTO> recalculerCout() {
        return Mapper.toDtos(eventServices.calculCout());
    }

    private Event existant(String idEvent) {
        Event event = eventServices.retrieveEvent(idEvent);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found with id " + idEvent);
        }
        return event;
    }
}
