package tn.fst.events.event.dto;

import java.util.HashSet;
import java.util.List;
import tn.fst.events.event.entities.Event;

/** Conversion entité ↔ DTO (partie Event du Mapper de feature/participant, avec des ID). */
public final class Mapper {

    private Mapper() {
    }

    public static EventDTO toDto(Event e) {
        if (e == null) {
            return null;
        }
        return new EventDTO(e.getIdEvent(), e.getDescription(), e.getDateDebut(), e.getDateFin(), e.getCout(),
                e.getParticipantIds(), e.getLogisticsIds());
    }

    public static List<EventDTO> toDtos(List<Event> list) {
        return list == null ? null : list.stream().map(Mapper::toDto).toList();
    }

    /** Toujours un ensemble (jamais null), comme le Mapper d'origine pour les participants. */
    public static Event toEntity(EventRequest r) {
        Event e = new Event();
        e.setDescription(r.getDescription());
        e.setDateDebut(r.getDateDebut());
        e.setDateFin(r.getDateFin());
        e.setParticipantIds(r.getParticipantIds() == null ? new HashSet<>() : new HashSet<>(r.getParticipantIds()));
        e.setLogisticsIds(new HashSet<>());
        return e;
    }
}
