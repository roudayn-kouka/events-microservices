package tn.fst.events.logistics.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.fst.events.logistics.entities.Logistics;
import tn.fst.events.logistics.services.ILogisticsService;

/** Repris de feature/participant (2 tests, Farah) ; adaptation JUnit 4 → JUnit 5 seulement. */
@ExtendWith(MockitoExtension.class)
class LogisticsControllerListMockTest {

    @Mock
    ILogisticsService logisticsService;

    @InjectMocks
    LogisticsController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private Logistics tables() {
        Logistics l = new Logistics();
        l.setIdLog(2);
        l.setDescription("Tables");
        l.setReserve(true);
        return l;
    }

    @Test
    void getAll() throws Exception {
        when(logisticsService.getAllLogistics()).thenReturn(Collections.singletonList(tables()));
        mockMvc.perform(get("/logistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Tables"));
    }

    @Test
    void getReserved() throws Exception {
        when(logisticsService.getReservedLogistics()).thenReturn(Collections.singletonList(tables()));
        mockMvc.perform(get("/logistics/reserved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reserve").value(true));
    }
}
