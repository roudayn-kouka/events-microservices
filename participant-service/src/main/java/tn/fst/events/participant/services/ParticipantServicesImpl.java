package tn.fst.events.participant.services;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantCreated;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantDeleted;
import tn.fst.events.api.participant.ParticipantEvents.ParticipantUpdated;
import tn.fst.events.participant.entities.Participant;
import tn.fst.events.participant.entities.Tache;
import tn.fst.events.participant.repositories.ParticipantRepository;

/**
 * Logique reprise telle quelle de feature/participant, plus la lecture par lot et la recherche.
 * Phase 3 (CQRS) : chaque écriture publie son événement dans Axon (lu par statistics-service).
 * La publication a lieu dans la transaction, avant le commit : si Axon Server la refuse, l'écriture
 * en base est annulée (pas de participant sans événement).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ParticipantServicesImpl implements IParticipantServices {

    private final ParticipantRepository participantRepository;
    private final EventGateway eventGateway;

    @Override
    @Transactional
    public Participant addParticipant(Participant participant) {
        Participant cree = participantRepository.save(participant);
        eventGateway.publish(new ParticipantCreated(cree.getIdPart(), cree.getNom(), cree.getPrenom(), nom(cree.getTache())));
        return cree;
    }

    @Override
    public List<Participant> getAllParticipants() {
        return participantRepository.findAll();
    }

    @Override
    public Participant getParticipantById(int idPart) {
        return participantRepository.findById(idPart)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participant not found with id " + idPart));
    }

    @Override
    @Transactional
    public Participant updateParticipant(int idPart, Participant participant) {
        Participant existing = getParticipantById(idPart);
        existing.setNom(participant.getNom());
        existing.setPrenom(participant.getPrenom());
        existing.setTache(participant.getTache());
        return publierModification(participantRepository.save(existing));
    }

    @Override
    @Transactional
    public void deleteParticipant(int idPart) {
        Participant existing = getParticipantById(idPart);
        participantRepository.delete(existing);
        eventGateway.publish(new ParticipantDeleted(idPart));
        log.info("Participant {} deleted", idPart);
    }

    @Override
    @Transactional
    public Participant changeTache(int idPart, Tache tache) {
        Participant existing = getParticipantById(idPart);
        existing.setTache(tache);
        return publierModification(participantRepository.save(existing));
    }

    @Override
    public List<Participant> getParticipantsByIds(Collection<Integer> ids) {
        return ids == null || ids.isEmpty() ? List.of() : participantRepository.findByIdPartIn(ids);
    }

    @Override
    public List<Participant> search(String nom, String prenom, Tache tache) {
        return participantRepository.findByNomAndPrenomAndTache(nom, prenom, tache);
    }

    private Participant publierModification(Participant p) {
        eventGateway.publish(new ParticipantUpdated(p.getIdPart(), p.getNom(), p.getPrenom(), nom(p.getTache())));
        return p;
    }

    private static String nom(Tache tache) {
        return tache == null ? null : tache.name();
    }
}
