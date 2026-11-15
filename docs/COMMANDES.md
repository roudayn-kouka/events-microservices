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
_Phase 1._

## Eureka
_Phase 1._

## Gateway
_Phase 1._

## MySQL
_Phase 1._

## Feign
_Phase 2._

## Axon (CQRS)
_Phase 3._

## Resilience4j
_Phase 4._

## LoadBalancer
_Phase 5._

## Docker / Docker Compose
_Phase 1._

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
