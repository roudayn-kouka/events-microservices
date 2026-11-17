package tn.fst.events.statistics.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.clients.FeedbackAverageDTO;
import tn.fst.events.statistics.clients.LogisticsDTO;
import tn.fst.events.statistics.clients.ParticipantDTO;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.dto.Tache;
import tn.fst.events.statistics.sources.StatsSource;

/**
 * Repris de feature/event-statistics (mon module). Les règles de calcul sont inchangées : durée
 * inclusive, répartition par tâche avec toutes les tâches à 0, coût et taux de la logistique
 * RÉSERVÉE, tri par date de début, intervalle invalide → 400.
 * Changement : les trois repositories (une seule base) sont remplacés par une {@link StatsSource}.
 * À la phase 2, cette source est Feign (VERSION AVANT CQRS) ; les objets liés sont lus en lots.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventStatsServiceImpl implements IEventStatsService {

    StatsSource source;

    @Override
    public EventStatsDTO getEventStats(String idEvent) {
        EventDTO event = source.evenement(idEvent)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found with id " + idEvent));
        EventStatsDTO stats = statistiques(List.of(event)).get(0);
        log.info("Stats de l'event {} : {} participants, coût logistique réservée = {}",
                event.description(), stats.getNbParticipants(), stats.getCoutLogistiqueReservee());
        return stats;
    }

    @Override
    public List<EventStatsDTO> getAllEventsStats() {
        return statistiques(source.evenements().stream()
                .sorted(Comparator.comparing(EventDTO::dateDebut, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
    }

    @Override
    public List<EventStatsDTO> getUpcomingEventsStats() {
        return statistiques(aVenir(source.evenements(), LocalDate.now()));
    }

    @Override
    public List<EventStatsDTO> getEventsStatsBetween(LocalDate d1, LocalDate d2) {
        if (d1 == null || d2 == null || d1.isAfter(d2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intervalle de dates invalide : " + d1 + " -> " + d2);
        }
        return statistiques(source.evenements().stream()
                .filter(e -> e.dateDebut() != null && !e.dateDebut().isBefore(d1) && !e.dateDebut().isAfter(d2))
                .sorted(Comparator.comparing(EventDTO::dateDebut))
                .toList());
    }

    @Override
    public GlobalStatsDTO getGlobalStats() {
        List<EventDTO> events = source.evenements();
        List<ParticipantDTO> participants = source.participants();
        List<LogisticsDTO> logistics = source.logistique();
        Collection<FeedbackAverageDTO> moyennes = source.moyennes().values();

        List<LogisticsDTO> reservees = logistics.stream().filter(LogisticsDTO::reserve).toList();
        float cout = coutReserve(reservees);
        float taux = logistics.isEmpty() ? 0f : (reservees.size() * 100f) / logistics.size();
        long nbFeedbacks = moyennes.stream().mapToLong(FeedbackAverageDTO::totalFeedbacks).sum();
        double somme = moyennes.stream().mapToDouble(m -> m.averageRating() * m.totalFeedbacks()).sum();

        return GlobalStatsDTO.builder()
                .nbEvents(events.size())
                .nbEventsAVenir(aVenir(events, LocalDate.now()).size())
                .nbParticipants(participants.size())
                .nbLogistics(logistics.size())
                .nbLogisticsReservees(reservees.size())
                .tauxReservation(taux)
                .coutTotalLogistiqueReservee(cout)
                .participantsParTache(countByTache(participants))
                .nbFeedbacks(nbFeedbacks)
                .noteMoyenne(nbFeedbacks == 0 ? 0.0 : arrondi(somme / nbFeedbacks))
                .build();
    }

    @Override
    public String source() {
        return source.nom();
    }

    /**
     * Statistiques d'une liste d'événements : un lot de participants, un lot de logistique et les
     * moyennes des avis sont lus UNE fois pour toute la liste (et non une fois par événement).
     */
    List<EventStatsDTO> statistiques(List<EventDTO> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        Map<Integer, ParticipantDTO> participants = source.participants(ids(events, EventDTO::participantIds)).stream()
                .collect(Collectors.toMap(ParticipantDTO::idPart, Function.identity(), (a, b) -> a));
        Map<Integer, LogisticsDTO> logistics = source.logistique(ids(events, EventDTO::logisticsIds)).stream()
                .collect(Collectors.toMap(LogisticsDTO::idLog, Function.identity(), (a, b) -> a));
        Map<String, FeedbackAverageDTO> moyennes = source.moyennes();
        return events.stream().map(e -> buildStats(e, participants, logistics, moyennes.get(e.idEvent()))).toList();
    }

    /** Construit le DTO statistique d'un événement (repris de buildStats, avec des ID). */
    EventStatsDTO buildStats(EventDTO event, Map<Integer, ParticipantDTO> participantsParId,
                             Map<Integer, LogisticsDTO> logisticsParId, FeedbackAverageDTO moyenne) {
        // ID orphelin (participant supprimé dans participant-service) : ignoré, voir THEORIE Q10
        List<ParticipantDTO> participants = lire(event.participantIds(), participantsParId);
        List<LogisticsDTO> logistics = lire(event.logisticsIds(), logisticsParId);

        List<LogisticsDTO> reservees = logistics.stream().filter(LogisticsDTO::reserve).toList();

        long duree = 0;
        if (event.dateDebut() != null && event.dateFin() != null) {
            duree = ChronoUnit.DAYS.between(event.dateDebut(), event.dateFin()) + 1;
        }

        return EventStatsDTO.builder()
                .idEvent(event.idEvent())
                .description(event.description())
                .dateDebut(event.dateDebut())
                .dateFin(event.dateFin())
                .dureeJours(duree)
                .nbParticipants(participants.size())
                .participantsParTache(countByTache(participants))
                .nbLogistics(logistics.size())
                .nbLogisticsReservees(reservees.size())
                .coutLogistiqueReservee(coutReserve(reservees))
                .averageRating(moyenne == null ? 0.0 : arrondi(moyenne.averageRating()))
                .nbFeedbacks(moyenne == null ? 0 : moyenne.totalFeedbacks())
                .build();
    }

    // ------------------------------------------------------------------ outils

    private static List<EventDTO> aVenir(List<EventDTO> events, LocalDate aujourdhui) {
        return events.stream()
                .filter(e -> e.dateDebut() != null && !e.dateDebut().isBefore(aujourdhui))
                .sorted(Comparator.comparing(EventDTO::dateDebut))
                .toList();
    }

    private static Set<Integer> ids(List<EventDTO> events, Function<EventDTO, Set<Integer>> champ) {
        return events.stream().map(champ).filter(Objects::nonNull).flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static <T> List<T> lire(Set<Integer> ids, Map<Integer, T> parId) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(parId::get).filter(Objects::nonNull).toList();
    }

    /** Note sur 5 arrondie à 2 décimales (MySQL renvoie AVG d'un entier avec 4 décimales : 4,3333). */
    static double arrondi(double note) {
        return Math.round(note * 100) / 100.0;
    }

    private static float coutReserve(List<LogisticsDTO> reservees) {
        float cout = 0f;
        for (LogisticsDTO l : reservees) {
            cout += l.prixUnit() * l.quantite();
        }
        return cout;
    }

    /** Compte les participants par tâche ; toutes les tâches apparaissent, même à 0. */
    private static Map<Tache, Long> countByTache(Collection<ParticipantDTO> participants) {
        Map<Tache, Long> parTache = new EnumMap<>(Tache.class);
        for (Tache t : Tache.values()) {
            parTache.put(t, 0L);
        }
        for (ParticipantDTO p : participants) {
            tache(p.tache()).ifPresent(t -> parTache.merge(t, 1L, Long::sum));
        }
        return parTache;
    }

    /** Tâche inconnue (contrat JSON qui évolue) : ignorée plutôt que de faire échouer les statistiques. */
    private static java.util.Optional<Tache> tache(String nom) {
        if (nom == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Tache.valueOf(nom));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
