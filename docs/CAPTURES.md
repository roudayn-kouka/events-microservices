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

_Les captures des phases suivantes sont ajoutées à la fin de chaque phase._
