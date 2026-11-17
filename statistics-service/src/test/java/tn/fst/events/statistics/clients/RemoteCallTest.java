package tn.fst.events.statistics.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Ajouté : 404 distant → 404 ; panne (réponse 500 ou aucune réponse) → 503 qui nomme le service. */
class RemoteCallTest {

    @Test
    void notFoundDistantDevient404() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> RemoteCall.call("event-service", "Event not found with id x", () -> {
                    throw FeignErreurs.introuvable("/events/x");
                }));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void panneDevient503QuiNommeLeService() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> RemoteCall.call("feedback-service", "x", () -> {
                    throw FeignErreurs.erreurServeur("/feedback/moyennes");
                }));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals("feedback-service indisponible (HTTP 500)", e.getReason());
    }
}
