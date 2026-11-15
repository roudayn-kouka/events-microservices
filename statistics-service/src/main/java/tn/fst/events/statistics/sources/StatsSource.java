package tn.fst.events.statistics.sources;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tn.fst.events.statistics.clients.EventDTO;
import tn.fst.events.statistics.clients.FeedbackAverageDTO;
import tn.fst.events.statistics.clients.LogisticsDTO;
import tn.fst.events.statistics.clients.ParticipantDTO;

/**
 * D'où viennent les données des statistiques. C'est le point de bascule avant / après CQRS :
 * <ul>
 *   <li>AVANT CQRS (phase 2) : {@link FeignStatsSource}, lecture des quatre services à chaque requête ;</li>
 *   <li>APRÈS CQRS (phase 6) : lecture des projections locales de db_stats, alimentées par Axon.</li>
 * </ul>
 * Le calcul des statistiques (EventStatsServiceImpl) ne dépend que de cette interface.
 */
public interface StatsSource {

    /** Nom de la source, renvoyé dans l'en-tête X-Stats-Source (comparaison avant / après). */
    String nom();

    List<EventDTO> evenements();

    /** Vide si l'événement n'existe pas. */
    Optional<EventDTO> evenement(String idEvent);

    List<ParticipantDTO> participants();

    List<ParticipantDTO> participants(Collection<Integer> ids);

    List<LogisticsDTO> logistique();

    List<LogisticsDTO> logistique(Collection<Integer> ids);

    /** Moyenne et nombre d'avis, par identifiant d'événement (seuls les événements notés). */
    Map<String, FeedbackAverageDTO> moyennes();
}
