# État final

Ce document est complété au fil des phases. Il recense ce qui est fait et testé, et ce qui reste à lancer (push, pipeline Jenkins réel, AWS), avec le fichier qui l'explique.

---

## Reprise des tests des branches d'équipe (phase 2)

### Méthode

Les 5 branches sont des copies du même monolithe : beaucoup de classes de test y sont identiques. J'ai donc compté les **versions distinctes** (même nom de classe et même contenu) : **30 versions, soit 179 tests**. Les 126 tests de `feature/feedback` comprennent ses tests propres et les copies des tests des autres features.

Statuts utilisés :
- **Repris** : la logique de test est identique ; seuls les imports et les annotations Boot 3 changent (`getStatusCode()`, `@MockitoBean`).
- **Adapté** : même intention, mais le chemin, le code HTTP ou la relation testée change (endpoint REST, ID au lieu d'entité, Feign simulé).
- **Supprimé** : le comportement testé n'existe plus. La raison est donnée pour chaque suppression.
- **Ajouté** : nouveau test, pour un comportement nouveau (Feign, lecture par lot, validation, module `common`).

### participant-service — origine `feature/participant`

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `ParticipantServicesImplMockitoTest` | 9 | 9 | 0 | 0 | `getStatus()` → `getStatusCode()` |
| `ParticipantServicesImplIntegrationTest` | 7 | 7 | 0 | 0 | idem (`@DataJpaTest` sur H2) |
| `ParticipantRestControllerMockitoTest` | 8 | 0 | 8 | 0 | chemins REST `/participants`, 201 à la création, 204 à la suppression, `@MockitoBean` |
| `ParticipantRestControllerIntegrationTest` | 6 | 0 | 6 | 0 | chemins REST, 201/204 |
| `MapperTest` (partie participant) | 1 | 0 | 1 | 0 | plus d'« événements courts » dans le participant (relation supprimée) |
| `MapperTest` (parties Event et Logistics) | 2 | — | — | — | déplacés dans event-service et logistics-service |
| **Total d'origine** | **31** | **16** | **15** | **0** | |
| Ajoutés | | | | | 10 : lecture par lot (3), recherche (3), validation (1), erreur JSON commune (1), `X-Instance-Id` (1), `Mapper` null (1) |

La variante de `ParticipantServicesImplMockitoTest` présente sur `feature/feedback` ne diffère que par une variable intermédiaire : elle est couverte par la même reprise.

Résultat : `mvn verify` → **41 tests verts** dans participant-service (31 d'origine + 10 ajoutés), plus **5** dans `common`.

### logistics-service — origine `feature/logistics` (+ 3 tests de `feature/participant`)

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `LogisticsServiceImplMockTest` | 4 | 4 | 0 | 0 | JUnit 4 (`MockitoJUnitRunner`) → JUnit 5 (`MockitoExtension`) |
| `LogisticsServiceImplTest` | 4 | 3 | 1 | 0 | MySQL → H2 ; `testDeleteLogistics` attend désormais une `ResponseStatusException` 404 au lieu d'une `IllegalArgumentException` (HTTP 500) |
| `LogisticsControllerMockTest` | 4 | 4 | 0 | 0 | JUnit 5 ; le JSON du DTO a les mêmes champs que l'entité |
| `LogisticsControllerTest` | 4 | 4 | 0 | 0 | JUnit 5, H2 |
| `LogisticsControllerListMockTest` (feature/participant) | 2 | 2 | 0 | 0 | JUnit 5 |
| `MapperTest` (partie logistique, feature/participant) | 1 | 1 | 0 | 0 | — |
| **Total d'origine** | **19** | **18** | **1** | **0** | |
| Ajoutés | | | | | 10 : 404 (2), coût réservé (4), lecture par lot (2), validation (1), aller-retour du `Mapper` (1) |

Résultat : `mvn verify` → **29 tests verts** dans logistics-service (19 d'origine + 10 ajoutés).

### event-service — origine `feature/event` (+ `feature/event-statistics`, `feature/participant`, `feature/feedback`)

Toutes les classes d'origine sont **adaptées** : l'identifiant devient un UUID, et les repositories Participant et Logistics deviennent des clients Feign simulés.

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `EventServicesMockTest` (feature/event) | 5 | 0 | 5 | 0 | JUnit 4 → 5, `int` → UUID, clients Feign simulés |
| `EventServicesImplTest` (feature/event) | 4 | 0 | 4 | 0 | MySQL → H2, « id > 0 » → « id est un UUID » |
| `EventServicesAffectationMockTest` (feature/event-statistics) | 10 | 0 | 9 | 1 | relations → ensembles d'ID. `addAffectLog` sauvegarde toujours l'événement (les ID sont dans `db_event`). Sans logistique : liste vide au lieu de `null`. Le coût réservé est calculé par logistics-service |
| `EventServicesImplMockitoTest` (feature/feedback) | 10 | 0 | 10 | 0 | participant absent = 404 renvoyé par Feign ; `getStatus()` → `getStatusCode()` |
| `EventRestControllerMockTest` (feature/participant) | 5 | 0 | 4 | 1 | fusionné avec la variante suivante (4 + 4 → 4 tests qui gardent les vérifications des deux) ; chemins `/events…`, 201, JSON avec ID |
| `EventRestControllerMockTest` (feature/event-statistics) | 5 | 0 | 4 | 1 | voir ligne précédente |
| `EventRestControllerMockitoTest` (feature/feedback) | 5 | 0 | 4 | 1 | `EventWriteRequest` → `EventRequest` ; le contrôleur renvoie un `EventDTO` |
| `MapperTest` (partie Event, feature/participant) | 1 | 0 | 1 | 0 | `EventDTO` vide → `EventRequest` vide |
| `DemoDataLoaderMockTest` (feature/event-statistics) | 2 | 0 | 0 | 2 | voir « Suppressions » |
| **Total d'origine** | **47** | **0** | **41** | **6** | 41 tests d'origine → 37 méthodes (4 fusions) |
| Ajoutés | | | | | 28 : `RemoteCall` 404/503 (4), service : UUID et dates (2), affectations par ID, détails, organisateur absent, panne (7), contrôleur : lecture, 404 `ApiError`, validation, mise à jour, suppression, détails, recalcul (9), `Mapper` (3), repository : `@ElementCollection`, doublon de description (3) |

**Suppressions (6) et raisons :**
- `testAddParticipant`, `addParticipant` (×2) et `addParticipantMapsRequestAndReturnsServiceResult` (4 tests) : la création d'un participant (`POST /event/addPart`) appartient désormais à participant-service, où elle est testée (`ParticipantServicesImplMockitoTest`, `ParticipantRestControllerMockitoTest`) ;
- `DemoDataLoaderMockTest` (2 tests) : le `CommandLineRunner` du monolithe écrivait d'un coup dans les tables participant, logistique et événement. Avec une base par service, c'est impossible. Il est remplacé par `scripts/seed-demo.sh`, qui passe par la Gateway (EVT-42).

Résultat : `mvn verify` → **65 tests verts** dans event-service (37 d'origine + 28 ajoutés), couverture JaCoCo **96 %** des instructions.

### feedback-service — origine `feature/feedback`

Les autres classes de test de `feature/feedback` sont des copies des tests des autres branches. Elles sont comptées avec leur service : event-service ci-dessus, et statistics-service pour les tests `EventStats*`. Les tests paramétrés comptent pour une méthode ; le nombre d'exécutions est entre parenthèses.

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `FeedbackServiceTest` | 20 (24) | 0 | 20 | 0 | repositories Event et Participant → clients Feign simulés ; événement absent = 404 renvoyé par Feign ; inscription lue dans `participantIds` ; `getStatus()` → `getStatusCode()` ; UUID. Dans `doesNotMislabelOtherDatabaseErrorsAsDuplicates`, la contrainte « autre » devient une contrainte NOT NULL, car il n'y a plus de clé étrangère |
| `FeedbackControllerIntegrationTest` | 10 (21) | 0 | 10 | 0 | l'événement et le participant viennent de clients Feign simulés (`@MockitoBean`) au lieu d'entités persistées ; chemins sans context-path ; ID non numérique testé sur le participant (l'événement est un UUID) ; `/event-stats` → `/feedback/moyennes` (source des statistiques) |
| `FeedbackConcurrencyIntegrationTest` | 1 | 0 | 1 | 0 | clients Feign simulés ; toujours exactement un 201 et un 409, garantis par la contrainte d'unicité |
| `FeedbackRepositoryTest` | 6 | 0 | 6 | 0 | identifiants au lieu d'entités `Event` et `Participant` à persister |
| `RequestDtoMappingTest` | 5 | 0 | 0 | 5 | voir « Suppressions » |
| **Total d'origine** | **42 (53)** | **0** | **37** | **5** | |
| Ajoutés | | | | | 10 : `RemoteCall` (3), panne d'event-service et de participant-service (2), `/moyennes` sans appel distant (2), une seule vérification distante par lecture (1), 503 au format `ApiError` (1), moyennes groupées en base (1) |

**Suppressions (5) et raison :** `RequestDtoMappingTest` testait `EventWriteRequest`, `ParticipantWriteRequest` et `LogisticsWriteRequest`. Ce sont les DTO d'écriture des événements, participants et logistique du monolithe, pas ceux du feedback. Leurs remplaçants vivent dans les services propriétaires et y sont testés :
- `EventRequest` → `Event` : `MapperTest` d'event-service, 2 tests ;
- `ParticipantDTO` : `MapperTest` de participant-service ;
- `LogisticsDTO` : `MapperTest` de logistics-service.

Les références d'entités (`toReferenceEntity`, ensembles `participants` et `logistics` imbriqués) n'existent plus, puisque les relations sont devenues des ID.

Résultat : `mvn verify` → **62 exécutions vertes** dans feedback-service (47 méthodes), couverture JaCoCo **96 %** des instructions.

### ⭐ statistics-service (mon module) — origine `feature/event-statistics` (+ `feature/event`, `feature/feedback`) — VERSION AVANT CQRS

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `EventStatsServiceImplMockTest` (feature/event-statistics) | 9 | 0 | 9 | 0 | les trois repositories simulés deviennent la `StatsSource` simulée ; relations → ID ; requêtes JPA par date → filtre ; UUID |
| `EventStatsServiceImplMockTest` (variante feature/feedback) | 9 | 0 | 9 | 0 | **fusionnée** avec la précédente (9 + 9 → 9) : l'assertion `averageRating` est gardée dans `testGetEventStats` |
| `EventStatsServiceImplIntegrationTest` | 4 | 0 | 4 | 0 | plus de base locale avant CQRS : les 4 clients Feign sont remplacés par des « services distants » en mémoire ; le vrai service et la vraie `FeignStatsSource` sont testés ensemble |
| `EventStatsControllerMockTest` | 5 | 0 | 5 | 0 | `@WebMvcTest` + `@MockitoBean` ; chemins `/stats/...` |
| `EventStatsControllerNoMockTest` (feature/event) | 5 | 0 | 5 | 0 | faux service écrit à la main, conservé ; il implémente aussi `source()` |
| `BusinessMetricsConfigTest` | 3 | 0 | 3 | 0 | mêmes jauges et mêmes valeurs, lues dans la `StatsSource` |
| **Total d'origine** | **35** | **0** | **35** | **0** | 35 tests d'origine → 26 méthodes (9 fusions) |
| Ajoutés | | | | | 19 : `FeignStatsSource` (7), service : lots, ID orphelin, tâche inconnue, moyenne pondérée, panne (5), contrôleur : `X-Stats-Source`, 404, 400, 503 (4), `RemoteCall` (2), jauge NaN en panne (1) |

Résultat : `mvn verify` → **45 tests verts**, couverture JaCoCo **98 %** des instructions et 88 % des branches (objectif EVT-74 : ≥ 70 %).

### Bilan de la phase 2

Unité : un test `@Test` d'une version distincte de classe, comme l'inventaire initial (30 versions, **179 tests**). Les 4 tests paramétrés de feedback, comptés à part dans son tableau, ne sont pas dans l'inventaire.

| Service | Tests d'origine | Repris | Adaptés | Fusionnés | Supprimés | Ajoutés | Tests du service |
|---|---:|---:|---:|---:|---:|---:|---:|
| participant-service | 40 | 16 | 15 | 9 ¹ | 0 | 10 | 41 |
| logistics-service | 19 | 18 | 1 | 0 | 0 | 10 | 29 |
| event-service | 47 | 0 | 37 | 4 ² | 6 | 28 | 65 |
| feedback-service | 38 | 0 | 33 (+4 paramétrés) | 0 | 5 | 10 | 47 méthodes (62 exécutions) |
| statistics-service ⭐ | 35 | 0 | 26 | 9 ³ | 0 | 19 | 45 |
| common | — | — | — | — | — | 5 | 5 |
| **Total** | **179** | **34** | **112** | **22** | **11** | **82** | **232 méthodes (247 exécutions)** |

1. La variante `feature/feedback` de `ParticipantServicesImplMockitoTest` ne diffère que par une variable intermédiaire. Elle est couverte par la reprise des 9 tests de `feature/participant`.
2. Les deux variantes d'`EventRestControllerMockTest` sont fusionnées : 4 + 4 → 4.
3. Les deux variantes d'`EventStatsServiceImplMockTest` sont fusionnées : 9 + 9 → 9.

**Les 11 suppressions et leurs raisons** :
- 4 tests de création de participant dans event-service : ce rôle appartient à participant-service, où il est testé ;
- 2 tests de `DemoDataLoader` : un chargeur qui écrit dans 3 bases est impossible ; il est remplacé par `scripts/seed-demo.sh` (EVT-42) ;
- 5 tests des DTO d'écriture du monolithe dans feedback (`RequestDtoMappingTest`) : ils sont remplacés par les `MapperTest` des services propriétaires.

## Phase 3 — CQRS Axon : tests

| Module | Avant la phase 3 | Après | Ajoutés | Adaptés (même scénario, nouvelle vérification) |
|---|---:|---:|---:|---|
| events-api (nouveau) | — | 14 | 14 | — (contrat JSON de chaque événement) |
| event-service | 65 | 88 | 23 : 16 `AggregateTestFixture`, 3 d'intégration CQRS (event store JPA sur H2), 3 de traduction d'erreurs, 1 de `calculCout` isolé | 33 tests de service : « l'événement est sauvegardé » devient « la commande est envoyée » (CommandGateway simulée) ; aucun supprimé |
| participant-service | 41 | 44 | 3 (publication) | aucun : seuls un `EventGateway` simulé et un Axon local en test sont ajoutés |
| logistics-service | 29 | 31 | 2 (publication) | aucun |
| feedback-service | 62 | 65 | 3 (publication, et rien publié si l'avis est refusé) | aucun (construction du service avec le paramètre `EventGateway`) |
| statistics-service ⭐ | 45 | 63 | 18 : projection (5), source projections (3), traduction des erreurs de requête (5), intégration CQRS de bout en bout avec replay (1), sérialisation réelle Axon (3), arrondi (1) | 4 tests d'intégration fixés sur `stats.source=feign` (ils traversent désormais aussi le bus de requêtes) |

Tous modules confondus : **318 tests verts** (`mvn verify`) ; Newman : 64 requêtes, 122 assertions, 0 échec ; `make health` entièrement vert ; couverture JaCoCo de statistics-service : **97 %**.

## Phase 4 — Résilience : tests et vérifications

| Module | Avant | Après | Ajoutés |
|---|---:|---:|---|
| common | 5 | 19 | 14 : Retry 3 fois puis fallback, POST jamais retenté, 404 ni retenté ni compté, circuit OPEN puis CLOSED, TimeLimiter, RateLimiter (429), réponse incorrecte, enveloppe des seuls clients Feign ; pannes simulées (latence, réponse corrompue, actuator épargné, URL forcée et sa correction) |
| event-service | 88 | 90 | 2 : vue détaillée dégradée ; une erreur métier n'est pas masquée |
| api-gateway | 4 | 4 | — (le test « 503 » vérifie maintenant le message du fallback) |

Aucun test existant supprimé. Dans les tests des règles métier, `events.resilience.enabled: false` : la résilience est testée à part, dans `common`.

Total : **334 tests verts**. Les 5 pannes ont été jouées en réel et rétablies (`make panne N=1` à `5`). Newman : 64 requêtes, 0 échec ; `make health` entièrement vert.

## Phase 5 — Répartition de charge : tests et vérifications

| Module | Avant | Après | Ajoutés |
|---|---:|---:|---|
| statistics-service | 63 | 65 | 2 : replay sur l'instance qui détient le jeton ; refus 409 sur l'autre, sans laisser le processeur arrêté |

Total : **336 tests verts**. Vérifié en réel :
- répartition 50 / 50 par la Gateway et par Feign, et bascule sans erreur (`make repartition`) ;
- `make replay` avec 2 instances ;
- `make health` (2 instances UP chacun) ;
- Newman 64 requêtes, 0 échec. Avec 2 instances, le replay de Postman accepte 202 ou le 409 explicite.

## Phase 6 — Observabilité : tests et vérifications

| Module | Avant | Après | Ajoutés |
|---|---:|---:|---|
| common | 19 | 20 | 1 : mesures Feign renommées, mesures du client HTTP de Spring conservées |

Total : **337 tests verts**. Vérifié en réel :
- 11 cibles Prometheus UP, découvertes par Eureka ;
- 3 tableaux de bord provisionnés ; requêtes vérifiées (p95, métriques métier, mémoire) ;
- trace de 7 spans sur 4 services ;
- traceId dans les logs, y compris dans le thread du TimeLimiter ;
- alertes : `CircuitOuvert` « firing » pendant une panne, 5 règles saines ;
- `make health` vert (contrôles d'observabilité ajoutés) ; Newman 64 requêtes, 0 échec.

## Correctif du circuit du cron (après la phase 7)

| Module | Avant | Après | Ajoutés |
|---|---:|---:|---|
| common | 20 | 22 | 2 : les échecs d'un traitement de fond ouvrent `participant-service-batch` et laissent le circuit des utilisateurs CLOSED ; hors traitement de fond, retour au circuit des utilisateurs |

Total : **339 tests verts**. `make newman-pannes` 3 fois de suite, 0 échec. Pendant ces passages, le cron a croisé 5 pannes, dont la panne 3.

## Phase 9 — Frontend Angular : tests et vérifications

- 10 tests Vitest (`make front-test`) : routes de la Gateway, en-têtes `X-Stats-Source` et `X-Instance-Id`, messages des fallbacks, calcul des graphiques.
- `make e2e` (Chromium, Playwright) : 13 contrôles. Création de participant, d'événement (commande Axon) et de logistique ; inscription par Feign ; refus métier 400 dans l'écran Avis ; nettoyage ; aucune erreur JavaScript ni réponse 5xx.
- Vue dégradée affichée dans l'UI pendant l'arrêt des 2 instances de participant-service.

## Phase 10 — Qualité : seuil JaCoCo bloquant

Règle dans le POM parent (`jacoco:check` à la phase `verify`, `haltOnFailure`) : **lignes ≥ 70 % et branches ≥ 60 %** pour chaque service métier. Seuils surchargeables : `-Djacoco.minimum.lignes=…`.

| Service | Lignes | Branches | Instructions |
|---|---:|---:|---:|
| ⭐ statistics-service | 97,7 % | 87,5 % | 96,9 % |
| event-service | 95,8 % | 88,9 % | 95,9 % |
| participant-service | 96,0 % | 83,3 % | 97,7 % |
| logistics-service | 88,6 % | 90,0 % | 94,2 % |
| feedback-service | 96,6 % | 97,1 % | 97,1 % |

Preuve que le seuil est bloquant : `mvn -pl statistics-service -am verify -Djacoco.minimum.lignes=0.99` donne `BUILD FAILURE` (« lines covered ratio is 0.97, but expected minimum is 0.99 »).
Le handler d'exceptions global (`common`) et les DTO `@Valid` existent depuis les phases 2 et 4 (ticket 72). Les tests WireMock (ticket 73) ne sont pas faits : les clients Feign sont testés par un faux service distant (`ClientsResilientsTest`) et, en réel, par les 5 pannes vérifiées par Newman.

## Phase 11 — Pipeline Jenkins : vérifications

| Build | Déclenchement | Résultat | Durée | Remarque |
|---|---|---|---|---|
| 1 | scrutation (premier build, tout reconstruit) | SUCCESS | 11 min | tests 339, quality gate OK, 9 images ; Trivy statistics-service 18 (8 CRITICAL), frontend 57 |
| 2 | scrutation (POM modifié → tout) | SUCCESS | ≈ 11 min | Tomcat 10.1.56, Jackson, Netty, `apk upgrade` : frontend 0 |
| 3 | scrutation (POM modifié → tout) | SUCCESS | ≈ 11 min | Tomcat 10.1.59 : statistics-service 7 (3 CRITICAL) |
| 4 | scrutation, premier build sur `develop` (après la fusion) | SUCCESS | ≈ 11 min | checkout de `origin/develop` ; Push et AWS « skipped » |

Trivy, dernier build : services Java 6 à 8 vulnérabilités (3 à 4 CRITICAL), frontend 0. Restent Spring Framework 6.2 (correctif seulement en 7.x), BouncyCastle 1.80 et Guava (transitives) ; justification dans THEORIE Q30.
Sonar : la couverture JaCoCo des 5 services métier est importée ; Gateway, Config, Eureka et `common` n'ont pas de rapport JaCoCo.

## Phase 12 — AWS : vérifications sans AWS

`make aws-check` : Terraform fmt et validate ; `terraform test` 5/5 (fournisseur simulé) ; Ansible `--syntax-check` et `ansible-lint` (production, 0 échec) ; ShellCheck sur tous les scripts ; 16/16 tests des scripts de déploiement. Aucun appel AWS, aucun `apply`.

## Vérification finale (phase 13, 2026-11-17, stack complète, profil core)

| Contrôle | Résultat |
|---|---|
| `mvn clean verify` | 339 tests verts, seuil JaCoCo respecté dans les 5 services |
| `make health` | « Tout est opérationnel. » (2 instances participant et statistics, Prometheus, Grafana, Zipkin, frontend) |
| `make newman` | 69 requêtes, 137 assertions, 0 échec |
| `make newman-pannes` | 5 scénarios vérifiés et rétablis (et 3 passages consécutifs après le correctif du cron) |
| `make e2e` | 13 contrôles `OK` dans Chromium |
| `make aws-check` | Terraform 5/5, ansible-lint 0 échec, ShellCheck, 16/16 tests des scripts |
| Jenkins `events-microservices` | builds 1 à 4 en SUCCESS |
| Après les tests | `config-overrides/` vide (README seul), tous les circuits CLOSED |

## État des conteneurs laissé en place

- Pile events : profil **core** (complet), démarrée.
- `~/devops` : `jenkins` et `sonarqube` démarrés (ils étaient arrêtés). `nexus` n'a pas été démarré. Pour les arrêter : `docker stop jenkins sonarqube`.
- `events-git` (git daemon en lecture seule, pour Jenkins) : démarré. À arrêter après le push : `bash ci/git-daemon.sh stop`.

## Ce qui reste à faire par l'utilisatrice

| Quoi | Où |
|---|---|
| Captures (C1 à C43 des phases 0 à 2, C75 et suivantes) | `docs/CAPTURES.md` |
| Publier le dépôt, webhook GitHub, push Docker Hub (facultatif) | `docs/APRES-PUSH.md` |
| Déploiement AWS (Learner Lab démarré), puis destruction | `docs/AWS-RUNBOOK.md` |

