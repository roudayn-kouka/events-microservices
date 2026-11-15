package tn.fst.events.participant.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import tn.fst.events.participant.entities.Participant;
import tn.fst.events.participant.entities.Tache;

/**
 * Repris de feature/participant (MapperTest, partie Participant). Le test d'origine vérifiait
 * aussi les « événements courts » du participant : cette relation n'existe plus ici. Les 2 autres
 * tests d'origine (Event, Logistics) sont repris dans event-service et logistics-service.
 */
class MapperTest {

    @Test
    void participantRoundTripKeepsFields() {
        Participant p = new Participant(1, "Tounsi", "Ahmed", Tache.SERVEUR);
        ParticipantDTO d = Mapper.toDto(p);
        assertEquals(1, d.getIdPart());
        assertEquals("Tounsi", d.getNom());
        assertEquals(Tache.SERVEUR, d.getTache());
        Participant back = Mapper.toEntity(d);
        assertEquals("Tounsi", back.getNom());
        assertEquals("Ahmed", back.getPrenom());
    }

    @Test
    void nullParticipantAndNullListStayNull() {
        assertNull(Mapper.toDto(null));
        assertNull(Mapper.toDtos(null));
    }
}
