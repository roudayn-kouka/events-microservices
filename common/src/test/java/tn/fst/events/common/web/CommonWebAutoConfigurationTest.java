package tn.fst.events.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Le gestionnaire global et le filtre X-Instance-Id sont chargés automatiquement et fonctionnent. */
@WebMvcTest(controllers = CommonWebAutoConfigurationTest.DemoController.class,
        properties = "eureka.instance.instance-id=demo-service:demo-1")
@ImportAutoConfiguration(CommonWebAutoConfiguration.class)
@Import(CommonWebAutoConfigurationTest.DemoController.class)
class CommonWebAutoConfigurationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void ajouteLEnTeteXInstanceId() throws Exception {
        mvc.perform(get("/demo/ok")).andExpect(status().isOk())
                .andExpect(header().string(InstanceIdFilter.HEADER, "demo-service:demo-1"));
    }

    @Test
    void transformeUneErreurMetierEnJson() throws Exception {
        mvc.perform(get("/demo/introuvable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Ressource introuvable"))
                .andExpect(jsonPath("$.path").value("/demo/introuvable"));
    }

    @Test
    void detailleLesErreursDeValidation() throws Exception {
        mvc.perform(post("/demo").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Données invalides"))
                // le texte du message dépend de la langue de la JVM : on vérifie le champ en cause
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.startsWith("nom : ")));
    }

    @Test
    void refuseUnParametreDuMauvaisType() throws Exception {
        mvc.perform(get("/demo/nombre/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valeur « abc » invalide pour le paramètre n"));
    }

    @Test
    void refuseUnJsonIllisible() throws Exception {
        mvc.perform(post("/demo").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête illisible"));
    }

    @SpringBootApplication
    static class DemoApplication {
    }

    record Demande(@NotBlank String nom) {
    }

    @RestController
    static class DemoController {

        @GetMapping("/demo/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/demo/introuvable")
        String introuvable() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource introuvable");
        }

        @GetMapping("/demo/nombre/{n}")
        int nombre(@PathVariable("n") int n) {
            return n;
        }

        @PostMapping("/demo")
        String creer(@Valid @RequestBody Demande demande) {
            return demande.nom();
        }
    }
}
