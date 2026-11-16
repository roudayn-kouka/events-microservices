package tn.fst.events.statistics.projections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.sources.StatsSource;

/**
 * Replay (EVT-50) : remise à zéro du jeton du processeur « statistiques ». Axon appelle alors le
 * @ResetHandler (tables vidées) puis relit TOUT l'event store depuis le début : db_stats est
 * reconstruite à l'identique. Utile après une correction de la projection, ou pour la démo.
 * Phase 5 (2 instances) : le jeton est partagé dans db_stats et une seule instance détient le
 * segment. Un replay demandé à l'autre instance est refusé (409, message explicite) ;
 * scripts/replay.sh (make replay) arrête statistics-service-2 puis rejoue sur l'instance 1.
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
        try {
            processeur.resetTokens();
        } catch (UnableToClaimTokenException e) {
            processeur.start(); // ne rien laisser arrêté sur cette instance
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Replay impossible depuis cette instance : le jeton "
                    + "du processeur est détenu par une autre instance. Arrêter d'abord le processeur sur toutes les "
                    + "instances (make replay). Détail : " + e.getMessage());
        }
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
