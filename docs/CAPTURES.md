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

À la phase 3, les **mêmes commandes** (ST5, ST6, ST8) seront relancées. Les captures seront placées côte à côte sur une diapositive :

| Point comparé | AVANT CQRS (phase 2) | APRÈS CQRS (phase 3) |
|---|---|---|
| Source (en-tête `X-Stats-Source`) | C40 : `feign-avant-cqrs` | C-CQRS-1 : projections `db_stats` |
| Latence de `/api/stats/summary` | C40 : environ 40 ms, 4 appels réseau | C-CQRS-1 : lecture locale, 0 appel réseau |
| feedback-service arrêté | C41 : `503` | C-CQRS-2 : `200`, dernières valeurs projetées |
| Code du calcul (`EventStatsServiceImpl`) | inchangé | inchangé (seule la `StatsSource` change) |

_Les captures des phases suivantes sont ajoutées à la fin de chaque phase._
