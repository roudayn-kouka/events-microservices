# Events — microservices Spring Cloud

Gestion des événements de la Faculté des Sciences de Tunis (FST) : événements, participants, logistique, feedbacks et **statistiques**, en architecture microservices Spring Cloud avec CQRS (Axon).

Projet individuel — DS Architecture des composants, GL5 — Roudayna Kouka.
Module mis en avant : **statistics-service** (côté requêtes CQRS).

Plan détaillé : [docs/PLAN.md](docs/PLAN.md). État final : [docs/ETAT-FINAL.md](docs/ETAT-FINAL.md).

## Architecture

<p align="center">
  <img src="docs/images/architecture-events-microservices.gif" alt="Architecture Events FST : Angular, API Gateway, 5 microservices avec une base MySQL chacun, Axon Server (CQRS), Config Server, Eureka, observabilité Prometheus/Grafana/Zipkin, CI/CD Jenkins/SonarQube/Trivy et déploiement AWS Terraform + Ansible" width="100%">
</p>

- **Entrée** : Angular (nginx :4200, proxy `/api`) → **API Gateway** (:8088, routes `lb://`).
- **5 microservices métier** : event (commandes, agrégat), participant (Feign, 2 instances), logistics (réservations, coût), feedback (notes, avis) et **statistics** (requêtes CQRS, 2 instances), chacun avec **sa propre base MySQL** (:3307).
- **Bus CQRS** : Axon Server (:8024) — bus de commandes, d'événements, de requêtes et event store.
- **Socle** : Config Server (:8888, config-repo Git) et Eureka (:8761, annuaire).
- **Observabilité** : Prometheus (:9091, 11 cibles, 5 alertes), Grafana (:3001, 3 tableaux de bord), Zipkin (:9411, traces Micrometer/Brave).
- **CI/CD et qualité** : Jira (89 tickets), GitHub (`v1.0.0`), Jenkins (étapes parallèles), SonarQube (quality gate A), Trivy, Docker.
- **Cloud AWS** : Terraform (VPC, SG, EC2) + Ansible (Docker et stack) vers une EC2 t3.large, prêts à déployer.

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
| 12 | AWS préparé sans rien créer : Terraform (EC2, tests simulés), Ansible, `make aws-up` / `aws-down`, AWS-RUNBOOK | Terminée (déploiement à lancer : AWS-RUNBOOK.md) |
| 13 | Livrables finaux, tag `v1.0.0` | Terminée |
| 8 | Keycloak | Retirée du périmètre (THEORIE Q19) |

## Résultats finaux

| Indicateur | Résultat final (build Jenkins n° 9, commit `d910ee1`, 2026-11-17) |
|---|---|
| Tests | **344** verts (`mvn clean verify`, comptés aussi par Sonar) ; seuil JaCoCo respecté dans les 5 services (statistics-service : 97,7 % des lignes) |
| Postman / Newman | **69** requêtes, **137** assertions, 0 échec ; `make newman-pannes` : 5 pannes sur 5 |
| Frontend | 10 tests Vitest ; `make e2e` : 13 contrôles dans Chromium |
| SonarQube | quality gate **Passed** ; 0 bug, fiabilité **A** ; 0 vulnérabilité, sécurité **A** ; 26 code smells, maintenabilité **A** ; couverture **77,7 %** ; duplication **0,7 %** ; 4 356 lignes de code |
| Security Hotspots | 1 seul, revu à **100 %** (**A**) : `java:S3752` (CSRF) sur le fallback `IndisponibleController`, **Safe**, car il accepte toutes les méthodes pour les forward des routes, répond 503 en lecture seule, sans session ni cookie (THEORIE Q31) |
| Trivy (HIGH + CRITICAL corrigeables) | services Java : 6 à 8 (3 CRITICAL, 4 pour eureka-server), dont statistics-service 7 (contre 18 au build 1) ; frontend 0 (contre 57) |
| Jenkins | builds 1 à 9 en SUCCESS ; Push et AWS désactivés par défaut |

## Livrables

| Document | Contenu |
|---|---|
| [COMMANDES](docs/COMMANDES.md) | Toutes les commandes, par technologie, avec le résultat attendu |
| [CAPTURES](docs/CAPTURES.md) | Liste des captures d'écran (C1 à C97) |
| [SCENARIO-DEMO](docs/SCENARIO-DEMO.md) | Démonstration de 10 minutes, préparation et plans de secours |
| [THEORIE](docs/THEORIE.md) | Questions du jury et réponses (Q1 à Q31) |
| [PRESENTATION-PLAN](docs/PRESENTATION-PLAN.md) | Plan des slides, avec extraits AVANT / APRÈS |
| [ETAT-FINAL](docs/ETAT-FINAL.md) | Ce qui est fait et testé, ce qui reste |
| [APRES-PUSH](docs/APRES-PUSH.md) | Publication GitHub, webhook Jenkins, Docker Hub |
| [AWS-RUNBOOK](docs/AWS-RUNBOOK.md) | Déploiement sur l'EC2 et destruction |
| [JOURNAL](JOURNAL.md) | Heures réelles, problèmes et corrections, phase par phase |
| [Schéma d'architecture](docs/images/architecture-events-microservices.gif) | Vue d'ensemble animée de l'architecture (GIF) |

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
make aws-check   # AWS : Terraform, Ansible, scripts vérifiés sans rien créer
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
| Swagger agrégé | http://localhost:8088/swagger-ui.html |
| Jenkins (`~/devops`), job du projet | http://localhost:8080/job/events-microservices/ |
| SonarQube (`~/devops`) | http://localhost:9000/dashboard?id=events-microservices |

## Arborescence

```
events-microservices/
├── pom.xml                 POM parent (Boot 3.5.16, Spring Cloud 2025.0.3, Axon 4.13.3)
├── config-server/          Configuration centralisée (Git + surcharges à chaud)
├── eureka-server/          Annuaire de services
├── api-gateway/            Point d'entrée : routes lb://, CORS
├── common/                 Erreurs JSON, en-tête X-Instance-Id, résilience Feign (circuits utilisateurs et -batch)
├── events-api/             Contrats des événements Axon (records, sans dépendance)
├── participant-service/    Participants (8102, db_participant)
├── logistics-service/      Logistique et coût réservé (8103, db_logistics)
├── event-service/          Événements, Feign, calculCout (8101, db_event)
├── feedback-service/       Avis sur les événements terminés, Feign (8104, db_feedback)
├── statistics-service/     ⭐ Mon module : côté requêtes CQRS (8105, db_stats), replay
├── config-repo/            Configuration de référence (un YAML par service)
├── config-overrides/       Surcharges à chaud des scénarios de panne (non versionnées)
├── frontend/               Angular 20 (5 écrans, statistiques en SVG), nginx, tests Vitest, e2e Playwright
├── docker/                 MySQL (init), Prometheus, Grafana (tableaux générés)
├── docker-compose.yml      Stack locale (profils core, lite, observability, frontend)
├── Makefile                make up / health / panne / newman / e2e / aws-check… (make help)
├── scripts/                init-env, health, seed-demo, pannes/ (5 scénarios), répartition, replay
├── postman/                Collection (69 requêtes), pannes, environnements local et AWS
├── Jenkinsfile             Pipeline multi-services (phase 11)
├── ci/                     Détection des changements, quality gate, job Jenkins, git daemon
├── infra/aws/              Terraform (EC2), Ansible, make aws-up / aws-down (phase 12)
├── jira/                   Backlog importable (EVT-1 à EVT-89)
└── docs/                   Plan, commandes, captures, théorie, scénario, présentation, après-push, AWS, état final
    └── images/             architecture-events-microservices.gif (schéma d'architecture)
```
