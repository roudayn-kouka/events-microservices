package tn.fst.events.statistics.sources;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.clients.FeedbackAverageDTO;
import tn.fst.events.statistics.clients.LogisticsDTO;
import tn.fst.events.statistics.clients.ParticipantDTO;
import tn.fst.events.statistics.projections.StatsAvisRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsEvenement;
import tn.fst.events.statistics.projections.StatsEvenementRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsLogistique;
import tn.fst.events.statistics.projections.StatsLogistiqueRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsParticipant;
import tn.fst.events.statistics.projections.StatsParticipantRepository;

/**
 * ===== VERSION APRÈS CQRS (phase 3, source par défaut) =====
 * Lit les projections locales de db_stats : 0 appel réseau, et les statistiques répondent même si
 * event, participant, logistics ou feedback-service sont arrêtés. Mêmes DTO que FeignStatsSource :
 * le calcul (EventStatsServiceImpl) ne voit pas la différence.
 * stats.source=feign rétablit la version AVANT CQRS (comparaison en démonstration).
 */
@Component
@ConditionalOnProperty(name = "stats.source", havingValue = "projections", matchIfMissing = true)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectionStatsSource implements StatsSource {

    public static final String NOM = "projections-cqrs";

    private final StatsEvenementRepository evenements;
    private final StatsParticipantRepository participants;
    private final StatsLogistiqueRepository logistique;
    private final StatsAvisRepository avis;

    @Override
    public String nom() {
        return NOM;
    }

    @Override
    public List<EventDTO> evenements() {
        return evenements.findAll().stream().map(ProjectionStatsSource::dto).toList();
    }

    @Override
    public Optional<EventDTO> evenement(String idEvent) {
        return evenements.findById(idEvent).map(ProjectionStatsSource::dto);
    }

    @Override
    public List<ParticipantDTO> participants() {
        return participants.findAll().stream().map(ProjectionStatsSource::dto).toList();
    }

    @Override
    public List<ParticipantDTO> participants(Collection<Integer> ids) {
        return ids.isEmpty() ? List.of() : participants.findAllById(ids).stream().map(ProjectionStatsSource::dto).toList();
    }

    @Override
    public List<LogisticsDTO> logistique() {
        return logistique.findAll().stream().map(ProjectionStatsSource::dto).toList();
    }

    @Override
    public List<LogisticsDTO> logistique(Collection<Integer> ids) {
        return ids.isEmpty() ? List.of() : logistique.findAllById(ids).stream().map(ProjectionStatsSource::dto).toList();
    }

    @Override
    public Map<String, FeedbackAverageDTO> moyennes() {
        return avis.moyennes().stream()
                .collect(Collectors.toMap(FeedbackAverageDTO::eventId, Function.identity(), (a, b) -> a));
    }

    private static EventDTO dto(StatsEvenement s) {
        return new EventDTO(s.getIdEvent(), s.getDescription(), s.getDateDebut(), s.getDateFin(), s.getCout(),
                Set.copyOf(s.getParticipantIds()), Set.copyOf(s.getLogisticsIds()));
    }

    private static ParticipantDTO dto(StatsParticipant p) {
        return new ParticipantDTO(p.getIdPart(), p.getNom(), p.getPrenom(), p.getTache());
    }

    private static LogisticsDTO dto(StatsLogistique l) {
        return new LogisticsDTO(l.getIdLog(), l.getDescription(), l.isReserve(), l.getPrixUnit(), l.getQuantite());
    }
}
