# Journal de réalisation

Heures locales (Tunis). Les durées réelles sont mesurées de la fin de la phase précédente au commit de fusion de la phase. Pour les phases 0 à 3, ce journal a été créé à la fin de la phase 3 : les heures viennent des horodatages des commits Git (`git log --date=format:%H:%M`). Les estimations sont celles de `docs/PLAN.md` (section 7), en jours de travail humain.

| Phase | Début | Fin | Durée réelle | Estimation du plan | Résultat |
|---|---|---|---|---|---|
| 0 — Initialisation | 2026-11-15, non mesuré (avant le 1er commit) | 2026-11-15 20:46 | — | 0,5 j | monorepo, POM parent, backlog JIRA, squelette des livrables |
| 1 — Socle Spring Cloud | 2026-11-15 20:46 | 2026-11-15 22:10 | 1 h 24 | 1 j | Config Server, Eureka, Gateway, MySQL, Compose |
| 2 — Services métier + Feign | 2026-11-15 22:10 | 2026-11-15 23:59 | 1 h 49 | 2 j | 5 services, 179 tests d'origine repris, `make seed` |
| 3 — CQRS Axon | 2026-11-16 15:35 | 2026-11-16 17:10 | 1 h 35 | 2 j | agrégat, publications, projections et requêtes de statistics-service, replay |
| 4 — Resilience4j et pannes | 2026-11-16 19:46 | 2026-11-16 21:10 | 1 h 24 | 1 j | 4 mécanismes sur tous les appels Feign, fallbacks, Gateway 503, 5 pannes |
| 5 — LoadBalancer | 2026-11-16 21:12 | 2026-11-16 21:40 | 28 min | 0,5 j | 2 instances, 50 / 50 Gateway et Feign, bascule sans erreur, replay à 2 instances |
| 6 — Observabilité | 2026-11-16 21:42 | 2026-11-16 22:15 | 33 min | 1 j | Prometheus (Eureka), Grafana (3 tableaux), Zipkin (trace de bout en bout), 5 alertes |
| 7 — Swagger et Postman | 2026-11-16 22:16 | 2026-11-16 22:55 | 39 min | 0,5 j | Swagger agrégé, 69 requêtes Postman, 5 pannes vérifiées par Newman |

## Phase 3 — CQRS Axon

- **Début** : 2026-11-16 15:35 — **fin** : 17:10 — **durée réelle** : 1 h 35 — estimation : 2 j.
- **Fait** : Axon Server (conteneur) ; module `events-api` ; `EventAggregate` event-sourcé et sa projection locale ; publications de participant, logistics et feedback ; statistics-service côté requêtes (projections `db_stats`, `QueryGateway`, replay) ; bascule `stats.source` pour comparer AVANT et APRÈS ; remise à zéro des volumes (autorisée), puis `make seed`.
- **Problèmes et corrections** (diagnostic, puis correction) :
  1. la numérotation des phases dans les livrables (CQRS écrit « phase 6 ») est alignée sur le plan ;
  2. XStream ne sérialise pas les records : Jackson aussi dans les tests ;
  3. `markDeleted()` court-circuitait la règle 404 : indicateur `supprime` ;
  4. un agrégat absent arrivait sans détails (409) et bloquait `calculCout` : emballage en 404 et calcul isolé ;
  5. `make restart` ne reconstruisait pas l'image, puis recréait les dépendances : `--build` et `--no-deps` ;
  6. `Integer` → `int` : NPE dans les contrats d'événements, ID passés en `Integer` ;
  7. dépôts JPA imbriqués non détectés, et nom d'entité `ProjectionModel$StatsAvis` : fichiers séparés et `@Entity(name)` ;
  8. une `List` renvoyée par une requête perdait son type à travers Axon Server (500) : `ListeStats`, et test de sérialisation réel ;
  9. moyenne affichée 4.19998 (AVG MySQL à 4 décimales) : arrondi à 2 décimales.
- **Vérifié** : 318 tests verts ; `make health` vert ; Newman 64 requêtes, 122 assertions, 0 échec ; comparaison AVANT / APRÈS mesurée (CAPTURES).
- **Reporté à la phase 4** : la Gateway répond 500, et non 503, quand un service est arrêté pendant le délai du cache du LoadBalancer.

## Phase 4 — Resilience4j et pannes

- **Début** : 2026-11-16 19:46 — **fin** : 21:10 — **durée réelle** : 1 h 24 — estimation : 1 j.
- **Fait** :
  - `common/ClientsResilients` : Retry (lectures), RateLimiter, CircuitBreaker et TimeLimiter sur chaque client Feign, et fallbacks qui nomment la cause ;
  - pannes simulées à chaud (latence, réponse corrompue, URL forcée) ;
  - vue détaillée dégradée ;
  - CircuitBreaker et fallback 503 dans la Gateway ;
  - 5 scripts de panne, rétablis automatiquement (`make panne N=…`).
- **Problèmes et corrections** :
  1. Lombok absent de `common` : loggers SLF4J explicites ;
  2. réglages Resilience4j citant des classes Feign lus par la Gateway (sans Feign) : fichier dédié `resilience-feign.yml`, demandé seulement par les 3 services concernés ;
  3. `read` d'une sortie curl sans saut de ligne : `set -e` arrêtait le script ;
  4. **retour à la normale à chaud inopérant** (le refresh gardait la source supprimée) : valeur neutre avant la suppression (THEORIE Q23).
- **Vérifié** : 334 tests verts ; les 5 pannes jouées en réel ; Newman 64 requêtes, 0 échec ; `make health` vert ; la Gateway répond 503 JSON au lieu de 500.

## Phase 5 — LoadBalancer

- **Début** : 2026-11-16 21:12 — **fin** : 21:40 — **durée réelle** : 28 min — estimation : 0,5 j.
- **Fait** : `participant-service-2` et `statistics-service-2` (profil core) ; contrôle « 2 instances UP » dans `make health` ; `make repartition` ; `make replay`.
- **Problèmes et corrections** :
  1. 3 requêtes sur 20 en 503 pendant une bascule. Il y avait deux causes : un Retry placé avant le CircuitBreaker (la réponse du fallback était déjà envoyée), et une connexion de 14 s vers l'instance arrêtée. Correction : Retry des GET dans le CircuitBreaker de chaque route, et connexion à 2 s ; résultat, 0 erreur ;
  2. un replay sur l'instance sans jeton donnait un 500 (`UnableToClaimTokenException`) : refus 409 explicite, et `make replay` (arrêt de l'instance 2, reset sur 8105), comme tu l'as demandé ;
  3. le replay de Postman passe par la Gateway : il accepte 202 ou 409.
- **Vérifié** : 336 tests verts ; 50 / 50 mesuré ; `make health` vert ; Newman 0 échec (deux exécutions).

## Phase 6 — Observabilité

- **Début** : 2026-11-16 21:42 — **fin** : 22:15 — **durée réelle** : 33 min — estimation : 1 j.
- **Fait** :
  - Micrometer Tracing et Zipkin sur 6 applications, et `feign-micrometer` ;
  - Prometheus v3.15.1 avec découverte Eureka et 5 règles d'alerte ;
  - Grafana 13.0.13 (version choisie avec toi, pour Trivy) provisionné, avec 3 tableaux de bord générés ;
  - Zipkin 3.6.1 ;
  - contrôles d'observabilité dans `make health`.
- **Problèmes et corrections** :
  1. trace coupée au changement de thread du TimeLimiter : `context-propagation` ;
  2. mesures Feign rejetées (conflit d'étiquettes) : `MeterFilter`, `feign.client.requests` ;
  3. alerte `CircuitOuvert` invisible au bout de 10 s : OPEN ou HALF_OPEN.
- **Observé, non corrigé** : 35 s après l'arrêt simultané des 2 instances de participant-service, Eureka en listait encore une. `ServiceAbsent` reste donc en attente pendant cette fenêtre ; `InstanceInjoignable` couvre ce cas.
- **Vérifié** : 337 tests verts ; trace de 7 spans ; 11 cibles UP ; `make health` vert ; Newman 0 échec.

## Phase 7 — Swagger et Postman

- **Fait** :
  - springdoc dans les 5 services (serveur `/api`, déclaré dans `common`) ;
  - Swagger agrégé dans la Gateway (`/swagger-ui.html`, routes `/docs/<service>/v3/api-docs`) ;
  - dossier OpenAPI dans Postman ;
  - collection des pannes et `make newman-pannes` ; `make newman`.
- **Problèmes et corrections** :
  1. régression de la phase 5 : les scripts de panne n'agissaient que sur la première instance de participant-service. Ils agissent maintenant sur toutes ;
  2. entre deux pannes, un circuit CLOSED pouvait encore contenir des échecs (3 sur 4, sous le minimum de 5) et s'ouvrir au premier échec suivant. Le script attend maintenant une fenêtre sans échec ;
  3. mon script de diagnostic avait laissé la panne 3 active, faute de `trap` : rétabli à la main, et les diagnostics suivants ont un `trap`.
- **Point ouvert** : un passage de `make newman-pannes` a reçu un 503 (3 tentatives, 1 s) sur la panne 3, alors que event-service journalisait « réponse incorrecte ». La panne 3 seule passe, et l'enchaînement complet est passé ensuite sans échec. La cause n'est pas établie avec certitude : piste à vérifier, une instance de participant-service redémarrée par la panne 1 et pas encore rafraîchie.
- **Vérifié** : `make newman` 69 requêtes, 0 échec ; `make newman-pannes` 5 dossiers verts ; Swagger agrégé, 5 OpenAPI.

## Périmètre révisé (2026-11-16, à la demande)

- Phases 4 à 7 : inchangées. Phase 8 (Keycloak) : retirée, tickets « Won't do », sécurisation de la Gateway expliquée dans THEORIE.
- Phase 9 : Angular allégé (0,5 à 1 j), sans connexion. Phase 10 : un seul commit (seuil JaCoCo bloquant).
- Phase 11 : arrêt avant toute action sur `~/devops`. Phase 12 : 0,5 j, sans `terraform apply`. Phase 13 : inchangée.
