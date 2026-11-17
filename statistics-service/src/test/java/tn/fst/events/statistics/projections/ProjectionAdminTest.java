package tn.fst.events.statistics.projections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.fst.events.statistics.sources.StatsSource;

/** Ajouté (phase 5) : replay avec deux instances qui partagent le jeton du processeur. */
class ProjectionAdminTest {

    StreamingEventProcessor processeur;
    ProjectionAdmin admin;

    @BeforeEach
    void setUp() {
        processeur = mock(StreamingEventProcessor.class);
        EventProcessingConfiguration processing = mock(EventProcessingConfiguration.class);
        when(processing.eventProcessor(StatistiquesProjection.GROUPE, StreamingEventProcessor.class))
                .thenReturn(Optional.of(processeur));
        admin = new ProjectionAdmin(processing, mock(StatsSource.class), mock(StatsEvenementRepository.class),
                mock(StatsParticipantRepository.class), mock(StatsLogistiqueRepository.class), mock(StatsAvisRepository.class));
    }

    @Test
    void replaySurLInstanceQuiDetientLeJeton() {
        admin.rejouer();
        InOrder ordre = inOrder(processeur);
        ordre.verify(processeur).shutDown();
        ordre.verify(processeur).resetTokens();
        ordre.verify(processeur).start();
    }

    @Test
    void replaySurLAutreInstanceRefuseEn409EtRedemarreLeProcesseur() {
        doThrow(new UnableToClaimTokenException("owned by 1@autre")).when(processeur).resetTokens();

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> admin.rejouer());

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(processeur).start(); // rien ne reste arrêté
    }
}
