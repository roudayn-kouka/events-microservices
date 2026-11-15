package tn.fst.events.logistics.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.fst.events.logistics.dto.LogisticsDTO;

/**
 * Repris de feature/logistics (4 tests du contrôleur SANS Mockito : vrai contrôleur, vrai service,
 * vraie base ; MySQL dans le monolithe, H2 ici). Adaptation : JUnit 5, lecture du DTO. 1 test ajouté.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogisticsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private LogisticsDTO buildLogistics() {
        return new LogisticsDTO(0, "Chaises", true, 12.5f, 40);
    }

    /** Ajoute une logistique via l'API et retourne l'objet créé. */
    private LogisticsDTO addViaApi() throws Exception {
        MvcResult result = mockMvc.perform(post("/logistics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLogistics())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), LogisticsDTO.class);
    }

    private void deleteViaApi(int id) throws Exception {
        mockMvc.perform(delete("/logistics/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void testAddLogistics() throws Exception {
        mockMvc.perform(post("/logistics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLogistics())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLog").exists())
                .andExpect(jsonPath("$.description").value("Chaises"))
                .andExpect(jsonPath("$.quantite").value(40));
    }

    @Test
    void testRetrieveLogistics() throws Exception {
        LogisticsDTO saved = addViaApi();
        mockMvc.perform(get("/logistics/{id}", saved.getIdLog()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLog").value(saved.getIdLog()))
                .andExpect(jsonPath("$.description").value("Chaises"));
        // 12.5 x 40 = 500
        mockMvc.perform(get("/logistics/{id}/cout", saved.getIdLog()))
                .andExpect(status().isOk())
                .andExpect(content().string("500.0"));
        deleteViaApi(saved.getIdLog());
    }

    @Test
    void testUpdateLogistics() throws Exception {
        LogisticsDTO saved = addViaApi();
        saved.setDescription("Tables");
        saved.setQuantite(10);
        mockMvc.perform(put("/logistics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLog").value(saved.getIdLog()))
                .andExpect(jsonPath("$.description").value("Tables"))
                .andExpect(jsonPath("$.quantite").value(10));
        deleteViaApi(saved.getIdLog());
    }

    @Test
    void testDeleteLogistics() throws Exception {
        LogisticsDTO saved = addViaApi();
        mockMvc.perform(delete("/logistics/{id}", saved.getIdLog()))
                .andExpect(status().isNoContent());
    }

    // ---- Ajouté

    @Test
    void unknownLogistics_shouldReturn404AsJson() throws Exception {
        mockMvc.perform(get("/logistics/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Logistics introuvable : 999999"));
    }
}
