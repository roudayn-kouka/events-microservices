package tn.fst.events.participant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.participant.entities.Participant;
import tn.fst.events.participant.entities.Tache;

/**
 * Repris de feature/participant (7 tests, sans Mockito : vrai repository sur H2 en mémoire,
 * annulation automatique après chaque test). Adaptation : getStatusCode(). 2 tests ajoutés.
 */
@DataJpaTest
@Import(ParticipantServicesImpl.class)
class ParticipantServicesImplIntegrationTest {

    @Autowired
    private ParticipantServicesImpl service;

    private Participant newParticipant(String nom, String prenom, Tache tache) {
        Participant p = new Participant();
        p.setNom(nom);
        p.setPrenom(prenom);
        p.setTache(tache);
        return p;
    }

    @Test
    void addParticipant_shouldGenerateId() {
        Participant saved = service.addParticipant(newParticipant("Tounsi", "Ahmed", Tache.ORGANISATEUR));
        assertTrue(saved.getIdPart() > 0);
        assertEquals("Tounsi", saved.getNom());
    }

    @Test
    void getAllParticipants_shouldReturnSavedOnes() {
        service.addParticipant(newParticipant("A", "A", Tache.INVITE));
        service.addParticipant(newParticipant("B", "B", Tache.SERVEUR));
        List<Participant> all = service.getAllParticipants();
        assertEquals(2, all.size());
    }

    @Test
    void getParticipantById_shouldReturnParticipant() {
        Participant saved = service.addParticipant(newParticipant("Tounsi", "Ahmed", Tache.INVITE));
        Participant found = service.getParticipantById(saved.getIdPart());
        assertEquals("Ahmed", found.getPrenom());
    }

    @Test
    void getParticipantById_unknownId_shouldThrow404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getParticipantById(9999));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void updateParticipant_shouldPersistChanges() {
        Participant saved = service.addParticipant(newParticipant("Old", "Name", Tache.INVITE));
        service.updateParticipant(saved.getIdPart(), newParticipant("New", "Person", Tache.ANIMATEUR));
        Participant reloaded = service.getParticipantById(saved.getIdPart());
        assertEquals("New", reloaded.getNom());
        assertEquals("Person", reloaded.getPrenom());
        assertEquals(Tache.ANIMATEUR, reloaded.getTache());
    }

    @Test
    void changeTache_shouldPersistNewTache() {
        Participant saved = service.addParticipant(newParticipant("Tounsi", "Ahmed", Tache.INVITE));
        service.changeTache(saved.getIdPart(), Tache.SERVEUR);
        assertEquals(Tache.SERVEUR, service.getParticipantById(saved.getIdPart()).getTache());
    }

    @Test
    void deleteParticipant_shouldRemoveIt() {
        Participant saved = service.addParticipant(newParticipant("Tounsi", "Ahmed", Tache.INVITE));
        int id = saved.getIdPart();
        service.deleteParticipant(id);
        assertThrows(ResponseStatusException.class, () -> service.getParticipantById(id));
    }

    // ---- Ajoutés : requêtes utilisées par Feign

    @Test
    void getParticipantsByIds_shouldReturnOnlyRequestedOnes() {
        Participant a = service.addParticipant(newParticipant("A", "A", Tache.INVITE));
        service.addParticipant(newParticipant("B", "B", Tache.SERVEUR));
        Participant c = service.addParticipant(newParticipant("C", "C", Tache.ANIMATEUR));
        List<Participant> found = service.getParticipantsByIds(List.of(a.getIdPart(), c.getIdPart()));
        assertEquals(2, found.size());
    }

    @Test
    void search_shouldMatchNomPrenomAndTache() {
        service.addParticipant(newParticipant("Tounsi", "Ahmed", Tache.ORGANISATEUR));
        service.addParticipant(newParticipant("Tounsi", "Ahmed", Tache.INVITE));
        assertEquals(1, service.search("Tounsi", "Ahmed", Tache.ORGANISATEUR).size());
    }
}
