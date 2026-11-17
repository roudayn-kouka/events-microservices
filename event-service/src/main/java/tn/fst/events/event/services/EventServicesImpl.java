package tn.fst.events.event.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.common.resilience.ClientsResilients;
import tn.fst.events.event.clients.LogisticsClient;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.clients.ParticipantClient;
import tn.fst.events.event.clients.ParticipantDTO;
import tn.fst.events.event.clients.RemoteCall;
import tn.fst.events.event.commands.ErreurCommande;
import tn.fst.events.event.commands.EventCommands.AffecterLogistique;
import tn.fst.events.event.commands.EventCommands.AffecterParticipant;
import tn.fst.events.event.commands.EventCommands.CreateEvent;
import tn.fst.events.event.commands.EventCommands.DeleteEvent;
import tn.fst.events.event.commands.EventCommands.UpdateEvent;
import tn.fst.events.event.commands.EventCommands.UpdateEventCost;
import tn.fst.events.event.dto.EventDetailsDTO;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.repositories.EventRepository;

/**
 * Repris de feature/event (Nour Mustapha), avec les corrections de feature/feedback.
 *
 * <p>Phase 2 : les accès aux bases participant et logistique sont devenus des appels OpenFeign.
 * <p>Phase 3 (CQRS) : les ÉCRITURES sont des commandes envoyées à EventAggregate
 * (CommandGateway, event sourcing dans Axon Server) ; les LECTURES restent sur la table event de
 * db_event, devenue le modèle de lecture tenu à jour par EventProjection. Les vérifications qui
 * demandent le réseau (Feign) sont faites ici, avant d'envoyer la commande.
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
    private final CommandGateway commandGateway;

    @Override
    public Event addEvent(Event event) {
        if (event.getIdEvent() == null) {
            event.setIdEvent(UUID.randomUUID().toString());
        }
        validerDates(event);
        envoyer(new CreateEvent(event.getIdEvent(), event.getDescription(), event.getDateDebut(), event.getDateFin(),
                Set.copyOf(participantIds(event))));
        // comportement du monolithe conservé : un coût fourni à la création est enregistré
        if (event.getCout() != 0f) {
            envoyer(new UpdateEventCost(event.getIdEvent(), event.getCout()));
        }
        return lire(event.getIdEvent());
    }

    @Override
    public Event retrieveEvent(String idEvent) {
        return eventRepository.findById(idEvent).orElse(null);
    }

    @Override
    public List<Event> retrieveAllEvents() {
        return eventRepository.findAll();
    }

    /** Description et dates par UpdateEvent ; le coût, s'il a changé, par UpdateEventCost. */
    @Override
    public Event updateEvent(Event event) {
        validerDates(event);
        envoyer(new UpdateEvent(event.getIdEvent(), event.getDescription(), event.getDateDebut(), event.getDateFin()));
        envoyer(new UpdateEventCost(event.getIdEvent(), event.getCout()));
        return lire(event.getIdEvent());
    }

    @Override
    public void deleteEvent(String idEvent) {
        envoyer(new DeleteEvent(idEvent));
    }

    /** Monolithe : participant.getEvents().add(event) (relation JPA). Ici : l'ID rejoint la commande de création. */
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
        evenementExistant(idEvent);
        verifierParticipant(idParticipant);
        envoyer(new AffecterParticipant(idEvent, idParticipant));
        return lire(idEvent);
    }

    /** Monolithe : event.getLogistics().add(logistics). Ici : création par Feign, puis commande AffecterLogistique. */
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

    /**
     * Toutes les 60 s dans le monolithe ; cron réglable (désactivé dans les tests : « - »).
     * Traitement de fond : ses appels Feign passent par les circuits « participant-service-batch »
     * et « logistics-service-batch », sans Retry. Une panne vue par le cron n'ouvre donc pas le
     * circuit des requêtes des utilisateurs (constaté en phase 7 : 503 au lieu de 502, panne 3).
     */
    @Scheduled(cron = "${events.cout.cron:0 * * * * *}")
    public void calculCoutPlanifie() {
        try {
            ClientsResilients.enTraitementDeFond(this::calculCout);
        } catch (ResponseStatusException e) {
            log.warn("calculCout reporté : {}", e.getReason());
        }
    }

    /**
     * Une commande UpdateEventCost par événement de l'organisateur : l'agrégat ne publie un
     * EventCostUpdated que si le coût a changé (pas d'événement par minute dans l'event store).
     */
    @Override
    public List<Event> calculCout() {
        Set<Integer> organisateurs = RemoteCall.call("participant-service", "Organisateur introuvable",
                        () -> participantClient.search(ORGANISATEUR_NOM, ORGANISATEUR_PRENOM, ORGANISATEUR_TACHE))
                .stream().map(ParticipantDTO::idPart).collect(Collectors.toSet());
        if (organisateurs.isEmpty()) {
            return List.of();
        }
        List<Event> events = eventRepository.findDistinctByParticipantIdsIn(organisateurs);
        List<Event> calcules = new ArrayList<>();
        for (Event event : events) {
            Set<Integer> ids = logisticsIds(event);
            // somme remise à zéro pour chaque événement ; aucun appel si pas de logistique
            float somme = ids.isEmpty() ? 0f : RemoteCall.call("logistics-service", "Logistique introuvable",
                    () -> logisticsClient.coutReserve(ids));
            try {
                envoyer(new UpdateEventCost(event.getIdEvent(), somme));
            } catch (ResponseStatusException e) {
                // un événement refusé (supprimé, absent de l'event store) ne bloque pas les autres
                log.warn("Coût de l'événement {} non mis à jour : {}", event.getIdEvent(), e.getReason());
                continue;
            }
            // copie détachée renvoyée à l'appelant ; la table est mise à jour par la projection
            event.setCout(somme);
            calcules.add(event);
        }
        return calcules;
    }

    /**
     * FALLBACK DÉGRADÉ (phase 4) : la vue composée reste disponible si participant-service ou
     * logistics-service est en panne ; la partie manquante est vide et signalée. (Les écritures,
     * elles, sont refusées : on n'inscrit pas un participant dont on ne peut pas vérifier l'existence.)
     */
    @Override
    public EventDetailsDTO details(String idEvent) {
        Event e = evenementExistant(idEvent);
        List<String> avertissements = new ArrayList<>();
        List<ParticipantDTO> participants = participantIds(e).isEmpty() ? List.of()
                : ouDegrade(avertissements, "participants", () -> RemoteCall.call("participant-service",
                        "Participant introuvable", () -> participantClient.getParticipants(e.getParticipantIds())));
        List<LogisticsDTO> logistics = logisticsIds(e).isEmpty() ? List.of()
                : ouDegrade(avertissements, "logistique", () -> RemoteCall.call("logistics-service",
                        "Logistique introuvable", () -> logisticsClient.getLogistics(e.getLogisticsIds())));
        return new EventDetailsDTO(e.getIdEvent(), e.getDescription(), e.getDateDebut(), e.getDateFin(),
                e.getCout(), participants, logistics, avertissements);
    }

    /** Panne d'un service (5xx, 429) : liste vide et avertissement ; une erreur métier (4xx) remonte. */
    private static <T> List<T> ouDegrade(List<String> avertissements, String partie, java.util.function.Supplier<List<T>> lecture) {
        try {
            return lecture.get();
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is4xxClientError() && ex.getStatusCode().value() != 429) {
                throw ex;
            }
            avertissements.add(partie + " indisponible : " + ex.getReason());
            return List.of();
        }
    }

    // ------------------------------------------------------------------ commandes

    /**
     * Envoie une commande et attend son traitement (agrégat + projection locale). Une règle refusée
     * par l'agrégat revient avec son code dans les « détails » (ErreurCommande, voir AxonConfig).
     */
    void envoyer(Object commande) {
        try {
            commandGateway.sendAndWait(commande);
        } catch (CommandExecutionException e) {
            throw traduire(e);
        } catch (AggregateNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found with id " + e.getAggregateIdentifier());
        }
    }

    static ResponseStatusException traduire(CommandExecutionException e) {
        Object details = e.getDetails().orElse(null);
        if (details instanceof ErreurCommande erreur) {
            return new ResponseStatusException(HttpStatus.valueOf(erreur.status()), erreur.message());
        }
        if (details instanceof Map<?, ?> champs && champs.get("status") instanceof Number status) {
            return new ResponseStatusException(HttpStatus.valueOf(status.intValue()), String.valueOf(champs.get("message")));
        }
        if (e.getCause() instanceof AggregateNotFoundException
                || String.valueOf(e.getMessage()).contains("not found in the event store")) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        return new ResponseStatusException(HttpStatus.CONFLICT, "Commande refusée : " + e.getMessage());
    }

    private Event lire(String idEvent) {
        return eventRepository.findById(idEvent).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Modèle de lecture non mis à jour pour l'événement " + idEvent));
    }

    // ------------------------------------------------------------------ outils

    private LogisticsDTO rattacherLogistique(Event event, LogisticsDTO logistics) {
        LogisticsDTO creee = RemoteCall.call("logistics-service", "Logistique introuvable",
                () -> logisticsClient.addLogistics(logistics));
        envoyer(new AffecterLogistique(event.getIdEvent(), creee.idLog()));
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
