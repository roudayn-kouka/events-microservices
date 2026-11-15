package tn.fst.events.event.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.dto.EventRequest;
import tn.fst.events.event.dto.LogisticsRequest;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.services.IEventServices;

/**
 * Repris de feature/feedback (5 tests AVEC Mockito, appel direct des méthodes du contrôleur).
 * 4 adaptés, 1 supprimé : addParticipantMapsRequestAndReturnsServiceResult (création d'un
 * participant → participant-service).
 * Adaptations : EventWriteRequest / LogisticsWriteRequest deviennent EventRequest /
 * LogisticsRequest ; le contrôleur renvoie un EventDTO (et non plus l'entité) : assertSame sur
 * l'événement devient une comparaison de champs.
 */
@ExtendWith(MockitoExtension.class)
class EventRestControllerMockitoTest {

    @Mock
    private IEventServices eventServices;

    @InjectMocks
    private EventRestController controller;

    private static EventRequest request() {
        EventRequest request = new EventRequest();
        request.setDescription("Conference");
        return request;
    }

    private static Event expected() {
        Event expected = new Event();
        expected.setIdEvent("e-14");
        expected.setDescription("Conference");
        return expected;
    }

    @Test
    void addEventForParticipantMapsRequestAndPassesPathId() {
        when(eventServices.addAffectEvenParticipant(any(Event.class), eq(14))).thenReturn(expected());

        assertEquals("e-14", controller.addEventPart(request(), 14).idEvent());
        verify(eventServices).addAffectEvenParticipant(any(Event.class), eq(14));
    }

    @Test
    void addEventMapsRequestAndReturnsServiceResult() {
        when(eventServices.addAffectEvenParticipant(any(Event.class))).thenReturn(expected());

        assertEquals("Conference", controller.addEvent(request()).description());
        verify(eventServices).addAffectEvenParticipant(any(Event.class));
    }

    @Test
    void addLogisticsMapsRequestAndPassesEventDescription() {
        LogisticsRequest request = new LogisticsRequest("Projector", true, 10f, 1);
        LogisticsDTO expected = new LogisticsDTO(4, "Projector", true, 10f, 1);
        when(eventServices.addAffectLog(any(LogisticsDTO.class), eq("Conference"))).thenReturn(expected);

        assertSame(expected, controller.addAffectLog(request, "Conference"));
        verify(eventServices).addAffectLog(eq(new LogisticsDTO(0, "Projector", true, 10f, 1)), eq("Conference"));
    }

    @Test
    void getLogisticsDatesPassesDateRangeToService() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        List<LogisticsDTO> expected = List.of(new LogisticsDTO(1, "Projector", true, 10f, 1));
        when(eventServices.getLogisticsDates(start, end)).thenReturn(expected);

        assertSame(expected, controller.getLogistiquesDates(start, end));
        verify(eventServices).getLogisticsDates(start, end);
    }
}
