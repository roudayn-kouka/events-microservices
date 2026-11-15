package tn.fst.events.event.services;

import java.time.LocalDate;
import java.util.List;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.dto.EventDetailsDTO;
import tn.fst.events.event.entities.Event;

/**
 * Repris de feature/event. Changements : identifiant String (UUID) ; participants et logistique
 * désignés par ID et lus par Feign ; addParticipant (création d'un participant) a quitté ce
 * service : c'est le rôle de participant-service (POST /api/participants).
 */
public interface IEventServices {

    Event addEvent(Event event);

    /** null si l'événement n'existe pas (comportement d'origine, testé) ; le contrôleur répond 404. */
    Event retrieveEvent(String idEvent);

    List<Event> retrieveAllEvents();

    Event updateEvent(Event event);

    void deleteEvent(String idEvent);

    /** Crée l'événement en y inscrivant un participant existant (vérifié par Feign). */
    Event addAffectEvenParticipant(Event event, int idParticipant);

    /** Crée l'événement en y inscrivant tous ses participantIds (tous vérifiés par Feign). */
    Event addAffectEvenParticipant(Event event);

    /** Nouveau : inscrit un participant existant à un événement existant. */
    Event affecterParticipant(String idEvent, int idParticipant);

    /** Crée la logistique (logistics-service, Feign) et la rattache à l'événement de cette description. */
    LogisticsDTO addAffectLog(LogisticsDTO logistics, String descriptionEvent);

    /** Nouveau : même chose, en désignant l'événement par son identifiant. */
    LogisticsDTO affecterLogistique(String idEvent, LogisticsDTO logistics);

    /** Logistique RÉSERVÉE des événements qui commencent entre les deux dates. */
    List<LogisticsDTO> getLogisticsDates(LocalDate dateDebut, LocalDate dateFin);

    /** Recalcule le coût des événements de l'organisateur « Tounsi Ahmed » ; renvoie les événements mis à jour. */
    List<Event> calculCout();

    /** Événement + participants + logistique (deux appels Feign). */
    EventDetailsDTO details(String idEvent);
}
