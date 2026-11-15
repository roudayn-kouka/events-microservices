package tn.fst.events.feedback.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Ajouté : 404 distant → 404 avec le message métier ; panne → 503. */
class RemoteCallTest {

    @Test
    void notFoundDistantGardeLeMessageMetierDOrigine() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> RemoteCall.call("event-service", "Event not found", () -> {
                    throw FeignErreurs.introuvable("/events/x");
                }));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Event not found", e.getReason());
    }

    @Test
    void erreurServeurDistanteDevient503() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> RemoteCall.call("event-service", "Event not found", () -> {
                    throw FeignErreurs.erreurServeur("/events/x");
                }));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals("event-service indisponible (HTTP 500)", e.getReason());
    }

    @Test
    void serviceInjoignableDevient503SansStatut() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> RemoteCall.call("participant-service", "Participant not found", () -> {
                    throw FeignErreurs.injoignable("/participants/2");
                }));
        assertEquals("participant-service indisponible (pas de réponse)", e.getReason());
    }
}
