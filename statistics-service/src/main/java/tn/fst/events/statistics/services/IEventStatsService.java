package tn.fst.events.statistics.services;

import java.time.LocalDate;
import java.util.List;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;

/** Repris de feature/event-statistics ; l'identifiant d'événement devient un UUID (String). */
public interface IEventStatsService {

    /** Statistiques d'un événement donné (404 s'il n'existe pas). */
    EventStatsDTO getEventStats(String idEvent);

    /** Statistiques de tous les événements, triés par date de début. */
    List<EventStatsDTO> getAllEventsStats();

    /** Statistiques des événements à venir (dateDebut >= aujourd'hui), triés par date. */
    List<EventStatsDTO> getUpcomingEventsStats();

    /** Statistiques des événements dont la date de début est entre d1 et d2 (inclus). */
    List<EventStatsDTO> getEventsStatsBetween(LocalDate d1, LocalDate d2);

    /** Indicateurs globaux pour le dashboard. */
    GlobalStatsDTO getGlobalStats();

    /** Nouveau : nom de la source des données (« feign-avant-cqrs » à la phase 2). */
    String source();
}
