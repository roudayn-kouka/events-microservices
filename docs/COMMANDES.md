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
| J1 | Import de `jira/backlog.csv` (procédure dans `jira/README.md`) | Créer le backlog | Tickets EVT-1 à EVT-88, hiérarchie epic → story → tâche |

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

## Postman

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| N1 | `python3 postman/generate.py` | Régénérer la collection et les environnements (local, AWS) | `postman/*.json` |
| N2 | `docker run --rm --network host -v "$PWD/postman:/etc/newman" postman/newman:6-alpine run events-microservices.postman_collection.json -e local.postman_environment.json` | Exécuter toute la collection en ligne de commande (Newman) | participant-service : 11 requêtes, 19 assertions, 0 échec |
| N3 | Postman → *Import* → `postman/events-microservices.postman_collection.json` et `postman/local.postman_environment.json` | Utiliser la collection dans Postman | Un dossier par service, avec le tableau des correspondances d'endpoints |

## Feign
_Phase 2 : event-service et feedback-service._

## Axon (CQRS)
_Phase 3._

## Resilience4j
_Phase 4._

## LoadBalancer
_Phase 5._

## Docker / Docker Compose

| # | Commande | Rôle | Résultat |
|---|---|---|---|
| D1 | `docker version` (dans WSL) | Vérifier l'intégration WSL de Docker Desktop | Client et serveur 28.5.2 |
| D2 | `make env` | Crée `.env` depuis `.env.example`, mots de passe aléatoires (`openssl rand`) | `.env` (droits 600), ignoré par Git |
| D3 | `make up` | `docker compose --profile core up -d --build --wait`, branche Git courante lue par le Config Server | 4 conteneurs `healthy` : mysql, config-server, eureka-server, api-gateway |
| D4 | `make health` | Tests de fumée du socle | « Tout est opérationnel » |
| D5 | `docker compose ps` | État et santé des conteneurs | `Up … (healthy)` |
| D6 | `make down` | Arrêt (volumes conservés) | — |

## Prometheus · Grafana · Zipkin
_Phase 6._

## Swagger · Postman
_Phase 7._

## Keycloak
_Phase 8._

## Angular
_Phase 9._

## Jenkins · SonarQube · Trivy
_Phase 11._

## Terraform · Ansible · AWS (à exécuter après rechargement)
_Phase 12._
