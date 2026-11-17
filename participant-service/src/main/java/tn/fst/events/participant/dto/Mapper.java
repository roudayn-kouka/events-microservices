package tn.fst.events.participant.dto;

import java.util.List;
import tn.fst.events.participant.entities.Participant;

/**
 * Conversion entité ↔ DTO (repris du Mapper de feature/participant, partie Participant).
 * Les parties Event et Logistics du Mapper d'origine sont reprises dans event-service et
 * logistics-service.
 */
public final class Mapper {

    private Mapper() {
    }

    public static ParticipantDTO toDto(Participant p) {
        if (p == null) {
            return null;
        }
        return new ParticipantDTO(p.getIdPart(), p.getNom(), p.getPrenom(), p.getTache());
    }

    public static List<ParticipantDTO> toDtos(List<Participant> list) {
        return list == null ? null : list.stream().map(Mapper::toDto).toList();
    }

    public static Participant toEntity(ParticipantDTO d) {
        Participant p = new Participant();
        p.setIdPart(d.getIdPart());
        p.setNom(d.getNom());
        p.setPrenom(d.getPrenom());
        p.setTache(d.getTache());
        return p;
    }
}
