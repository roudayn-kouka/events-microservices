# Captures d'écran à réaliser

Dans l'ordre des slides (voir `PRESENTATION-PLAN.md`). Cocher au fur et à mesure.
Pour chaque capture : ce qu'il faut ouvrir, et ce qui doit être visible.

## Git et JIRA

- [ ] **C1 — Backlog JIRA importé** : projet `EVT` → Backlog (ou Liste). Visible : les epics par technologie, des clés EVT-18 à EVT-22 au statut *Terminé*.
- [ ] **C2 — Historique GitFlow** : terminal WSL, `git log --oneline --graph --all`. Visible : `main`, `develop`, `feature/EVT-18-init-monorepo`, la fusion `--no-ff`, des messages qui citent EVT-xx.
- [ ] **C3 — POM parent** : `pom.xml` ouvert dans l'éditeur. Visible : Spring Boot 3.5.16, `spring-cloud.version` 2025.0.3, `axon.version` 4.13.3.
- [ ] **C4 — `mvn validate`** : terminal. Visible : `BUILD SUCCESS` et la version Spring Cloud affichée par `help:evaluate`.

## Socle Spring Cloud (phase 1)

- [ ] **C5 — Stack démarrée** : terminal, `make up` puis `docker compose ps`. Visible : 4 conteneurs `(healthy)`.
- [ ] **C6 — Tests de fumée** : terminal, `make health`. Visible : toutes les lignes OK.
- [ ] **C7 — config-repo** : arborescence `config-repo/` dans l'éditeur, avec `api-gateway.yml` ouvert (routes `lb://`).
- [ ] **C8 — Config Server** : navigateur, `http://localhost:8888/api-gateway/default`. Visible : `propertySources` qui viennent de `config-repo`.
- [ ] **C9 — Surcharge à chaud** : terminal, commandes C3 et C4 de `COMMANDES.md`. Visible : `config-overrides` en première source, puis `git status` vide.
- [ ] **C10 — Eureka** : navigateur, `http://localhost:8761`. Visible : `API-GATEWAY` et `CONFIG-SERVER` UP.
- [ ] **C11 — Routes Gateway** : navigateur, `http://localhost:8088/actuator/gateway/routes`. Visible : les 5 routes `lb://`.
- [ ] **C12 — CORS** : terminal, commandes W3 et W4. Visible : `200` + `Access-Control-Allow-Origin` pour Angular, `403` pour une autre origine.
- [ ] **C13 — Isolation MySQL** : terminal, commandes S2 et S3. Visible : `event_user` ne voit que `db_event`, `ERROR 1044` sur `db_participant`.
- [ ] **C14 — Résilience au démarrage** : terminal, `docker compose restart config-server api-gateway`, puis `docker compose ps`. Visible : la Gateway redevient `healthy`.

## Services métier (phase 2)

### participant-service

- [ ] **C15 — participant-service dans Eureka** : `http://localhost:8761`. Visible : `PARTICIPANT-SERVICE` UP (1 instance).
- [ ] **C16 — CRUD via la Gateway** : terminal, commandes P3 à P7. Visible : création (`idPart`), liste, lot `[1,2]`, recherche, changement de tâche, avec le JSON formaté par `jq`.
- [ ] **C17 — Erreurs JSON homogènes** : terminal, commandes P8 à P10. Visible : 404, 400 de validation (`details`), 400 de tâche inconnue.
- [ ] **C18 — X-Instance-Id** : terminal, commande P11. Visible : `X-Instance-Id: participant-service:participant-service-1`.
- [ ] **C19 — Postman** : dossier `participant-service` ouvert. Visible : le tableau « ancien endpoint → nouvel endpoint » dans la description, et le Runner (ou Newman, commande N2) avec 0 échec.
- [ ] **C20 — make health** : terminal. Visible : `participant-service : UP dans Eureka` et `répond via la Gateway`.

### logistics-service

- [ ] **C21 — logistique via la Gateway** : terminal, commandes L2 à L5. Visible : création, liste réservée, coût 500.0, coût réservé d'un lot.
- [ ] **C22 — 404 au lieu de 500** : terminal, commande L6, à côté de l'extrait « avant / après » de `PRESENTATION-PLAN.md`.
- [ ] **C23 — Reprise après redémarrage d'Eureka** : terminal, commande L7. Visible : `make health` tout vert et les 4 applications dans le registre.
- [ ] **C24 — Postman** : dossier `logistics-service` (tableau des correspondances) et Newman : 22 requêtes, 0 échec.

### event-service

- [ ] **C25 — event-service dans Eureka** : `http://localhost:8761` et `make health`. Visible : `EVENT-SERVICE` UP, et `event-service : répond via la Gateway`.
- [ ] **C26 — Création avec Feign** : terminal, commandes EV2 à EV4. Visible : `201`, l'UUID, `participantIds: [1]`, puis la logistique créée dans logistics-service et rattachée par son ID.
- [ ] **C27 — calculCout via Feign** : terminal, commande EV5, puis EV11. Visible : `"cout": 250.0`, puis la ligne `[scheduling-1] … Cout de l'Event … est 250.0` (exécution planifiée).
- [ ] **C28 — Vue composée** : terminal, commande EV6. Visible : un événement de `db_event`, avec ses participants (participant-service) et sa logistique (logistics-service).
- [ ] **C29 — Panne d'un service appelé (AVANT Resilience4j)** : terminal, commande EV10. Visible : `503` « participant-service indisponible », alors que `GET /api/events/{id}` répond toujours `200`. À refaire à la phase 4 pour comparer avec le fallback.
- [ ] **C30 — Postman** : dossier `event-service` (tableau des correspondances) et Newman : 38 requêtes, 73 assertions, 0 échec.

### feedback-service

- [ ] **C31 — feedback-service dans Eureka** : `make health`. Visible : `feedback-service : UP dans Eureka` et `répond via la Gateway`, plus les 6 applications dans le registre.
- [ ] **C32 — Les règles métier via la Gateway** : terminal, commandes FB2 à FB5. Visible : `201`, puis `409` (deuxième avis), `400` (non inscrit), `400` (pas terminé).
- [ ] **C33 — Validation et vérifications Feign** : terminal, commandes FB6 et FB7. Visible : note 6 → `details`, note 1.5 → 400 ; événement ou participant inconnu → 404 renvoyé par Feign.
- [ ] **C34 — Moyennes** : terminal, commande FB8. Visible : la moyenne de l'événement et `GET /api/feedback/moyennes`, source de statistics-service.
- [ ] **C35 — Panne d'event-service (AVANT Resilience4j)** : terminal, commande FB9. Visible : `503` pour un nouvel avis, mais `/moyennes` répond `200` (lecture locale). À comparer à la phase 4.
- [ ] **C36 — Postman** : dossier `feedback-service` et Newman : 53 requêtes, 97 assertions, 0 échec.

### ⭐ statistics-service (mon module) — VERSION AVANT CQRS

- [ ] **C37 — Les 5 services dans Eureka** : `http://localhost:8761` et `make health`. Visible : les 7 applications UP, et `statistics-service : répond via la Gateway`.
- [ ] **C38 — Résumé global** : terminal, commande ST2. Visible : le JSON du tableau de bord (`nbEvents`, `tauxReservation`, `participantsParTache`, `noteMoyenne`).
- [ ] **C39 — Statistiques d'un événement noté** : terminal, commande ST3. Visible : durée, participants par tâche, coût réservé, `averageRating` lue dans feedback-service.
- [ ] **C40 — AVANT CQRS : source et latence** : terminal, commandes ST5 et ST6. Visible : `X-Stats-Source: feign-avant-cqrs`, puis les 5 temps de réponse (environ 40 ms).
- [ ] **C41 — AVANT CQRS : un service en panne rend les statistiques indisponibles** : terminal, commande ST8. Visible : `feedback-service` arrêté → `/api/stats/summary` répond `503`.
- [ ] **C42 — Postman** : dossier « statistics-service (mon module) — AVANT CQRS », et Newman : 62 requêtes, 116 assertions, 0 échec.

### Données de démonstration

- [ ] **C43 — make seed** : terminal, commandes D7 et D8. Visible : les 4 événements `[Démo]`, les coûts recalculés par `calculCout` et le résumé de statistics-service ; puis la relance sans doublon.

### Comparaison AVANT / APRÈS CQRS (planifiée dès la phase 2)

Les **mêmes commandes** (ST5, ST6, ST8) ont été relancées à la phase 3 (valeurs mesurées). À placer côte à côte sur une diapositive :

| Point comparé | AVANT CQRS (phase 2) | APRÈS CQRS (phase 3) |
|---|---|---|
| Source (en-tête `X-Stats-Source`) | C40 : `feign-avant-cqrs` | C50 : `projections-cqrs` |
| Latence de `/api/stats/summary` | C40 : 38 à 47 ms, 4 appels réseau | C50 : 19 à 21 ms, 0 appel réseau |
| feedback-service arrêté | C41 : `503` | C51 : `200`, dernières valeurs projetées |
| Code du calcul (`EventStatsServiceImpl`) | inchangé | inchangé (seule la `StatsSource` change) |

### ⭐ CQRS Axon (phase 3)

- [ ] **C44 — Axon Server** : `http://localhost:8024`. Visible : les 5 applications connectées, et les commandes d'event-service (A2).
- [ ] **C45 — Event sourcing** : terminal, commande A3. Visible : l'historique numéroté d'un agrégat (`EventCreated`, `LogistiqueAffectee`, `EventCostUpdated`).
- [ ] **C46 — Règle de l'agrégat** : terminal, commande A4. Visible : `204`, puis `404` sur la re-suppression, avec le message transporté par Axon Server.
- [ ] **C47 — Tests de l'agrégat** : terminal, commande A11. Visible : les 16 tests Given / When / Then verts (IDE ou rapport Surefire).
- [ ] **C48 — Projection de mon module** : terminal, commande A5, et `db_stats` dans MySQL (`SELECT * FROM token_entry`). Visible : processeur `statistiques` actif, tables remplies, jeton enregistré.
- [ ] **C49 — Cohérence à terme** : terminal, commande A8. Visible : l'ancienne valeur, puis la nouvelle.
- [ ] **C50 — APRÈS CQRS : source et latence** : terminal, commande A6. À placer à côté de C40.
- [ ] **C51 — APRÈS CQRS : services d'écriture arrêtés** : terminal, commande A7 (et ST8). Visible : `200`. À placer à côté de C41.
- [ ] **C52 — Replay** : terminal, commande A9. Visible : `202`, puis « identique ».

### Résilience4j et pannes (phase 4)

- [ ] **C53 — Panne 1, disponibilité** : terminal, `make panne N=1`. Visible : le premier appel lent (3 tentatives), puis les appels refusés en 0,01 s, l'état **OPEN**, le 503 explicite, puis HALF_OPEN et CLOSED.
- [ ] **C54 — Transitions du circuit** : terminal, commande R7, juste après C53. Visible : CLOSED_TO_OPEN, OPEN_TO_HALF_OPEN, HALF_OPEN_TO_CLOSED avec leurs heures.
- [ ] **C55 — Panne 2, temps de réponse** : `make panne N=2`. Visible : `config-overrides` + `/actuator/refresh` → `["panne.latence-ms"]`, puis 504 « TimeLimiter ».
- [ ] **C56 — Panne 3, réponse incorrecte** : `make panne N=3`. Visible : le JSON tronqué renvoyé en 200, puis 502 « réponse incorrecte, rejetée ».
- [ ] **C57 — Panne 4, erreur réseau et correction à chaud** : `make panne N=4`. Visible : « injoignable (erreur réseau) après 3 tentatives », puis la correction par refresh et CLOSED.
- [ ] **C58 — Panne 5, dépendance externe** : `make panne N=5`. Visible : santé `{"status":"DOWN","db":"DOWN"}`, circuit OPEN, puis UP et CLOSED.
- [ ] **C59 — Fallback de la Gateway** : commande R9. Visible : 503 JSON « … indisponible (Gateway) » (à placer à côté du 500 de la phase 3).
- [ ] **C60 — Configuration** : `config-repo/resilience-feign.yml` dans l'IDE. Visible : Retry 3 × 500 ms, RateLimiter 5/s, CircuitBreaker, TimeLimiter 5 s.

### Répartition de charge (phase 5)

- [ ] **C61 — Eureka, 2 instances** : `http://localhost:8761`. Visible : `PARTICIPANT-SERVICE` et `STATISTICS-SERVICE` avec 2 instances UP chacun.
- [ ] **C62 — Répartition 50 / 50** : `make repartition` (parties 1 et 2). Visible : 10 / 10 pour la Gateway et pour Feign.
- [ ] **C63 — Bascule sans erreur** : `make repartition` (partie 3). Visible : 20 requêtes sur l'instance 1 quand l'instance 2 est arrêtée.
- [ ] **C64 — Replay à 2 instances** : `make replay`. Visible : les 4 étapes, « identique à avant », et les 2 instances UP.

_Les captures des phases suivantes sont ajoutées à la fin de chaque phase._
