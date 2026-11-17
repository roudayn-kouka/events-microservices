package tn.fst.events.logistics.dto;

import java.util.List;
import tn.fst.events.logistics.entities.Logistics;

/** Conversion entité ↔ DTO (partie Logistics du Mapper de feature/participant). */
public final class Mapper {

    private Mapper() {
    }

    public static LogisticsDTO toDto(Logistics l) {
        if (l == null) {
            return null;
        }
        return new LogisticsDTO(l.getIdLog(), l.getDescription(), l.isReserve(), l.getPrixUnit(), l.getQuantite());
    }

    public static Logistics toEntity(LogisticsDTO d) {
        return new Logistics(d.getIdLog(), d.getDescription(), d.isReserve(), d.getPrixUnit(), d.getQuantite());
    }

    public static List<LogisticsDTO> toLogisticsDtos(List<Logistics> list) {
        return list == null ? null : list.stream().map(Mapper::toDto).toList();
    }
}
