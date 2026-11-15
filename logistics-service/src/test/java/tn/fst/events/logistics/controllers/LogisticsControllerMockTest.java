package tn.fst.events.logistics.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.fst.events.common.web.GlobalExceptionHandler;
import tn.fst.events.logistics.entities.Logistics;
import tn.fst.events.logistics.services.ILogisticsService;

/**
 * Repris de feature/logistics (4 tests du contrôleur AVEC Mockito : service simulé, aucun contexte
 * Spring complet). Adaptation : JUnit 4 → JUnit 5 ; le JSON échangé est celui du DTO, mêmes champs
 * que l'entité. 3 tests ajoutés (validation, lot d'ID, coût réservé).
 */
@ExtendWith(MockitoExtension.class)
class LogisticsControllerMockTest {

    @Mock
    ILogisticsService logisticsService;

    @InjectMocks
    LogisticsController logisticsController;

    MockMvc mockMvc;

    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(logisticsController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Logistics buildLogistics() {
        Logistics l = new Logistics();
        l.setIdLog(1);
        l.setDescription("Chaises");
        l.setReserve(true);
        l.setPrixUnit(12.5f);
        l.setQuantite(40);
        return l;
    }

    @Test
    void testAddLogistics() throws Exception {
        Logistics l = buildLogistics();
        when(logisticsService.addLogistics(any(Logistics.class))).thenReturn(l);
        mockMvc.perform(post("/logistics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(l)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLog").value(1))
                .andExpect(jsonPath("$.description").value("Chaises"));
        verify(logisticsService, times(1)).addLogistics(any(Logistics.class));
    }

    @Test
    void testRetrieveLogistics() throws Exception {
        when(logisticsService.getLogisticsById(1)).thenReturn(buildLogistics());
        mockMvc.perform(get("/logistics/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLog").value(1))
                .andExpect(jsonPath("$.quantite").value(40));
        verify(logisticsService).getLogisticsById(1);
    }

    @Test
    void testUpdateLogistics() throws Exception {
        Logistics l = buildLogistics();
        l.setDescription("Tables");
        when(logisticsService.updateLogistics(any(Logistics.class))).thenReturn(l);
        mockMvc.perform(put("/logistics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(l)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Tables"));
        verify(logisticsService).updateLogistics(any(Logistics.class));
    }

    @Test
    void testDeleteLogistics() throws Exception {
        mockMvc.perform(delete("/logistics/{id}", 1))
                .andExpect(status().isNoContent());
        verify(logisticsService, times(1)).deleteLogistics(1);
    }

    // ---- Ajoutés

    @Test
    void add_negativeQuantity_shouldReturn400WithoutCallingService() throws Exception {
        mockMvc.perform(post("/logistics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Chaises\",\"reserve\":true,\"prixUnit\":10,\"quantite\":-3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("quantite : la quantité ne peut pas être négative"));
        verify(logisticsService, never()).addLogistics(any());
    }

    @Test
    void getByIds_shouldUseBatchLookup() throws Exception {
        when(logisticsService.getLogisticsByIds(List.of(1, 2))).thenReturn(List.of(buildLogistics()));
        mockMvc.perform(get("/logistics").param("ids", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idLog").value(1));
    }

    @Test
    void coutReserve_shouldReturnTheSum() throws Exception {
        when(logisticsService.calculerCoutReserve(List.of(1, 3))).thenReturn(1000f);
        mockMvc.perform(get("/logistics/cout-reserve").param("ids", "1,3"))
                .andExpect(status().isOk())
                .andExpect(content().string("1000.0"));
    }
}
