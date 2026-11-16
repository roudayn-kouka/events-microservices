package tn.fst.events.statistics.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tn.fst.events.statistics.dto.EventStatsDTO;
import tn.fst.events.statistics.dto.GlobalStatsDTO;
import tn.fst.events.statistics.dto.Tache;
import tn.fst.events.statistics.queries.StatsQueries.ListeStats;
import tn.fst.events.statistics.queries.StatsQueries.StatsPeriodeQuery;
import tn.fst.events.statistics.queries.StatsQueries.StatsResumeQuery;

/**
 * Ajouté après un défaut constaté en réel : les réponses de requêtes traversent Axon Server en JSON.
 * Le bus local des tests ne sérialise rien ; ce test passe par le vrai JacksonSerializer d'Axon,
 * avec l'ObjectMapper de Spring Boot, comme en production.
 */
@JsonTest(properties = "spring.jackson.serialization.fail-on-empty-beans=false")
class QuerySerialisationTest {

    @Autowired ObjectMapper objectMapper;

    private Object allerRetour(Object objet) {
        JacksonSerializer serializer = JacksonSerializer.builder().objectMapper(objectMapper).build();
        SerializedObject<byte[]> json = serializer.serialize(objet, byte[].class);
        return serializer.deserialize(json);
    }

    private static EventStatsDTO stats() {
        Map<Tache, Long> parTache = new EnumMap<>(Tache.class);
        parTache.put(Tache.INVITE, 2L);
        return EventStatsDTO.builder().idEvent("e-1").description("Gala").dateDebut(LocalDate.of(2026, 12, 10))
                .participantsParTache(parTache).coutLogistiqueReservee(250f).averageRating(4.5).build();
    }

    @Test
    void uneListeNuePerdLeTypeDeSesElements() {
        Object relu = allerRetour(new ArrayList<>(List.of(stats())));
        assertInstanceOf(Map.class, ((List<?>) relu).get(0)); // d'où l'erreur 500 constatée
    }

    @Test
    void listeStatsGardeLeTypeDeSesElements() {
        ListeStats relu = (ListeStats) allerRetour(new ListeStats(List.of(stats())));
        EventStatsDTO e = relu.elements().get(0);
        assertEquals(LocalDate.of(2026, 12, 10), e.getDateDebut());
        assertEquals(Long.valueOf(2), e.getParticipantsParTache().get(Tache.INVITE));
        assertEquals(250f, e.getCoutLogistiqueReservee(), 0.001);
    }

    @Test
    void requetesEtResumeFontLAllerRetour() {
        assertEquals(new StatsResumeQuery(), allerRetour(new StatsResumeQuery()));
        StatsPeriodeQuery periode = new StatsPeriodeQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(periode, allerRetour(periode));
        GlobalStatsDTO global = (GlobalStatsDTO) allerRetour(GlobalStatsDTO.builder().nbEvents(4).noteMoyenne(4.2).build());
        assertEquals(4.2, global.getNoteMoyenne(), 0.001);
    }
}
