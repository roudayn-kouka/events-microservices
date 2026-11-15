package tn.fst.events.event.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Ajouté : traduction des erreurs Feign en réponses HTTP (404 distant → 404, panne → 503). */
class RemoteCallTest {

    @Test
    void renvoieLeResultatDeLAppel() {
        assertEquals("ok", RemoteCall.call("participant-service", "introuvable", () -> "ok"));
    }

    @Test
    void notFoundDistantDevient404AvecLeMessageFourni() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> RemoteCall.call("participant-service", "Participant not found with id 7", () -> {
                    throw FeignErreurs.introuvable("/participants/7");
                }));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Participant not found with id 7", e.getReason());
    }

    @Test
    void erreurServeurDistanteDevient503AvecLeStatut() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> RemoteCall.call("logistics-service", "introuvable", () -> {
                    throw FeignErreurs.erreurServeur("/logistics");
                }));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals("logistics-service indisponible (HTTP 500)", e.getReason());
    }

    @Test
    void serviceInjoignableDevient503SansStatut() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> RemoteCall.call("logistics-service", "introuvable", () -> {
                    throw FeignErreurs.injoignable("/logistics");
                }));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals("logistics-service indisponible (pas de réponse)", e.getReason());
    }
}
