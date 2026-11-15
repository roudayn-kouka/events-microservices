package tn.fst.events.event.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.event.clients.LogisticsClient;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.clients.ParticipantClient;
import tn.fst.events.event.clients.ParticipantDTO;
import tn.fst.events.event.clients.RemoteCall;
import tn.fst.events.event.dto.EventDetailsDTO;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Repris de feature/event (Nour Mustapha), avec les corrections de feature/feedback
 * (participant introuvable → 404 ; participants obligatoires → 400 ; événement sans logistique
 * ignoré par getLogisticsDates au lieu de renvoyer null).
 * Les accès directs à ParticipantRepository et LogisticsRepository (autres bases) sont remplacés
 * par des appels OpenFeign vers participant-service et logistics-service.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EventServicesImpl implements IEventServices {

    static final String ORGANISATEUR_NOM = "Tounsi";
    static final String ORGANISATEUR_PRENOM = "Ahmed";
    static final String ORGANISATEUR_TACHE = "ORGANISATEUR";

    private final EventRepository eventRepository;
    private final ParticipantClient participantClient;
    private final LogisticsClient logisticsClient;

    @Override
    public Event addEvent(Event event) {
        if (event.getIdEvent() == null) {
            event.setIdEvent(UUID.randomUUID().toString());
        }
        validerDates(event);
        return eventRepository.save(event);
    }

    @Override
    public Event retrieveEvent(String idEvent) {
        return eventRepository.findById(idEvent).orElse(null);
    }

    @Override
    public List<Event> retrieveAllEvents() {
        return eventRepository.findAll();
    }

    @Override
    public Event updateEvent(Event event) {
        validerDates(event);
        return eventRepository.save(event);
    }

    @Override
    public void deleteEvent(String idEvent) {
        eventRepository.deleteById(idEvent);
    }

    /** Monolithe : participant.getEvents().add(event) (relation JPA). Ici : l'ID rejoint event.participantIds. */
    @Override
    public Event addAffectEvenParticipant(Event event, int idParticipant) {
        verifierParticipant(idParticipant);
        participantIds(event).add(idParticipant);
        return addEvent(event);
    }

    @Override
    public Event addAffectEvenParticipant(Event event) {
        if (event.getParticipantIds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event participants are required");
        }
        verifierParticipants(event.getParticipantIds());
        return addEvent(event);
    }

    @Override
    public Event affecterParticipant(String idEvent, int idParticipant) {
        Event event = evenementExistant(idEvent);
        verifierParticipant(idParticipant);
        participantIds(event).add(idParticipant);
        return eventRepository.save(event);
    }

    /** Monolithe : event.getLogistics().add(logistics) puis logisticsRepository.save. Ici : Feign + ID. */
    @Override
    public LogisticsDTO addAffectLog(LogisticsDTO logistics, String descriptionEvent) {
        Event event = eventRepository.findFirstByDescription(descriptionEvent)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Event not found with description " + descriptionEvent));
        return rattacherLogistique(event, logistics);
    }

    @Override
    public LogisticsDTO affecterLogistique(String idEvent, LogisticsDTO logistics) {
        return rattacherLogistique(evenementExistant(idEvent), logistics);
    }

    @Override
    public List<LogisticsDTO> getLogisticsDates(LocalDate dateDebut, LocalDate dateFin) {
        Set<Integer> ids = eventRepository.findByDateDebutBetween(dateDebut, dateFin).stream()
                .flatMap(e -> logisticsIds(e).stream())
                .collect(Collectors.toCollection(TreeSet::new));
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return RemoteCall.call("logistics-service", "Logistique introuvable",
                        () -> logisticsClient.getLogistics(ids)).stream()
                .filter(LogisticsDTO::reserve)
                .toList();
    }

    /** Toutes les 60 s dans le monolithe ; cron réglable (désactivé dans les tests : « - »). */
    @Scheduled(cron = "${events.cout.cron:0 * * * * *}")
    public void calculCoutPlanifie() {
        try {
            calculCout();
        } catch (ResponseStatusException e) {
            log.warn("calculCout reporté : {}", e.getReason());
        }
    }

    @Override
    public List<Event> calculCout() {
        Set<Integer> organisateurs = RemoteCall.call("participant-service", "Organisateur introuvable",
                        () -> participantClient.search(ORGANISATEUR_NOM, ORGANISATEUR_PRENOM, ORGANISATEUR_TACHE))
                .stream().map(ParticipantDTO::idPart).collect(Collectors.toSet());
        if (organisateurs.isEmpty()) {
            return List.of();
        }
        List<Event> events = eventRepository.findDistinctByParticipantIdsIn(organisateurs);
        for (Event event : events) {
            Set<Integer> ids = logisticsIds(event);
            // somme remise à zéro pour chaque événement ; aucun appel si pas de logistique
            float somme = ids.isEmpty() ? 0f : RemoteCall.call("logistics-service", "Logistique introuvable",
                    () -> logisticsClient.coutReserve(ids));
            event.setCout(somme);
            eventRepository.save(event);
            log.info("Cout de l'Event {} est {}", event.getDescription(), somme);
        }
        return events;
    }

    @Override
    public EventDetailsDTO details(String idEvent) {
        Event e = evenementExistant(idEvent);
        List<ParticipantDTO> participants = participantIds(e).isEmpty() ? List.of()
                : RemoteCall.call("participant-service", "Participant introuvable",
                        () -> participantClient.getParticipants(e.getParticipantIds()));
        List<LogisticsDTO> logistics = logisticsIds(e).isEmpty() ? List.of()
                : RemoteCall.call("logistics-service", "Logistique introuvable",
                        () -> logisticsClient.getLogistics(e.getLogisticsIds()));
        return new EventDetailsDTO(e.getIdEvent(), e.getDescription(), e.getDateDebut(), e.getDateFin(),
                e.getCout(), participants, logistics);
    }

    // ------------------------------------------------------------------ outils

    private LogisticsDTO rattacherLogistique(Event event, LogisticsDTO logistics) {
        LogisticsDTO creee = RemoteCall.call("logistics-service", "Logistique introuvable",
                () -> logisticsClient.addLogistics(logistics));
        logisticsIds(event).add(creee.idLog());
        eventRepository.save(event);
        return creee;
    }

    private Event evenementExistant(String idEvent) {
        return eventRepository.findById(idEvent)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found with id " + idEvent));
    }

    private void verifierParticipant(int idParticipant) {
        RemoteCall.call("participant-service", "Participant not found with id " + idParticipant,
                () -> participantClient.getParticipant(idParticipant));
    }

    /** Un seul appel Feign pour tous les participants ; 404 qui liste les ID manquants. */
    private void verifierParticipants(Set<Integer> ids) {
        if (ids.isEmpty()) {
            return;
        }
        Set<Integer> trouves = RemoteCall.call("participant-service", "Participant introuvable",
                () -> participantClient.getParticipants(ids)).stream()
                .map(ParticipantDTO::idPart).collect(Collectors.toSet());
        Set<Integer> manquants = new TreeSet<>(ids);
        manquants.removeAll(trouves);
        if (!manquants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found with id " + manquants);
        }
    }

    private static void validerDates(Event event) {
        if (event.getDateDebut() != null && event.getDateFin() != null
                && event.getDateFin().isBefore(event.getDateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin précède la date de début");
        }
    }

    private static Set<Integer> participantIds(Event e) {
        if (e.getParticipantIds() == null) {
            e.setParticipantIds(new HashSet<>());
        }
        return e.getParticipantIds();
    }

    private static Set<Integer> logisticsIds(Event e) {
        if (e.getLogisticsIds() == null) {
            e.setLogisticsIds(new HashSet<>());
        }
        return e.getLogisticsIds();
    }
}
