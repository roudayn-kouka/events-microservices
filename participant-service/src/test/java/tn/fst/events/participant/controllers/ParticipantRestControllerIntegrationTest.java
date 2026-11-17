package tn.fst.events.participant.controllers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repris de feature/participant (6 tests, sans Mockito : contexte complet, vrai service,
 * vrai repository, H2). Adaptations : chemins REST /participants, 201/204. 2 tests ajoutés :
 * format d'erreur JSON commun et en-tête X-Instance-Id (module common).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParticipantRestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private int createParticipant(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("idPart").asInt();
    }

    @Test
    void add_then_getById() throws Exception {
        int id = createParticipant("{\"nom\":\"Tounsi\",\"prenom\":\"Ahmed\",\"tache\":\"ORGANISATEUR\"}");
        assertTrue(id > 0);
        mockMvc.perform(get("/participants/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Tounsi"))
                .andExpect(jsonPath("$.tache").value("ORGANISATEUR"));
    }

    @Test
    void getAll_shouldContainCreatedParticipants() throws Exception {
        createParticipant("{\"nom\":\"A\",\"prenom\":\"A\",\"tache\":\"INVITE\"}");
        createParticipant("{\"nom\":\"B\",\"prenom\":\"B\",\"tache\":\"SERVEUR\"}");
        mockMvc.perform(get("/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void update_shouldChangeValues() throws Exception {
        int id = createParticipant("{\"nom\":\"Old\",\"prenom\":\"Name\",\"tache\":\"INVITE\"}");
        mockMvc.perform(put("/participants/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"New\",\"prenom\":\"Person\",\"tache\":\"ANIMATEUR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("New"))
                .andExpect(jsonPath("$.tache").value("ANIMATEUR"));
    }

    @Test
    void changeTache_shouldPersist() throws Exception {
        int id = createParticipant("{\"nom\":\"Tounsi\",\"prenom\":\"Ahmed\",\"tache\":\"INVITE\"}");
        mockMvc.perform(put("/participants/" + id + "/tache/SERVEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tache").value("SERVEUR"));
        mockMvc.perform(get("/participants/" + id))
                .andExpect(jsonPath("$.tache").value("SERVEUR"));
    }

    @Test
    void delete_then_getById_shouldReturn404() throws Exception {
        int id = createParticipant("{\"nom\":\"Tounsi\",\"prenom\":\"Ahmed\",\"tache\":\"INVITE\"}");
        mockMvc.perform(delete("/participants/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/participants/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_unknownId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/participants/99999"))
                .andExpect(status().isNotFound());
    }

    // ---- Ajoutés : briques du module common

    @Test
    void notFound_shouldUseCommonJsonErrorFormat() throws Exception {
        mockMvc.perform(get("/participants/99999"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Participant not found with id 99999"))
                .andExpect(jsonPath("$.path").value("/participants/99999"));
    }

    @Test
    void everyResponse_shouldExposeInstanceId() throws Exception {
        mockMvc.perform(get("/participants"))
                .andExpect(header().exists("X-Instance-Id"));
    }
}
