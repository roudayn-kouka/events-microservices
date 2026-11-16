package tn.fst.events.statistics.projections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.sources.StatsSource;

/**
 * Replay (EVT-50) : remise à zéro du jeton du processeur « statistiques ». Axon appelle alors le
 * @ResetHandler (tables vidées) puis relit TOUT l'event store depuis le début : db_stats est
 * reconstruite à l'identique. Utile après une correction de la projection, ou pour la démo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionAdmin {

    private final EventProcessingConfiguration processing;
    private final StatsSource source;
    private final StatsEvenementRepository evenements;
    private final StatsParticipantRepository participants;
    private final StatsLogistiqueRepository logistique;
    private final StatsAvisRepository avis;

    public record EtatProjection(String processeur, boolean enCours, boolean replay, String source,
                                 long evenements, long participants, long logistiques, long avis) {
    }

    public EtatProjection rejouer() {
        StreamingEventProcessor processeur = processeur();
        log.info("Replay demandé pour « {} »", StatistiquesProjection.GROUPE);
        processeur.shutDown();
        processeur.resetTokens();
        processeur.start();
        return etat();
    }

    public EtatProjection etat() {
        StreamingEventProcessor processeur = processeur();
        return new EtatProjection(StatistiquesProjection.GROUPE, processeur.isRunning(), processeur.isReplaying(),
                source.nom(), evenements.count(), participants.count(), logistique.count(), avis.count());
    }

    private StreamingEventProcessor processeur() {
        return processing.eventProcessor(StatistiquesProjection.GROUPE, StreamingEventProcessor.class)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Le processeur « " + StatistiquesProjection.GROUPE + " » n'est pas un processeur à jetons"));
    }
}
