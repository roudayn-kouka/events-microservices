package tn.fst.events.event.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.common.web.CommonWebAutoConfiguration;
import tn.fst.events.event.clients.LogisticsDTO;
import tn.fst.events.event.clients.ParticipantDTO;
import tn.fst.events.event.dto.EventDetailsDTO;
import tn.fst.events.event.entities.Event;
import tn.fst.events.event.services.IEventServices;

/**
 * Repris de DEUX variantes d'EventRestControllerMockTest (feature/participant et
 * feature/event-statistics, 5 tests chacune, AVEC Mockito), fusionnées en une classe :
 * - supprimés (×2) : addParticipant / testAddParticipant (POST /event/addPart : la création d'un
 *   participant appartient à participant-service) ;
 * - fusionnés (4 + 4 → 4) : chaque test garde les vérifications des deux variantes (JSON
 *   renvoyé + argument transmis au service) ;
 * - adaptés : chemins REST (/events…) ; 201 à la création ; JSON avec participantIds et
 *   logisticsIds au lieu des objets imbriqués ; @WebMvcTest + @MockitoBean au lieu du
 *   standaloneSetup JUnit 4 ; les dates ISO sont converties par @DateTimeFormat.
 * 9 tests ajoutés : lecture, 404 au format ApiError, validation 400, mise à jour, suppression,
 * affectations par identifiant, vue détaillée, recalcul du coût.
 */
@WebMvcTest(EventRestController.class)
@ImportAutoConfiguration(CommonWebAutoConfiguration.class)
class EventRestControllerMockTest {

    private static final String ID = "3f1c2a8e-6b4d-4e2a-9c1f-0a7b5d9e2c11";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IEventServices eventServices;

    private Event event() {
        Event e = new Event();
        e.setIdEvent(ID);
        e.setDescription("Gala");
        e.setDateDebut(LocalDate.of(2026, 5, 1));
        e.setDateFin(LocalDate.of(2026, 5, 2));
        e.setParticipantIds(new HashSet<>(Set.of(1)));
        e.setLogisticsIds(new HashSet<>(Set.of(5)));
        return e;
    }

    private static final String EVENT_JSON =
            "{\"description\":\"Gala\",\"dateDebut\":\"2026-05-01\",\"dateFin\":\"2026-05-02\",\"participantIds\":[1]}";

    private static final String LOGISTICS_JSON =
            "{\"description\":\"Tables\",\"reserve\":true,\"prixUnit\":5,\"quantite\":2}";

    @Test
    void addEvent() throws Exception {
        when(eventServices.addAffectEvenParticipant(any(Event.class))).thenReturn(event());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEvent").value(ID))
                .andExpect(jsonPath("$.description").value("Gala"))
                .andExpect(jsonPath("$.participantIds[0]").value(1))
                .andExpect(jsonPath("$.logisticsIds[0]").value(5));
    }

    @Test
    void addEventForParticipant() throws Exception {
        when(eventServices.addAffectEvenParticipant(any(Event.class), eq(7))).thenReturn(event());

        mockMvc.perform(post("/events/participant/7").contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEvent").value(ID))
                .andExpect(jsonPath("$.description").value("Gala"));

        verify(eventServices).addAffectEvenParticipant(any(Event.class), eq(7));
    }

    @Test
    void addAffectLog() throws Exception {
        when(eventServices.addAffectLog(any(LogisticsDTO.class), eq("Gala")))
                .thenReturn(new LogisticsDTO(3, "Tables", true, 5f, 2));

        mockMvc.perform(put("/events/by-description/Gala/logistics")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGISTICS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLog").value(3))
                .andExpect(jsonPath("$.description").value("Tables"));
    }

    @Test
    void getLogs() throws Exception {
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 12, 31);
        when(eventServices.getLogisticsDates(debut, fin))
                .thenReturn(Collections.singletonList(new LogisticsDTO(3, "Tables", true, 5f, 2)));

        mockMvc.perform(get("/events/logistics/2026-01-01/2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Tables"));
    }

    // ---------------------------------------------------------------- ajoutés

    @Test
    void getEventsEtGetEvent() throws Exception {
        when(eventServices.retrieveAllEvents()).thenReturn(List.of(event()));
        when(eventServices.retrieveEvent(ID)).thenReturn(event());

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEvent").value(ID));
        mockMvc.perform(get("/events/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateFin").value("2026-05-02"));
    }

    @Test
    void getEventInconnuRepond404AuFormatApiError() throws Exception {
        when(eventServices.retrieveEvent("inconnu")).thenReturn(null);

        mockMvc.perform(get("/events/inconnu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Event not found with id inconnu"))
                .andExpect(jsonPath("$.path").value("/events/inconnu"));
    }

    @Test
    void addEventSansDescriptionNiDatesRepond400() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content("{\"participantIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Données invalides"))
                .andExpect(jsonPath("$.details.length()").value(3));

        verify(eventServices, never()).addAffectEvenParticipant(any(Event.class));
    }

    @Test
    void addEventAvecParticipantInconnuPropageLe404DuService() throws Exception {
        when(eventServices.addAffectEvenParticipant(any(Event.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found with id [42]"));

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(EVENT_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Participant not found with id [42]"));
    }

    @Test
    void updateEventModifieDescriptionEtDates() throws Exception {
        when(eventServices.retrieveEvent(ID)).thenReturn(event());
        when(eventServices.updateEvent(any(Event.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(put("/events/" + ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Gala 2026\",\"dateDebut\":\"2026-06-01\",\"dateFin\":\"2026-06-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Gala 2026"))
                .andExpect(jsonPath("$.participantIds[0]").value(1)); // inscriptions conservées
    }

    @Test
    void deleteEventRepond204PuisLecture404() throws Exception {
        when(eventServices.retrieveEvent(ID)).thenReturn(event());

        mockMvc.perform(delete("/events/" + ID)).andExpect(status().isNoContent());
        verify(eventServices).deleteEvent(ID);

        mockMvc.perform(delete("/events/inconnu")).andExpect(status().isNotFound());
        verify(eventServices, never()).deleteEvent("inconnu");
    }

    @Test
    void affectationsParIdentifiant() throws Exception {
        when(eventServices.affecterParticipant(ID, 2)).thenReturn(event());
        when(eventServices.affecterLogistique(eq(ID), any(LogisticsDTO.class)))
                .thenReturn(new LogisticsDTO(8, "Tables", true, 5f, 2));

        mockMvc.perform(post("/events/" + ID + "/participants/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEvent").value(ID));
        mockMvc.perform(post("/events/" + ID + "/logistics").contentType(MediaType.APPLICATION_JSON)
                        .content(LOGISTICS_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLog").value(8));
    }

    @Test
    void detailsExposeLesObjetsLusParFeign() throws Exception {
        when(eventServices.details(ID)).thenReturn(new EventDetailsDTO(ID, "Gala", LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 2), 10f, List.of(new ParticipantDTO(1, "Tounsi", "Ahmed", "ORGANISATEUR")),
                List.of(new LogisticsDTO(5, "Chaises", true, 2.5f, 4))));

        mockMvc.perform(get("/events/" + ID + "/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].nom").value("Tounsi"))
                .andExpect(jsonPath("$.logistics[0].description").value("Chaises"));
    }

    @Test
    void recalculDuCoutALaDemande() throws Exception {
        Event e = event();
        e.setCout(490f);
        when(eventServices.calculCout()).thenReturn(List.of(e));

        mockMvc.perform(post("/events/cout/recalcul"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Instance-Id", "event-service")) // sans Eureka : nom de l'application
                .andExpect(jsonPath("$[0].cout").value(490.0));
    }
}
