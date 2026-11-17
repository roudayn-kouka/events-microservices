package tn.fst.events.statistics.projections;

import java.util.HashSet;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.springframework.stereotype.Component;
import tn.fst.events.api.event.EventEvents.EventCostUpdated;
import tn.fst.events.api.event.EventEvents.EventCreated;
import tn.fst.events.api.event.EventEvents.EventDeleted;
import tn.fst.events.api.event.EventEvents.EventUpdated;
import tn.fst.events.api.event.EventEvents.LogistiqueAffectee;
import tn.fst.events.api.event.EventEvents.ParticipantAffecte;
import tn.fst.events.api.feedback.FeedbackEvents.FeedbackDeleted;
import tn.fst.events.api.feedback.FeedbackEvents.FeedbackSubmitted;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsCreated;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsDeleted;
import tn.fst.events.api.logistics.LogisticsEvents.LogisticsUpdated;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantCreated;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantDeleted;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantUpdated;
import tn.fst.events.statistics.projections.ProjectionModel.StatsAvis;
import tn.fst.events.statistics.projections.StatsAvisRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsEvenement;
import tn.fst.events.statistics.projections.StatsEvenementRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsLogistique;
import tn.fst.events.statistics.projections.StatsLogistiqueRepository;
import tn.fst.events.statistics.projections.ProjectionModel.StatsParticipant;
import tn.fst.events.statistics.projections.StatsParticipantRepository;

/**
 * ===== CÔTÉ REQUÊTES CQRS (mon module) =====
 * Consomme les événements des quatre services d'écriture et tient à jour db_stats. Processeur
 * « statistiques » en mode tracking : il lit le flux d'Axon Server à son rythme et enregistre sa
 * position (token_entry, dans db_stats). Conséquences :
 * <ul>
 *   <li>les statistiques se lisent localement, même si les services d'écriture sont arrêtés ;</li>
 *   <li>cohérence à terme : une écriture apparaît dans les statistiques après un court délai ;</li>
 *   <li>replay : remettre le jeton à zéro (@ResetHandler vide les tables) reconstruit tout.</li>
 * </ul>
 * Chaque handler est idempotent (« upsert ») : rejouer un événement donne le même état.
 */
@Slf4j
@Component
@ProcessingGroup(StatistiquesProjection.GROUPE)
@RequiredArgsConstructor
public class StatistiquesProjection {

    public static final String GROUPE = "statistiques";

    private final StatsEvenementRepository evenements;
    private final StatsParticipantRepository participants;
    private final StatsLogistiqueRepository logistique;
    private final StatsAvisRepository avis;

    // ------------------------------------------------------------------ event-service (agrégat)

    @EventHandler
    public void on(EventCreated e) {
        StatsEvenement s = evenements.findById(e.idEvent()).orElseGet(StatsEvenement::new);
        s.setIdEvent(e.idEvent());
        s.setDescription(e.description());
        s.setDateDebut(e.dateDebut());
        s.setDateFin(e.dateFin());
        s.setParticipantIds(new HashSet<>(e.participantIds()));
        evenements.save(s);
    }

    @EventHandler
    public void on(EventUpdated e) {
        modifier(e.idEvent(), s -> {
            s.setDescription(e.description());
            s.setDateDebut(e.dateDebut());
            s.setDateFin(e.dateFin());
        });
    }

    @EventHandler
    public void on(EventDeleted e) {
        evenements.deleteById(e.idEvent());
    }

    @EventHandler
    public void on(ParticipantAffecte e) {
        modifier(e.idEvent(), s -> s.getParticipantIds().add(e.idPart()));
    }

    @EventHandler
    public void on(LogistiqueAffectee e) {
        modifier(e.idEvent(), s -> s.getLogisticsIds().add(e.idLog()));
    }

    @EventHandler
    public void on(EventCostUpdated e) {
        modifier(e.idEvent(), s -> s.setCout(e.cout()));
    }

    // ------------------------------------------------------------------ participant-service

    @EventHandler
    public void on(ParticipantCreated e) {
        participant(e.idPart(), e.nom(), e.prenom(), e.tache());
    }

    @EventHandler
    public void on(ParticipantUpdated e) {
        participant(e.idPart(), e.nom(), e.prenom(), e.tache());
    }

    @EventHandler
    public void on(ParticipantDeleted e) {
        participants.deleteById(e.idPart());
    }

    // ------------------------------------------------------------------ logistics-service

    @EventHandler
    public void on(LogisticsCreated e) {
        logistique(e.idLog(), e.description(), e.reserve(), e.prixUnit(), e.quantite());
    }

    @EventHandler
    public void on(LogisticsUpdated e) {
        logistique(e.idLog(), e.description(), e.reserve(), e.prixUnit(), e.quantite());
    }

    @EventHandler
    public void on(LogisticsDeleted e) {
        logistique.deleteById(e.idLog());
    }

    // ------------------------------------------------------------------ feedback-service

    @EventHandler
    public void on(FeedbackSubmitted e) {
        StatsAvis a = new StatsAvis();
        a.setId(e.id());
        a.setEventId(e.eventId());
        a.setParticipantId(e.participantId());
        a.setNote(e.note());
        a.setDate(e.date());
        avis.save(a);
    }

    @EventHandler
    public void on(FeedbackDeleted e) {
        avis.deleteById(e.id());
    }

    // ------------------------------------------------------------------ replay

    /** Appelé par Axon avant un replay : la projection repart de zéro. */
    @ResetHandler
    public void reinitialiser() {
        log.info("Replay de la projection « {} » : tables de db_stats vidées", GROUPE);
        avis.deleteAllInBatch();
        logistique.deleteAllInBatch();
        participants.deleteAllInBatch();
        evenements.deleteAll(); // deleteAll (et non InBatch) : vide aussi les tables d'ID
    }

    // ------------------------------------------------------------------ outils

    /** Événement inconnu (supprimé, ou créé avant le CQRS) : ignoré, la projection reste cohérente. */
    private void modifier(String idEvent, Consumer<StatsEvenement> changement) {
        evenements.findById(idEvent).ifPresent(s -> {
            changement.accept(s);
            evenements.save(s);
        });
    }

    private void participant(Integer idPart, String nom, String prenom, String tache) {
        StatsParticipant p = new StatsParticipant();
        p.setIdPart(idPart);
        p.setNom(nom);
        p.setPrenom(prenom);
        p.setTache(tache);
        participants.save(p);
    }

    private void logistique(int idLog, String description, boolean reserve, float prixUnit, int quantite) {
        StatsLogistique l = new StatsLogistique();
        l.setIdLog(idLog);
        l.setDescription(description);
        l.setReserve(reserve);
        l.setPrixUnit(prixUnit);
        l.setQuantite(quantite);
        logistique.save(l);
    }
}
