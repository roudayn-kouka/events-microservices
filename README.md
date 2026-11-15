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
| 2 | Services métier + Feign | En cours : participant, logistics, event faits (3/5) |

## Prérequis

- WSL Ubuntu avec Docker Desktop (intégration WSL activée), JDK 17, Maven 3.9.
- Ports libres : 8088, 8888, 8761, 3307 (voir [docs/PLAN.md](docs/PLAN.md), section 4). Les ports de `~/devops` (8080, 8081, 9000, 9090, 3000) ne sont pas utilisés.

## Démarrage local

```bash
cd ~/events-microservices
make build    # mvn verify : compilation et tests
make up       # crée .env si besoin, construit les images, démarre et attend « healthy »
make health   # tests de fumée
make down     # arrêt (données conservées)
```

`make up` lit `config-repo/` sur la **branche Git courante** : une modification de configuration doit être commitée pour être servie.

## URL

| Composant | URL |
|---|---|
| API Gateway (point d'entrée des API) | http://localhost:8088/api/... |
| Routes de la Gateway | http://localhost:8088/actuator/gateway/routes |
| Eureka | http://localhost:8761 |
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
├── participant-service/    Participants (8102, db_participant)
├── logistics-service/      Logistique et coût réservé (8103, db_logistics)
├── event-service/          Événements, Feign, calculCout (8101, db_event)
├── config-repo/            Configuration de référence (un YAML par service)
├── config-overrides/       Surcharges à chaud des scénarios de panne (non versionnées)
├── docker/mysql/init/      Création des bases et utilisateurs par service
├── docker-compose.yml      Stack locale (profils core, lite…)
├── Makefile                make up / down / health…
├── scripts/                init-env.sh, health.sh
├── jira/                   Backlog importable (EVT-1 à EVT-88)
└── docs/                   Plan, commandes, captures, théorie, scénario, présentation
```
