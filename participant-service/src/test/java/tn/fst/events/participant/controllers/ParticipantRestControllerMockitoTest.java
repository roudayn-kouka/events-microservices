package tn.fst.events.participant.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.participant.entities.Participant;
import tn.fst.events.participant.entities.Tache;
import tn.fst.events.participant.services.IParticipantServices;

/**
 * Repris de feature/participant (8 tests, avec Mockito : couche web seule, service simulé).
 * Adaptations : chemins /participants (REST) au lieu de /participant/add, /all, /update/{id},
 * /delete/{id} ; 201 à la création, 204 à la suppression ; @MockitoBean (Boot 3.4+) au lieu de
 * @MockBean. 3 tests ajoutés (validation, lot d'ID, recherche).
 */
@WebMvcTest(ParticipantRestController.class)
class ParticipantRestControllerMockitoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IParticipantServices participantServices;

    private Participant buildParticipant(String nom, String prenom, Tache tache) {
        Participant p = new Participant();
        p.setNom(nom);
        p.setPrenom(prenom);
        p.setTache(tache);
        return p;
    }

    @Test
    void addParticipant_shouldReturnSavedParticipant() throws Exception {
        when(participantServices.addParticipant(any(Participant.class)))
                .thenReturn(buildParticipant("Tounsi", "Ahmed", Tache.ORGANISATEUR));
        mockMvc.perform(post("/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Tounsi\",\"prenom\":\"Ahmed\",\"tache\":\"ORGANISATEUR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Tounsi"))
                .andExpect(jsonPath("$.tache").value("ORGANISATEUR"));
        verify(participantServices).addParticipant(any(Participant.class));
    }

    @Test
    void getAll_shouldReturnJsonArray() throws Exception {
        when(participantServices.getAllParticipants()).thenReturn(Arrays.asList(
                buildParticipant("A", "A", Tache.INVITE),
                buildParticipant("B", "B", Tache.SERVEUR)));
        mockMvc.perform(get("/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nom").value("A"));
    }

    @Test
    void getById_shouldReturnParticipant() throws Exception {
        when(participantServices.getParticipantById(1))
                .thenReturn(buildParticipant("Tounsi", "Ahmed", Tache.INVITE));
        mockMvc.perform(get("/participants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Ahmed"));
    }

    @Test
    void getById_notFound_shouldReturn404() throws Exception {
        when(participantServices.getParticipantById(999))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        mockMvc.perform(get("/participants/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_shouldReturnUpdatedParticipant() throws Exception {
        when(participantServices.updateParticipant(eq(1), any(Participant.class)))
                .thenReturn(buildParticipant("Tounsi", "Ahmed", Tache.SERVEUR));
        mockMvc.perform(put("/participants/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Tounsi\",\"prenom\":\"Ahmed\",\"tache\":\"SERVEUR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tache").value("SERVEUR"));
    }

    @Test
    void changeTache_shouldCallServiceWithEnum() throws Exception {
        when(participantServices.changeTache(1, Tache.ANIMATEUR))
                .thenReturn(buildParticipant("Tounsi", "Ahmed", Tache.ANIMATEUR));
        mockMvc.perform(put("/participants/1/tache/ANIMATEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tache").value("ANIMATEUR"));
    }

    @Test
    void changeTache_invalidValue_shouldReturn400() throws Exception {
        mockMvc.perform(put("/participants/1/tache/NOT_A_TACHE"))
                .andExpect(status().isBadRequest());
        verify(participantServices, never()).changeTache(anyInt(), any());
    }

    @Test
    void delete_shouldCallService() throws Exception {
        doNothing().when(participantServices).deleteParticipant(1);
        mockMvc.perform(delete("/participants/1"))
                .andExpect(status().isNoContent());
        verify(participantServices).deleteParticipant(1);
    }

    // ---- Ajoutés

    @Test
    void add_blankNom_shouldReturn400WithoutCallingService() throws Exception {
        mockMvc.perform(post("/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"\",\"prenom\":\"Ahmed\",\"tache\":\"INVITE\"}"))
                .andExpect(status().isBadRequest());
        verify(participantServices, never()).addParticipant(any());
    }

    @Test
    void getByIds_shouldUseBatchLookup() throws Exception {
        when(participantServices.getParticipantsByIds(List.of(1, 2))).thenReturn(Arrays.asList(
                buildParticipant("A", "A", Tache.INVITE), buildParticipant("B", "B", Tache.SERVEUR)));
        mockMvc.perform(get("/participants").param("ids", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void search_shouldReturnMatches() throws Exception {
        when(participantServices.search("Tounsi", "Ahmed", Tache.ORGANISATEUR))
                .thenReturn(List.of(buildParticipant("Tounsi", "Ahmed", Tache.ORGANISATEUR)));
        mockMvc.perform(get("/participants/search")
                        .param("nom", "Tounsi").param("prenom", "Ahmed").param("tache", "ORGANISATEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tache").value("ORGANISATEUR"));
    }
}
