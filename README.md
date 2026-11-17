# Events — microservices Spring Cloud

Gestion des événements de la Faculté des Sciences de Tunis (FST) : événements, participants, logistique, feedbacks et **statistiques**, en architecture microservices Spring Cloud avec CQRS (Axon).

Projet individuel — DS Architecture des composants, GL5 — Roudayna Kouka.
Module mis en avant : **statistics-service** (côté requêtes CQRS).

Plan détaillé : [docs/PLAN.md](docs/PLAN.md). Ce README est complété à chaque phase.

## État d'avancement

| Phase | Contenu | État |
|---|---|---|
| 0 | Monorepo, POM parent, GitFlow, backlog JIRA, livrables | Terminée |
| 1 | Socle : Config Server, Eureka, Gateway, MySQL, Docker Compose | Terminée |
| 2 | Services métier + Feign (statistics en version AVANT CQRS), `make seed` | Terminée |
| 3 | CQRS Axon : agrégat event-sourcé, publications, projections et requêtes de statistics-service, replay | Terminée |
| 4 | Resilience4j (Retry, RateLimiter, CircuitBreaker, TimeLimiter), fallbacks, 5 pannes (`make panne N=1..5`) | Terminée |
| 5 | LoadBalancer : 2 instances de participant et de statistics, `make repartition`, `make replay` | Terminée |
| 6 | Observabilité : Prometheus (découverte Eureka), Grafana (3 tableaux de bord), Zipkin, alertes | Terminée |
| 7 | Swagger agrégé, Postman (local et AWS), `make newman`, `make newman-pannes` | Terminée |
| 9 | Angular 20 allégé : statistiques (graphiques SVG), événements, participants, logistique, avis, via la Gateway | Terminée |
| 10 | Seuil JaCoCo bloquant dans `mvn verify` (lignes ≥ 70 %, branches ≥ 60 %) | Terminée |
| 11 | Pipeline Jenkins (job local `events-microservices`) : détection des changements, Sonar et quality gate, Trivy, étapes parallèles ; push et AWS désactivés | Terminée |

## Prérequis

- WSL Ubuntu avec Docker Desktop (intégration WSL activée), JDK 17, Maven 3.9.
- Ports libres : 4200, 8088, 8888, 8761, 3307 (voir [docs/PLAN.md](docs/PLAN.md), section 4). Les ports de `~/devops` (8080, 8081, 9000, 9090, 3000) ne sont pas utilisés.

## Démarrage local

```bash
cd ~/events-microservices
make build    # mvn verify : compilation et tests
make up       # crée .env si besoin, construit les images, démarre et attend « healthy »
make health   # tests de fumée
make seed     # jeu de données de démonstration (par les API, via la Gateway)
make panne N=1   # scénario de panne 1 à 5, rétabli automatiquement (scripts/pannes)
make repartition # répartition de charge entre les 2 instances (Gateway, Feign, bascule)
make replay      # replay de la projection CQRS (2 instances de statistics-service)
make front-test  # frontend : tests Vitest et build (node:22)
make e2e         # frontend : parcours complet dans Chromium (Playwright)
make down     # arrêt (données conservées)
```

`make up` lit `config-repo/` sur la **branche Git courante** : une modification de configuration doit être commitée pour être servie.

## URL

| Composant | URL |
|---|---|
| **Frontend Angular** | http://localhost:4200 |
| API Gateway (point d'entrée des API) | http://localhost:8088/api/... |
| Routes de la Gateway | http://localhost:8088/actuator/gateway/routes |
| Eureka | http://localhost:8761 |
| Axon Server (event store, commandes, requêtes) | http://localhost:8024 |
| Prometheus (cibles, alertes) | http://localhost:9091 |
| Grafana (dossier « Events FST ») | http://localhost:3001 |
| Zipkin (traces) | http://localhost:9411 |
| Config Server | http://localhost:8888/{service}/default |
| MySQL | `localhost:3307` (une base et un utilisateur par service) |

## Arborescence

```
events-microservices/
├── pom.xml                 POM parent (Boot 3.5.16, Spring Cloud 2025.0.3, Axon 4.13.3)
├── config-server/          Configuration centralisée (Git + surcharges à chaud)
├── eureka-server/          Annuaire de services
├── api-gateway/            Point d'entrée : routes lb://, CORS
├── common/                 Erreurs JSON communes (ApiError), en-tête X-Instance-Id
├── events-api/             Contrats des événements Axon (records, sans dépendance)
├── participant-service/    Participants (8102, db_participant)
├── logistics-service/      Logistique et coût réservé (8103, db_logistics)
├── event-service/          Événements, Feign, calculCout (8101, db_event)
├── feedback-service/       Avis sur les événements terminés, Feign (8104, db_feedback)
├── statistics-service/     ⭐ Mon module : côté requêtes CQRS (8105, db_stats), replay
├── config-repo/            Configuration de référence (un YAML par service)
├── config-overrides/       Surcharges à chaud des scénarios de panne (non versionnées)
├── docker/mysql/init/      Création des bases et utilisateurs par service
├── docker-compose.yml      Stack locale (profils core, lite…)
├── Makefile                make up / down / health…
├── scripts/                init-env.sh, health.sh, seed-demo.sh
├── jira/                   Backlog importable (EVT-1 à EVT-89)
└── docs/                   Plan, commandes, captures, théorie, scénario, présentation
```
