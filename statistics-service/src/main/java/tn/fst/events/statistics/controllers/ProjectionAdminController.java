package tn.fst.events.statistics.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tn.fst.events.statistics.projections.ProjectionAdmin;
import tn.fst.events.statistics.projections.ProjectionAdmin.EtatProjection;

/**
 * Administration de la projection CQRS. Via la Gateway : /api/stats/admin/** (réservé au rôle
 * ADMIN à la phase 8, Keycloak).
 */
@RestController
@RequestMapping("/stats/admin")
@RequiredArgsConstructor
public class ProjectionAdminController {

    private final ProjectionAdmin admin;

    /** État du processeur et nombre de lignes de chaque table de db_stats. */
    @GetMapping("/projection")
    public EtatProjection etat() {
        return admin.etat();
    }

    /** Reconstruit db_stats depuis l'event store (traitement asynchrone : 202). */
    @PostMapping("/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EtatProjection replay() {
        return admin.rejouer();
    }
}
