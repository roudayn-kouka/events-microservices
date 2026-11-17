package tn.fst.events.common.pannes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tn.fst.events.common.resilience.UrlForceeInterceptor;

/** Ajouté (phase 4) : pannes 2, 3 (filtre) et 4 (intercepteur Feign), pilotées par des propriétés. */
class PannesSimuleesTest {

    private MockHttpServletResponse executer(MockEnvironment env, String chemin, MockFilterChain chaine) throws Exception {
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        new PannesSimuleesFilter(env).doFilter(new MockHttpServletRequest("GET", chemin), reponse, chaine);
        return reponse;
    }

    @Test
    void sansProprieteLaRequetePasseNormalement() throws Exception {
        MockFilterChain chaine = new MockFilterChain();
        executer(new MockEnvironment(), "/participants/1", chaine);
        assertTrue(chaine.getRequest() != null);
    }

    @Test
    void panne2LaLatenceRetardeLaReponse() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty(PannesSimuleesFilter.LATENCE, "300");
        long debut = System.currentTimeMillis();
        executer(env, "/participants/1", new MockFilterChain());
        assertTrue(System.currentTimeMillis() - debut >= 300);
    }

    @Test
    void panne3LaReponseCorrompueEstUnJsonIllisible() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty(PannesSimuleesFilter.CORROMPUE, "true");
        MockFilterChain chaine = new MockFilterChain();
        MockHttpServletResponse reponse = executer(env, "/participants/1", chaine);
        assertEquals(200, reponse.getStatus());
        assertNull(chaine.getRequest()); // le contrôleur n'est pas appelé
        // Corps lu HORS de la lambda, exception attendue précise : seul l'échec de lecture du JSON
        // peut faire passer le test (Sonar java:S5783 : un échec de getContentAsString() le
        // faisait passer pour une mauvaise raison).
        String corps = reponse.getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        assertThrows(JsonProcessingException.class, () -> mapper.readTree(corps));
    }

    @Test
    void lActuatorNEstJamaisTouche() throws Exception {
        MockEnvironment env = new MockEnvironment().withProperty(PannesSimuleesFilter.CORROMPUE, "true")
                .withProperty(PannesSimuleesFilter.LATENCE, "5000");
        MockFilterChain chaine = new MockFilterChain();
        long debut = System.currentTimeMillis();
        executer(env, "/actuator/health", chaine);
        assertTrue(chaine.getRequest() != null && System.currentTimeMillis() - debut < 1000);
    }

    @Test
    void panne4UneUrlForceeRemplaceLaCibleDuClientEtSaSuppressionLaRetablit() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("events.clients.participant-service.url-forcee", "http://participant-introuvable:8102");
        UrlForceeInterceptor interceptor = new UrlForceeInterceptor(env);
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(new Target.HardCodedTarget<>(Object.class, "participant-service", "http://participant-service"));
        template.target("http://participant-service");
        template.uri("/participants/1");

        interceptor.apply(template);
        assertEquals("http://participant-introuvable:8102/participants/1", template.url());

        env.setProperty("events.clients.participant-service.url-forcee", ""); // correction à chaud
        RequestTemplate normal = new RequestTemplate();
        normal.feignTarget(new Target.HardCodedTarget<>(Object.class, "participant-service", "http://participant-service"));
        normal.target("http://participant-service");
        normal.uri("/participants/1");
        interceptor.apply(normal);
        assertEquals("http://participant-service/participants/1", normal.url());
    }
}
