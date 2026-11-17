package tn.fst.events.feedback.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.fst.events.feedback.clients.EventClient;
import tn.fst.events.feedback.clients.EventDTO;
import tn.fst.events.feedback.clients.ParticipantClient;
import tn.fst.events.feedback.clients.ParticipantDTO;
import tn.fst.events.feedback.repositories.FeedbackRepository;

/**
 * Repris de feature/feedback (1 test) : vraies transactions dans deux threads de requête, sans
 * transaction gérée par le test. Adapté : l'événement et le participant sont renvoyés par les
 * clients Feign simulés au lieu d'être persistés ; chemins sans context-path. La contrainte
 * d'unicité de db_feedback garantit toujours exactement un 201 et un 409.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:feedbackConcurrency;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FeedbackConcurrencyIntegrationTest {

    static final String EV = "44444444-4444-4444-4444-444444444444";

    @Autowired MockMvc mvc;
    @Autowired FeedbackRepository feedbacks;
    @MockitoBean EventClient eventClient;
    @MockitoBean ParticipantClient participantClient;

    @Test
    void concurrentSubmissionsYieldOneCreatedOneConflictAndReadableCommittedFeedback() throws Exception {
        when(eventClient.getEvent(EV)).thenReturn(new EventDTO(EV, "Gala", null, LocalDate.now().minusDays(1), Set.of(7)));
        when(participantClient.getParticipant(7)).thenReturn(new ParticipantDTO(7, "Doe", "Jane", "INVITE"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> submit = () -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return mvc.perform(post("/feedback/{event}/{participant}", EV, 7)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":5}"))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(submit);
            Future<Integer> second = executor.submit(submit);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            Collections.sort(statuses);
            assertEquals(Arrays.asList(201, 409), statuses);
            assertEquals(1, feedbacks.countByEventId(EV));
            mvc.perform(get("/feedback/event/{id}", EV))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].eventId").value(EV))
                    .andExpect(jsonPath("$[0].participantId").value(7));
            mvc.perform(get("/feedback/event/{id}/moyenne", EV))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(5.0));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            feedbacks.deleteAll(feedbacks.findByEventIdOrderByDateAscIdAsc(EV));
        }
    }
}
