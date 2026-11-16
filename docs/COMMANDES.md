# Commandes exécutées, par technologie

Toutes les commandes ci-dessous ont été **réellement exécutées** dans WSL Ubuntu (`~/events-microservices`), sauf mention « à exécuter après rechargement » (AWS) ou « après push » (GitHub, Jenkins réel).
Format : la commande, son rôle, le résultat attendu ou obtenu.

---

## Git

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| G1 | `git init -b main` | Crée le dépôt local, branche principale `main` | Dépôt vide, **aucun remote** |
| G2 | `git config user.name "Roudayna Kouka"` puis `git config user.email "roudayna.kouka@etudiant-fst.utm.tn"` | Identité des commits (la même que dans le dépôt d'équipe) | — |
| G3 | `git config core.autocrlf input` | Fins de ligne Unix dans le dépôt | — |
| G4 | `git add .gitignore .gitattributes .editorconfig README.md docs/PLAN.md` puis `git commit -m "chore(repo): plan validé et fichiers de base EVT-18"` | Premier commit sur `main` | `fd2087e` |
| G5 | `git switch -c develop` | Branche d'intégration GitFlow | `develop` créée depuis `main` |
| G6 | `git switch -c feature/EVT-18-init-monorepo` | Branche de la phase 0 | — |
| G7 | `git merge --no-ff feature/EVT-18-init-monorepo` (sur `develop`) | Fusion de la phase 0 | Commit de fusion visible dans `git log --graph` |
| G8 | `git log --oneline --graph --all` | Afficher l'historique GitFlow | Capture C2 |

Convention de commit : `type(portée): message EVT-xx`, avec `type` ∈ feat, fix, test, docs, chore, ci, refactor.

## Maven

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| M1 | `mvn -B validate` | Vérifie le POM parent et résout les BOM Spring Cloud 2025.0.3 et Axon 4.13.3 | `BUILD SUCCESS` |
| M2 | `mvn -B help:evaluate -Dexpression=spring-cloud.version -DforceStdout` | Afficher la version Spring Cloud héritée | `2025.0.3` |

## JIRA

| # | Action | Rôle | Résultat |
|---|---|---|---|
| J1 | Import de `jira/backlog.csv` (procédure dans `jira/README.md`) | Créer le backlog | Tickets EVT-1 à EVT-89, hiérarchie epic → story → tâche |

## Config Server

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| C1 | `mvn -B verify` | Compile et teste le socle (config-server, eureka-server, api-gateway) | `BUILD SUCCESS`, 8 tests |
| C2 | `curl -s localhost:8888/api-gateway/default` | Configuration servie à la Gateway | Sources : `/repo/config-repo/api-gateway.yml` puis `/repo/config-repo/application.yml` (clone Git) |
| C3 | `printf "demo:\n  message: surcharge-a-chaud\n" > config-overrides/api-gateway.yml` puis la même requête | Surcharge à chaud, sans commit | 1re source : `file:/config-overrides/api-gateway.yml` → `{demo.message: surcharge-a-chaud}` |
| C4 | `rm config-overrides/api-gateway.yml` | Retirer la surcharge | `git status` vide : rien de versionné n'a changé |
| C5 | Ajouter une ligne dans `config-repo/api-gateway.yml` **sans commit**, puis la requête C2 | Prouver que le Config Server lit **Git** et non le disque | La modification n'est pas servie (0 occurrence) ; `git checkout -- config-repo/api-gateway.yml` |
| C6 | `docker compose restart config-server api-gateway` | Redémarrage simultané : la Gateway doit attendre le Config Server | Gateway `healthy` en 12 s, après 3 tentatives (`fail-fast` + `spring-retry`) |

## Eureka

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| E1 | Navigateur : `http://localhost:8761` | Tableau de bord Eureka | Instances `API-GATEWAY` et `CONFIG-SERVER` UP |
| E2 | `curl -s -H "Accept: application/json" localhost:8761/eureka/apps` | Registre au format JSON | Liste des applications et de leurs instances |

## Gateway

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| W1 | `curl -s localhost:8088/actuator/gateway/routes` | Routes chargées depuis le Config Server | 5 routes : `/api/events/**` → `lb://event-service`, participants, logistics, feedback, stats |
| W2 | `curl -s -o /dev/null -w "%{http_code}" localhost:8088/api/participants` | Route vers un service non démarré | `503` (aucune instance dans Eureka) |
| W3 | `curl -s -D - -o /dev/null -X OPTIONS localhost:8088/api/participants -H "Origin: http://localhost:4200" -H "Access-Control-Request-Method: POST"` | Préflight CORS du frontend Angular | `200`, `Access-Control-Allow-Origin: http://localhost:4200`, `Access-Control-Allow-Credentials: true` |
| W4 | Même requête avec `-H "Origin: http://pirate.example"` | Origine non autorisée | `403` |
| W5 | `curl -s -o /dev/null -w "%{http_code}" -X POST localhost:8088/actuator/gateway/refresh` | Modifier les routes à chaud (refusé) | `404` : endpoint en lecture seule |

## MySQL

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| S1 | `docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES;"'` | Bases créées au premier démarrage | `db_event db_participant db_logistics db_feedback db_stats db_keycloak` |
| S2 | `docker compose exec mysql sh -c 'mysql -uevent_user -p"$EVENT_DB_PASSWORD" -e "SHOW DATABASES;"'` | Ce que voit l'utilisateur d'event-service | Seulement `db_event` |
| S3 | `docker compose exec mysql sh -c 'mysql -uevent_user -p"$EVENT_DB_PASSWORD" -e "USE db_participant;"'` | Isolation entre services | `ERROR 1044 … Access denied for user 'event_user'@'%' to database 'db_participant'` |
| S4 | Client MySQL (Workbench, DBeaver) sur `localhost:3307` | Connexion depuis Windows (3306 = MySQL Windows) | — |

## Outils de la ligne de commande

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| O1 | `sudo apt install -y jq` | Installer jq (formatage et filtrage du JSON dans le terminal) | Déjà présent : `jq --version` → `jq-1.8.1` |
| O2 | `curl -s … \| jq` | Rendre les réponses JSON lisibles dans les captures | JSON indenté et coloré |

## Services métier

Toutes les requêtes passent par la Gateway (`localhost:8088`). Variable utile : `G=http://localhost:8088/api/participants`.

### participant-service

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| P1 | `mvn -B -pl common,participant-service -am install` | Compiler et tester le service | 41 tests verts (+ 5 pour `common`) |
| P2 | `make up` puis `make health` | Démarrer et vérifier | `participant-service : UP dans Eureka` et `répond via la Gateway` |
| P3 | `curl -s -X POST $G -H "Content-Type: application/json" -d '{"nom":"Tounsi","prenom":"Ahmed","tache":"ORGANISATEUR"}' \| jq` | Créer un participant | `201`, `{"idPart":1,"nom":"Tounsi",…}` |
| P4 | `curl -s $G \| jq` | Lister | Tableau des participants |
| P5 | `curl -s "$G?ids=1,2" \| jq 'map(.idPart)'` | Lecture par lot (utilisée par Feign) | `[1,2]` |
| P6 | `curl -s "$G/search?nom=Tounsi&prenom=Ahmed&tache=ORGANISATEUR" \| jq` | Recherche (calculCout) | Le participant 1 |
| P7 | `curl -s -X PUT $G/2/tache/ANIMATEUR \| jq` | Changer la tâche | `"tache":"ANIMATEUR"` |
| P8 | `curl -s $G/999 \| jq` | Participant inconnu | `{"status":404,"message":"Participant not found with id 999",…}` |
| P9 | `curl -s -X POST $G -H "Content-Type: application/json" -d '{"nom":"","prenom":"X","tache":"INVITE"}' \| jq` | Validation `@Valid` | `400`, `"details":["nom : le nom est obligatoire"]` |
| P10 | `curl -s -X PUT $G/1/tache/PILOTE \| jq` | Tâche inconnue | `400`, `Valeur « PILOTE » invalide pour le paramètre tache` |
| P11 | `curl -s -D - -o /dev/null $G \| grep -i x-instance-id` | Instance qui a répondu | `X-Instance-Id: participant-service:participant-service-1` |

### logistics-service

Variable utile : `L=http://localhost:8088/api/logistics`.

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| L1 | `mvn -B -pl logistics-service -am install` | Compiler et tester | 29 tests verts |
| L2 | `curl -s -X POST $L -H "Content-Type: application/json" -d '{"description":"Sonorisation","reserve":true,"prixUnit":250,"quantite":2}' \| jq` | Créer une logistique | `201`, `{"idLog":1,…}` |
| L3 | `curl -s $L/reserved \| jq 'map({idLog,description,reserve})'` | Logistique réservée | Liste filtrée |
| L4 | `curl -s $L/1/cout` | Coût d'une logistique | `500.0` (250 × 2) |
| L5 | `curl -s "$L/cout-reserve?ids=$(curl -s $L \| jq -r 'map(.idLog)\|join(",")')"` | Coût réservé d'un lot (calculCout) | Somme des seules logistiques réservées |
| L6 | `curl -s $L/999 \| jq` | Logistique inconnue | `404`, `"message":"Logistics introuvable : 999"` (500 dans le monolithe) |
| L7 | `make restart S=eureka-server` puis `make health` | Redémarrer un service en gardant la branche Git courante pour le Config Server | Toutes les applications se réinscrivent seules ; la Gateway route de nouveau |

### event-service

Variable utile : `V=http://localhost:8088/api/events`. EV2 enregistre l'identifiant dans `$EV`.

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| EV1 | `mvn -B -pl event-service -am install` | Compiler et tester | 65 tests verts |
| EV2 | `EV=$(curl -s -X POST $V -H "Content-Type: application/json" -d '{"description":"Gala FST","dateDebut":"2026-12-10","dateFin":"2026-12-11","participantIds":[1]}' \| tee /dev/stderr \| jq -r .idEvent)` | Créer un événement (participant vérifié par Feign) | `201`, UUID, `"participantIds":[1]` |
| EV3 | `curl -s -X POST $V/$EV/logistics -H "Content-Type: application/json" -d '{"description":"Chaises","reserve":true,"prixUnit":2.5,"quantite":100}' \| jq` | Créer la logistique dans logistics-service (Feign) et la rattacher | `201`, `{"idLog":4,…}` |
| EV4 | `curl -s -X POST $V/$EV/logistics -H "Content-Type: application/json" -d '{"description":"Goodies","reserve":false,"prixUnit":4,"quantite":50}' \| jq -c` | Logistique non réservée | `"reserve":false` |
| EV5 | `curl -s -X POST $V/cout/recalcul \| jq --arg ev "$EV" '.[] \| select(.idEvent==$ev) \| {description,cout,logisticsIds}'` | `calculCout` à la demande | `"cout": 250.0` (Goodies ignorés) |
| EV6 | `curl -s $V/$EV/details \| jq` | Vue composée (3 services) | `participants` et `logistics` complets |
| EV7 | `curl -s $V/logistics/2026-12-01/2026-12-31 \| jq -c 'map({idLog,description,reserve})'` | Ancien `getLogs` | Logistique réservée seulement |
| EV8 | `curl -s -X POST $V -H "Content-Type: application/json" -d '{"description":"X","dateDebut":"2026-12-10","dateFin":"2026-12-11","participantIds":[99999]}' \| jq` | Participant inconnu | `404`, `"Participant not found with id [99999]"` |
| EV9 | `curl -s -X POST $V -H "Content-Type: application/json" -d '{"description":"X","dateDebut":"2026-12-10","dateFin":"2026-12-01"}' \| jq .message` | Dates incohérentes | `400`, « La date de fin précède la date de début » |
| EV10 | `docker compose stop participant-service; curl -s -X POST $V/$EV/participants/1 \| jq; curl -s -o /dev/null -w '%{http_code}\n' $V/$EV; make restart S=participant-service` | Panne du service appelé (avant Resilience4j) | `503` explicite ; la lecture simple répond `200` |
| EV11 | `docker compose logs event-service \| grep scheduling-1 \| grep -E 'Cout de\|reporté' \| tail -3` | Exécution planifiée de `calculCout` (chaque minute) | `Cout de l'Event … est 250.0`, ou « calculCout reporté » pendant une panne |

### feedback-service

Variables utiles : `F=http://localhost:8088/api/feedback`. `$EV` est un événement **terminé** où le participant 1 est inscrit, `$FUTUR` un événement à venir.

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| FB1 | `mvn -B -pl feedback-service -am install` puis `make up` et `make health` | Compiler, tester, démarrer | 62 tests verts ; `feedback-service : UP dans Eureka` |
| FB2 | `EV=$(curl -s -X POST http://localhost:8088/api/events -H "Content-Type: application/json" -d '{"description":"Forum IA","dateDebut":"2026-11-01","dateFin":"2026-11-10","participantIds":[1]}' \| jq -r .idEvent); curl -s -X POST $F/$EV/1 -H "Content-Type: application/json" -d '{"note":5,"commentaire":"Excellent forum"}' \| jq` | Créer un événement terminé, puis donner un avis | `201`, `{"id":1,"note":5,"eventId":"…","participantId":1}` |
| FB3 | `curl -s -X POST $F/$EV/1 -H "Content-Type: application/json" -d '{"note":4}' \| jq -c '{status,message}'` | Deuxième avis | `409`, « Participant already reviewed this event » |
| FB4 | `curl -s -X POST $F/$EV/2 -H "Content-Type: application/json" -d '{"note":4}' \| jq -c '{status,message}'` | Participant non inscrit | `400`, « Participant is not associated with this event » |
| FB5 | `curl -s -X POST $F/$FUTUR/1 -H "Content-Type: application/json" -d '{"note":4}' \| jq -c '{status,message}'` | Événement pas terminé | `400`, « Event has not finished » |
| FB6 | `curl -s -X POST $F/$EV/1 -H "Content-Type: application/json" -d '{"note":6}' \| jq -c '{status,details}'` | Note hors bornes | `400`, `["note : Rating must be between 1 and 5"]` (1.5 : `400` aussi) |
| FB7 | `curl -s -X POST $F/00000000-0000-0000-0000-000000000000/1 -H "Content-Type: application/json" -d '{"note":4}' \| jq -c '{status,message}'` | Événement inconnu (Feign) | `404`, « Event not found » |
| FB8 | `curl -s $F/event/$EV/moyenne \| jq -c; curl -s $F/moyennes \| jq -c` | Moyenne d'un événement, moyennes de tous | `{"averageRating":5.0,"totalFeedbacks":1}` |
| FB9 | `docker compose stop event-service; curl -s -X POST $F/$EV/1 -H "Content-Type: application/json" -d '{"note":4}' \| jq -c '{status,message}'; curl -s -o /dev/null -w '%{http_code}\n' $F/moyennes; make restart S=event-service` | Panne d'event-service (avant Resilience4j) | `503` « event-service indisponible » ; `/moyennes` → `200` |

### ⭐ statistics-service (mon module) — VERSION AVANT CQRS

Variable utile : `S=http://localhost:8088/api/stats`. `$EV` est un événement noté (voir FB2).

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| ST1 | `mvn -B -pl statistics-service -am install` puis `make up` et `make health` | Compiler, tester, démarrer | 45 tests verts ; les 5 services UP dans Eureka |
| ST2 | `curl -s $S/summary \| jq` | Résumé global (tableau de bord) | `nbEvents`, `tauxReservation`, `participantsParTache`, `nbFeedbacks`, `noteMoyenne` |
| ST3 | `curl -s $S/events/$EV \| jq '{description,dureeJours,nbParticipants,participantsParTache,coutLogistiqueReservee,averageRating}'` | Statistiques d'un événement | `averageRating` lue dans feedback-service |
| ST4 | `curl -s $S/events \| jq -c '.[] \| {description,dateDebut,coutLogistiqueReservee}'`, `curl -s $S/upcoming \| jq -c 'map(.dateDebut)'`, `curl -s $S/between/2026-11-01/2026-11-30 \| jq -c 'map(.description)'` | Tous, à venir, période | Triés par date de début |
| ST5 | `curl -s -D - -o /dev/null $S/summary \| grep -i -E 'x-stats-source\|x-instance-id'` | Version de la source | `X-Stats-Source: feign-avant-cqrs` |
| ST6 | `for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{time_total}\n' $S/summary; done` | Latence AVANT CQRS | environ 0,040 s (4 appels Feign) |
| ST7 | `curl -s localhost:8105/actuator/prometheus \| grep '^fst_'` | Métriques métier | `fst_events_count`, `fst_participants_count`, `fst_logistics_reserved_cost` |
| ST8 | `docker compose stop feedback-service; curl -s $S/summary \| jq -c '{status,message}'; make restart S=feedback-service` | AVANT CQRS : un service lu est en panne | `503`, « feedback-service indisponible (pas de réponse) » |

## Postman

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| N1 | `python3 postman/generate.py` | Régénérer la collection et les environnements (local, AWS) | `postman/*.json` |
| N2 | `docker run --rm --network host -v "$PWD/postman:/etc/newman" postman/newman:6-alpine run events-microservices.postman_collection.json -e local.postman_environment.json` | Exécuter toute la collection en ligne de commande (Newman) | les 5 services (CQRS compris) : 64 requêtes, 122 assertions, 0 échec |
| N3 | Postman → *Import* → `postman/events-microservices.postman_collection.json` et `postman/local.postman_environment.json` | Utiliser la collection dans Postman | Un dossier par service, avec le tableau des correspondances d'endpoints |

## Feign

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| F1 | `grep -rn '@FeignClient' */src/main/java` | Lister les clients Feign | event → participant, logistics ; feedback → event, participant ; statistics → les 4 |
| F2 | `curl -s localhost:8888/event-service/default/develop \| jq '.propertySources[].source \| with_entries(select(.key\|test("openfeign")))'` | Délais Feign servis par le Config Server | `connect-timeout` 2000, `read-timeout` 5000 |
| F3 | Commande EV10 | Comportement d'un appel Feign vers un service arrêté | `503` « participant-service indisponible (pas de réponse) » |
| F4 | Commande FB9 | Appel Feign de feedback-service vers event-service arrêté | `503` « event-service indisponible (pas de réponse) » |

## Axon (CQRS)

Variables utiles : `S=http://localhost:8088/api/stats`. `$EV` est l'UUID d'un événement.

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| A1 | `docker compose ps axon-server` puis `http://localhost:8024` | Axon Server (event store, bus) | `healthy` ; l'UI liste les applications connectées |
| A2 | `curl -s 'localhost:8024/v1/components/event-service/commands?context=default' \| jq -c '[.[].command]'` | Commandes enregistrées par l'agrégat | 6 commandes `EventCommands$…` |
| A3 | `curl -s -N --max-time 3 -H 'Accept: text/event-stream' "localhost:8024/v1/events?aggregateId=$EV&context=default" \| sed -n 's/^data://p' \| jq -c '{seq: .aggregateSequenceNumber, type: (.payload.type \| split("$")[1]), data: (.payload.data \| fromjson)}'` | Historique de l'agrégat (event sourcing) | `EventCreated` (0), `LogistiqueAffectee` (1), `EventCostUpdated` (2)… |
| A4 | `curl -s -X DELETE localhost:8088/api/events/$EV; curl -s -X DELETE localhost:8088/api/events/$EV \| jq -c '{status,message}'` | Règle de l'agrégat transportée par Axon Server | `204`, puis `404` « Event not found with id … » |
| A5 | `curl -s $S/admin/projection \| jq -c` | État de la projection de mon module | `enCours: true`, nombre de lignes de chaque table |
| A6 | `curl -s -D - -o /dev/null $S/summary \| grep -i x-stats-source` puis `for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{time_total}\n' $S/summary; done` | Source et latence APRÈS CQRS | `projections-cqrs` ; environ 0,020 s |
| A7 | `docker compose stop event-service participant-service logistics-service feedback-service; curl -s -o /dev/null -w '%{http_code}\n' $S/summary; docker compose --profile core start event-service participant-service logistics-service feedback-service` | Statistiques sans les services d'écriture | `200` (AVANT CQRS : 503) |
| A8 | `curl -s -X POST localhost:8088/api/participants -H "Content-Type: application/json" -d '{"nom":"Cqrs","prenom":"Demo","tache":"SERVEUR"}' >/dev/null; for i in $(seq 20); do curl -s $S/summary \| jq .nbParticipants; done \| uniq` | Cohérence à terme | l'ancienne valeur, puis la nouvelle après quelques dizaines de ms |
| A9 | `curl -s $S/summary \| jq -S -c . > avant.json; curl -s -X POST $S/admin/replay \| jq -c; sleep 3; curl -s $S/summary \| jq -S -c . \| diff - avant.json && echo identique` | Replay : `db_stats` reconstruite depuis l'event store | `202`, puis « identique » |
| A10 | `printf 'stats:\n  source: feign\n' > config-overrides/statistics-service.yml; make restart S=statistics-service` puis A6 ; `rm config-overrides/statistics-service.yml; make restart S=statistics-service` | Bascule temporaire vers la version AVANT CQRS | `feign-avant-cqrs`, environ 0,040 s, puis retour à `projections-cqrs` |
| A11 | `mvn -B -pl event-service -Dtest=EventAggregateTest test` | Tests Given / When / Then de l'agrégat | 16 tests verts |
| A12 | `mvn -B -pl statistics-service -Dtest='StatsCqrsIntegrationTest,QuerySerialisationTest' test` | Chaîne CQRS de bout en bout et sérialisation réelle des réponses | verts |

## Resilience4j
| # | Commande | Rôle | Résultat |
|---|---|---|---|
| R1 | `make panne N=1` | Panne 1 : participant-service arrêté | Retry puis circuit **OPEN** ; vue dégradée ; 503 ; puis HALF_OPEN → CLOSED |
| R2 | `make panne N=2` | Panne 2 : latence de 7 s (à chaud) | 504 « pas de réponse en 5000 ms (TimeLimiter) » |
| R3 | `make panne N=3` | Panne 3 : réponse corrompue (à chaud) | 502 « réponse incorrecte, rejetée » |
| R4 | `make panne N=4` | Panne 4 : mauvaise URL, puis correction à chaud | « injoignable (erreur réseau) après 3 tentatives », puis CLOSED |
| R5 | `make panne N=5` | Panne 5 : base de participant inaccessible | santé DOWN, circuit OPEN, puis UP et CLOSED |
| R6 | `curl -s localhost:8101/actuator/circuitbreakers \| jq` | État des circuits d'event-service | `participant-service`, `logistics-service` : CLOSED, OPEN ou HALF_OPEN |
| R7 | `curl -s localhost:8101/actuator/circuitbreakerevents \| jq -c '.circuitBreakerEvents[] \| {circuitBreakerName, type, creationTime}' \| tail` | Historique des transitions | `STATE_TRANSITION` CLOSED_TO_OPEN, OPEN_TO_HALF_OPEN, HALF_OPEN_TO_CLOSED |
| R8 | `curl -s localhost:8101/actuator/health \| jq .components.circuitBreakers` | Santé des circuits | détails par circuit ; le service reste UP (`allow-health-indicator-to-fail: false`) |
| R9 | `docker compose stop logistics-service; curl -s localhost:8088/api/logistics \| jq -c; docker compose --profile core start logistics-service` | Fallback de la Gateway | 503 « logistics-service indisponible (Gateway) : … » |
| R10 | `seq 30 \| xargs -P 30 -I{} sh -c "curl -s localhost:8088/api/events/$EV/details \| jq -r '(.avertissements // [] \| if length==0 then \"complet\" else .[0] end)'" \| sort \| uniq -c` | RateLimiter (5/s vers participant-service) : 30 lectures simultanées | mesuré : 11 « complet », 19 dégradées « trop d'appels (RateLimiter : 5 par 1000 ms) », toutes en HTTP 200 |

## LoadBalancer
| # | Commande | Rôle | Résultat |
|---|---|---|---|
| LB1 | `make up` puis `make health` | Démarrer avec les 2es instances (profil core) | `participant-service : 2 instances UP`, `statistics-service : 2 instances UP` |
| LB2 | `curl -s -H 'Accept: application/json' localhost:8761/eureka/apps/PARTICIPANT-SERVICE \| jq -r '.application.instance[] \| "\(.instanceId) \(.status)"'` | Les 2 instances dans Eureka | `participant-service:participant-service-1 UP`, `…-2 UP` |
| LB3 | `make repartition` | Répartition Gateway et Feign, puis bascule | 50 / 50 partout ; instance 2 arrêtée : 100 % sur l'instance 1, 0 erreur |
| LB4 | `for i in $(seq 10); do curl -s -D - -o /dev/null localhost:8088/api/participants \| grep -i x-instance-id; done` | Voir l'alternance à la main | instances 1 et 2 en alternance |
| LB5 | `make replay` | Replay de la projection avec 2 instances | arrêt de l'instance 2, reset sur 8105, compteurs identiques, 2 instances UP |
| LB6 | `curl -s -X POST localhost:8115/stats/admin/replay \| jq -c '{status,message}'` | Replay demandé à l'instance qui ne détient pas le jeton | `409` « … détenu par une autre instance … (make replay) » |

## Docker / Docker Compose

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| D1 | `docker version` (dans WSL) | Vérifier l'intégration WSL de Docker Desktop | Client et serveur 28.5.2 |
| D2 | `make env` | Crée `.env` depuis `.env.example`, mots de passe aléatoires (`openssl rand`) | `.env` (droits 600), ignoré par Git |
| D3 | `make up` | `docker compose --profile core up -d --build --wait`, branche Git courante lue par le Config Server | 9 conteneurs `healthy` : mysql, config-server, eureka-server, api-gateway et les 5 services métier |
| D4 | `make health` | Tests de fumée : socle, puis chaque service UP dans Eureka et joignable via la Gateway | « Tout est opérationnel » |
| D5 | `docker compose ps` | État et santé des conteneurs | `Up … (healthy)` |
| D6 | `make down` | Arrêt (volumes conservés) | — |
| D7 | `make seed` | Jeu de démonstration créé par les API via la Gateway (remplace le `DemoDataLoader`) | 6 participants, 4 événements `[Démo]`, 8 logistiques, 5 avis, coûts recalculés (1220, 1080, 250 DT) |
| D8 | `make seed` (une deuxième fois) | Relance sans doublon | « 4 événement(s) [Démo] déjà présents : rien à faire » |
| D9 | `docker run --rm -v "$PWD:/mnt" -w /mnt koalaman/shellcheck:stable scripts/*.sh` | Analyse des scripts shell | Aucun avertissement |

## Prometheus · Grafana · Zipkin
_Phase 6._

## Swagger · Postman
_Phase 7._

## Keycloak
_Retiré du périmètre (2026-11-16) : voir THEORIE Q19._

## Angular
_Phase 9._

## Jenkins · SonarQube · Trivy
_Phase 11._

## Terraform · Ansible · AWS (à exécuter après rechargement)
_Phase 12._
