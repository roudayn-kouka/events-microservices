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
| Correctif — circuit du cron | 2026-11-17 11:40 | 2026-11-17 12:35 | 55 min | — | circuit `participant-service-batch` pour le cron, 3 × `newman-pannes` verts |
| 9 — Frontend Angular (allégé) | 2026-11-17 12:37 | 2026-11-17 12:48 | 11 min (+ ~25 min préparées pendant les runs Newman) | 0,5 à 1 j | 5 écrans via la Gateway, graphiques SVG, `make e2e` 13/13 |
| 10 — Seuil JaCoCo bloquant | 2026-11-17 12:48 | 2026-11-17 12:53 | 5 min | 0,5 j (réduite à un commit) | `jacoco:check` lignes ≥ 70 %, branches ≥ 60 %, preuve du blocage |
| 11 — Pipeline Jenkins, Sonar, Trivy | 2026-11-17 12:53 | 2026-11-17 13:36 | 43 min | 1 j | job local `events-microservices`, 3 builds verts, CVE CRITICAL de 8 à 3 |
| 12 — AWS préparé (sans apply) | 2026-11-17 13:20 | 2026-11-17 13:38 | 18 min (en parallèle des builds Jenkins) | 0,5 j | Terraform 5 tests simulés, ansible-lint, 16 tests des scripts, runbook |
| 13 — Livrables finaux | 2026-11-17 13:38 | 2026-11-17 13:53 | 15 min | 0,5 j | SCENARIO-DEMO et PRESENTATION-PLAN à jour, vérification finale complète, tag v1.0.0 |
| Sonar — fiabilité et hotspots | 2026-11-17 14:05 | 2026-11-17 14:43 | 38 min (dont 2 builds Jenkins d'environ 11 min) | — | bug corrigé avec test, hotspot revu Safe : fiabilité A, hotspots revus 100 % (A), quality gate OK |
| Préparation du push, release v1.0.1 | 2026-11-17 14:50 | 2026-11-17 15:30 | 40 min (hors interruption) | — | captures cochées, chiffres harmonisés, contrôles du push verts, tag v1.0.1 sur main |

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

## Correctif après la phase 7 — 503 au lieu de 502 sur la panne 3

- **Début** : 2026-11-17 11:40 (nouvelle session) — **fin** : 12:35 — **durée réelle** : 55 min.
- **Captures** : C44 à C74 réalisées par l'utilisatrice, cochées (commit `422824f`). État vérifié ensuite : `make health` vert, `config-overrides/` vide (README seul), tous les circuits CLOSED, 2 instances UP.
- **Diagnostic** (échantillonnage de `/actuator/circuitbreakers` toutes les 0,5 s pendant `make newman-pannes`, 503 reproduit au premier passage) :
  - l'hypothèse « circuit resté OPEN ou HALF_OPEN après le scénario précédent » est **réfutée** : CLOSED, 0 échec, au début de la panne 3 ;
  - cause : le cron `calculCout` (seconde :00) appelait participant-service pendant la panne. Ses 3 tentatives (le Retry enveloppe le circuit) faisaient 3 échecs, et l'inscription de Newman 2 de plus : le minimum de 5 était atteint, le circuit s'ouvrait et la 3e tentative recevait 503 « circuit ouvert ».
- **Premier correctif, remplacé** : `hors_cron` dans les scripts (attendre que la panne ne croise pas :00). Il corrigeait Newman, mais pas une démo manuelle (Postman, Angular). L'utilisatrice a demandé de corriger la cause : `hors_cron` est retiré.
- **Correctif retenu** (isolation par cas d'usage, THEORIE Q28) : `ClientsResilients.enTraitementDeFond(...)`. Le cron utilise les instances `participant-service-batch` et `logistics-service-batch` (CircuitBreaker et RateLimiter), sans Retry ; le TimeLimiter reste celui du service.
- **Incident pendant la vérification** : un 3e passage a échoué à la panne 4, parce que **mes propres `curl` d'exploration de l'API** (dont `/details`) tombaient pendant la panne. Zipkin le montre : deux `/details` simultanés à 11:09:05 UTC. Les vérifications suivantes se sont faites sans aucun appel en parallèle.
- **Vérifié** :
  - 339 tests verts, dont 2 nouveaux : les échecs du batch laissent le circuit utilisateur CLOSED à 0 échec ;
  - `make newman-pannes` 3 fois de suite, 0 échec. Pendant ces passages, le cron a croisé 5 pannes, dont la panne 3 (journal : `participant-service-batch : réponse incorrecte, rejetée`), sans effet sur les tests ;
  - `make newman` : 69 requêtes, 137 assertions, 0 échec ; `make health` vert.

## Phase 9 — Frontend Angular (allégé)

- **Début** : 2026-11-17 12:37 (branche) — **fin** : 12:48. Le code a été écrit entre 12:05 et 12:35, pendant les passages de `make newman-pannes`, sans aucun appel à l'API.
- **Fait** :
  - base Angular 20 de `feature/feedback` (configuration, `package-lock.json`, styles, cadre), lue dans le dépôt d'équipe sans le modifier ;
  - services réécrits pour les routes `/api` de la Gateway et les UUID d'événements ;
  - 5 écrans : Statistiques (mon module : 6 indicateurs, anneaux et barres en SVG, source CQRS et instance affichées), Événements (création = commande Axon), Détail (vue composée, inscription par Feign, **bandeau de vue dégradée**), Participants, Logistique, Avis (règles de feedback-service) ;
  - conteneur `frontend` : build dans `node:22`, nginx non root, `/api` transmis à la Gateway, port 4200, dans les profils core et lite ;
  - `make front-test`, `make e2e` (Playwright en conteneur) ; contrôles frontend dans `make health`. Locale `fr` pour les nombres et les dates.
- **Problèmes et corrections** :
  1. sélecteurs ambigus dans le script e2e (« Nom » contenu dans « Prénom », libellé d'un `select` qui inclut ses options) : correspondance exacte et attribut `name` ;
  2. le 400 attendu de la règle métier « événement non terminé » apparaissait comme une erreur console de Chromium : filtré, seuls les 5xx et les erreurs JavaScript comptent ;
  3. données laissées par un passage e2e interrompu : supprimées par l'API (participant 29, un événement « Atelier UI »).
- **Vérifié** : 10 tests Vitest ; build de 101 ko compressé ; `make e2e` 13/13 avec nettoyage ; vue dégradée affichée pendant l'arrêt de participant-service ; `make health` vert (avec le frontend) ; `make newman` 69 requêtes, 0 échec.
- **Non fait (périmètre)** : connexion Keycloak (ticket 71 « Won't Do »).

## Phase 10 — Seuil JaCoCo bloquant (un seul commit)

- **Début** : 2026-11-17 12:48 — **fin** : 12:53 — **durée réelle** : 5 min.
- **Fait** : exécution `jacoco:check` dans le POM parent (phase `verify`, `haltOnFailure`), avec lignes ≥ 70 % et branches ≥ 60 % dans chaque service métier ; seuils en propriétés Maven.
- **Constat** : la couverture était déjà de 88 à 98 % (lignes) dans les 5 services. Aucun test n'a été ajouté pour franchir le seuil.
- **Vérifié** : `mvn verify` vert, 5 × « All coverage checks have been met » ; avec un seuil de 99 %, statistics-service échoue (`BUILD FAILURE`, 0,97 < 0,99) ; `make health` vert.
- **Tickets** : 72 (handler global et `@Valid`, déjà en place) et 74 « Done » ; 73 (WireMock) « Won't Do », justification dans ETAT-FINAL.
- **Tags** : `v0.6.0` posé après coup sur la fusion de la phase 6 (le plan le prévoyait, il manquait) et `v0.10.0` sur cette fusion.

## Phase 11 — Pipeline Jenkins, SonarQube, Trivy (accord donné le 2026-11-17)

- **Début** : 12:53 — **fin** : 13:36 (fusion) — **durée réelle** : 43 min, dont 3 builds complets d'environ 11 min.
- **`~/devops`, ce qui a été fait** :
  - lu en lecture seule ;
  - `docker start` de `jenkins` et `sonarqube` (Nexus n'a pas été démarré, inutile ici) ;
  - un **nouveau** job, `events-microservices`, ajouté dans `jenkins_home`, et redémarrage de Jenkins pour le charger.
- **`~/devops`, ce qui n'a pas été touché** : aucun conteneur, volume ou job supprimé ni modifié. Les jobs `TestAWSCredentials` et `docker-spring-boot-pipeline` sont intacts.
- **Dépôt local sans push** : le conteneur Jenkins n'a pas de montage vers `~/events-microservices`, et le recréer était interdit. J'ai donc lancé un conteneur `events-git` (git daemon, dépôt monté en `:ro`, réseau `devops_default`) : Jenkins clone `git://events-git/events-microservices`. `file://` est bloqué par le plugin Git.
- **Identifiants** : aucun mot de passe nécessaire.
  - le job a été créé par fichier et le premier build a été déclenché par la scrutation ;
  - Sonar utilise l'identifiant Jenkins existant `sonar-token`, celui du job de l'équipe, sans jamais l'afficher.
- **Mémoire** : la pile events est passée en profil lite pendant les analyses (environ 6 Go libres sur 19).
- **Fait** :
  - `Jenkinsfile` avec détection des changements (`ci/changements.sh`), étapes parallèles (Sonar et frontend ; une branche par image avec Trivy), quality gate par l'API (`ci/quality-gate.sh`), JUnit et rapports archivés ;
  - `PUSH_IMAGES`, `DEPLOY_AWS` et `TRIVY_BLOQUANT` à `false` ;
  - webhook GitHub préparé en commentaire et documenté (APRES-PUSH.md).
- **Problèmes et corrections** :
  1. `alpine/git` n'inclut pas `git-daemon` (conteneur en boucle de redémarrage) : image dédiée `ci/git-daemon` ;
  2. `readProperties` demande un plugin absent (Pipeline Utility Steps) : lecture par `sed` ;
  3. **Trivy, build 1** : 17 à 19 vulnérabilités HIGH et CRITICAL par service, toutes dans les bibliothèques des JAR, et 57 dans l'image nginx. Montées de patch (Tomcat 10.1.59, Jackson 2.21.7, Netty 4.1.137) et `apk upgrade` : statistics-service passe de 18 à 7 (CRITICAL de 8 à 3), le frontend de 57 à 0 ;
  4. la première montée n'a eu **aucun effet** : sans `clean`, Maven jugeait le JAR à jour et gardait les anciennes bibliothèques. Diagnostic par `unzip -l`, puis `mvn clean verify` partout, y compris dans le pipeline ;
  5. Tomcat 10.1.58, cité par la base Trivy, n'est pas publié sur Maven Central : 10.1.59 ;
  6. la ligne de résumé Trivy était vide pour une image sans vulnérabilité : `grep -m1 … || echo`.
- **Vérifié** :
  - builds 1, 2 et 3 en SUCCESS : 339 tests, seuil JaCoCo, quality gate Sonar OK, 9 images, 9 rapports Trivy ;
  - détection des changements testée (frontend seul → `SERVICES=frontend`) ;
  - `make health` vert (profil lite) et `make newman` sans échec après la montée des versions.
- **Non corrigé (documenté, THEORIE Q30)** : Spring 6.2 (correctif en 7.x seulement, incompatible avec Axon 4), BouncyCastle, Guava et httpcore5 (transitives ou montée mineure).
- **Rien à demander** : aucun mot de passe ni jeton manquant.

## Phase 12 — AWS préparé, sans apply

- **Début** : 13:20 (pendant les builds Jenkins) — **fin** : 13:38 (fusion) — **durée réelle** : 18 min.
- **Fait** :
  - `infra/aws/terraform` : VPC, sous-réseau public, security group limité à `admin_cidr`, EC2 avec `LabInstanceProfile`, IMDSv2, disque gp3 chiffré, IP élastique ;
  - `infra/aws/ansible` : Docker, Compose, swap, dossier ;
  - `deployer.sh` (plan → confirmation → apply → Ansible → rsync → compose lite → tests de fumée) et `detruire.sh` ;
  - `make aws-check`, `aws-up`, `aws-down` ; `docs/AWS-RUNBOOK.md`.
- **Problèmes et corrections** :
  1. **`terraform test` a trouvé un vrai bug** que seul un `apply` aurait révélé : AWS refuse les accents dans les descriptions de security group. Les descriptions sont passées en ASCII ;
  2. une assertion comparait une `list` à un tuple (toujours faux) : `contains` et `length` ;
  3. ShellCheck : `source` non suivi (SC1091) : `-x -P SCRIPTDIR`.
- **Vérifié** (`make aws-check`, vert) :
  - `terraform fmt` et `validate` ;
  - `terraform test` : 5 runs sur un fournisseur simulé (instance du lab, disque chiffré, IMDSv2, accès limité à `admin_cidr`, refus de `0.0.0.0/0` et des types hors lab) ;
  - Ansible : `--syntax-check` et `ansible-lint` (profil production, 0 échec) ;
  - ShellCheck ;
  - 16 tests des scripts avec de faux `aws`, `terraform`, `ssh` et `rsync` (ordre des étapes, confirmation obligatoire, arrêt sans identifiants, mode à blanc, destruction confirmée) ;
  - filtre `rsync` vérifié à blanc : 10 JAR et `.git` envoyés, ni `.env` ni `node_modules`.
- **Non fait, volontairement** : aucun `terraform apply` ni appel AWS. Le playbook n'a pas été rejoué dans un conteneur Ubuntu (Docker dans Docker, sans systemd) : son exécution réelle est dans le runbook.

## Phase 13 — Livrables finaux

- **Début** : 13:38 — **fin** : 13:53 (fusion) — **durée réelle** : 15 min.
- **Fait** :
  - SCENARIO-DEMO réécrit : le déroulé réel, sans Keycloak, avec l'UI pendant les pannes, le bulkhead, Jenkins, AWS et les plans de secours ;
  - PRESENTATION-PLAN : lignes des phases 9 à 12 et sections AVANT / APRÈS (cron, frontend, JaCoCo, pipeline, AWS) ;
  - README : livrables, URL, arborescence ; CAPTURES C75 à C97 ; ETAT-FINAL : vérification finale et état des conteneurs ;
  - backlog : 83 tickets « Done », 6 « Won't Do ».
- **Jenkins** : pour un job Pipeline, la scrutation suit la branche du **dernier build** (`feature/ci`, renommée entre-temps), d'où « No changes ». Correction : une référence `feature/ci` temporaire sur `develop` a provoqué un build avec la nouvelle configuration (build 4, sur `develop`), puis elle a été supprimée.
- **Vérification finale (core)** :
  - le premier `make health` a échoué sur Prometheus, qui venait de redémarrer (cibles « unknown ») ; il est vert après le premier scrape ;
  - `make newman` 0 échec, `make e2e` 13/13, `make newman-pannes` 5/5 lancé seul ;
  - `config-overrides/` vide, circuits CLOSED.
- **Branches** : les branches de travail ont été renommées selon la convention `feature/EVT-xx-…` (EVT-55, 12, 13, 14, 16, 17).

## Après la phase 13 — SonarQube : fiabilité D et hotspots non revus

- **Début** : 14:05 — **fin** : 14:43 (fusion) — **durée réelle** : 38 min.
- **Accès** : Sonar refuse l'accès anonyme, et je n'ai pas cherché de jeton sur le poste. Le pipeline, qui utilise l'identifiant Jenkins `sonar-token`, exporte maintenant les bugs et les hotspots (`rapports/sonar-*.json`, archivés) : diagnostic fait à partir du build 6.
- **Diagnostic** :
  1. **Bug** `java:S5783` (CRITICAL) dans `PannesSimuleesTest` : dans `assertThrows(Exception.class, …)`, deux appels pouvaient échouer (`getContentAsString`, `readTree`). Le test pouvait passer pour une mauvaise raison ;
  2. **Hotspot** `java:S3752` (CSRF) sur `IndisponibleController` : le fallback accepte toutes les méthodes HTTP. Ce n'est pas un vrai risque : forward des routes, réponse 503 en lecture seule, ni session ni cookie.
- **Corrections** :
  - test précis (corps lu hors de la lambda, `JsonProcessingException`) ;
  - `IndisponibleControllerTest` (5 méthodes, même 503, pas de cookie) pour étayer le verdict ;
  - revue versionnée `ci/sonar-hotspots-revus.txt`, appliquée par `ci/revue-hotspots.sh` via `api/hotspots/change_status` (Safe, avec commentaire) ;
  - THEORIE Q31.
- **Vérifié** :
  - 344 tests verts (339 + 5), seuil JaCoCo respecté ;
  - script de revue testé avec un faux `curl` sur le rapport réel, ShellCheck ;
  - **build 7** : 0 bug, `reliability_rating` 1.0 (**A**), `security_hotspots_reviewed` **100 %**, `security_review_rating` 1.0 (**A**), quality gate OK.
- **Non modifié** : `main` reste sur `v1.0.0` ; ces correctifs sont sur `develop`.

## Préparation du push et release v1.0.1 (état final)

- **Début** : 14:50 — **fin** : 15:30. La session a été interrompue par la limite d'usage, puis reprise au point noté.
- **Captures** :
  - C1 à C93 et C101 cochées ;
  - restent C94 (webhook GitHub, impossible avant le push), C95 à C97 (déploiement AWS), C98 à C100 et C102 (Sonar avant correction, le bug, le hotspot, le journal du build) et C103 (GitHub for Jira, après le push).
- **Jira** : `jira/README.md` documente l'import (EVT-1 à EVT-89, tâches importées comme Sous-tâches). 15 tâches ont directement une Epic pour parent : leur rattachement est à vérifier dans Jira.
- **Cohérence** : un bloc « Résultats finaux » identique dans README, ETAT-FINAL et PRESENTATION-PLAN, plus THEORIE Q31 :
  - 344 tests ; Newman 69 requêtes et 137 assertions ;
  - Sonar (build 9) : 0 bug, fiabilité, sécurité et maintenabilité A, hotspots revus 100 %, couverture 77,7 %, duplication 0,7 % ;
  - Trivy 6 à 8 par service Java, 0 pour le frontend.
  - Écarts corrigés : C91 (339 → 344), slide 18 (339 → 344), COMMANDES N2 (64/122 → 69/137) et CI8 (Trivy par service). Les chiffres datés des phases passées sont conservés.
- **APRES-PUSH** : section « GitHub for Jira » (capture C103) ; push de toutes les branches et de tous les tags.
- **Pipeline** : la note de maintenabilité et le nombre de tests figurent dans le journal du build.
- **Contrôles avant le push** :
  - aucun secret versionné : `.env` est ignoré, `.env.example` ne contient que `change-me`, la seule clé versionnée est une clé **publique** de test Terraform ;
  - aucun secret dans l'historique : recherche de clés privées, de clés AWS et de jetons Sonar, GitHub et Slack, et d'affectations de mots de passe ; seulement des faux positifs (noms d'identifiants Jenkins, `http_tokens`, `js-tokens`) ;
  - `mvn clean verify` : 344 tests, 0 échec, 5 seuils JaCoCo respectés ;
  - `make health` et `make newman` (69 requêtes, 137 assertions) verts ;
  - aucune mention de Claude dans les commits ; aucun remote ; `git status` vide après la release.
- **Release** : `develop` fusionnée dans `main` (`--no-ff`) ; tag `v1.0.1`.

## Périmètre révisé (2026-11-16, à la demande)

- Phases 4 à 7 : inchangées. Phase 8 (Keycloak) : retirée, tickets « Won't do », sécurisation de la Gateway expliquée dans THEORIE.
- Phase 9 : Angular allégé (0,5 à 1 j), sans connexion. Phase 10 : un seul commit (seuil JaCoCo bloquant).
- Phase 11 : arrêt avant toute action sur `~/devops`. Phase 12 : 0,5 j, sans `terraform apply`. Phase 13 : inchangée.
