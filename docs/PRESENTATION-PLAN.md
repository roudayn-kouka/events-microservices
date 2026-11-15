# Plan de la présentation (par technologie)

Présentation individuelle. Fil rouge : **statistics-service est mon module** (côté requêtes CQRS, projections, dashboard). Chaque slide indique les commandes, l'extrait de code et les captures associées (numéros de `CAPTURES.md`). Le contenu est complété à chaque phase.

| # | Slide | Contenu | Captures |
|---|---|---|---|
| 1 | Titre | Events FST en microservices Spring Cloud — Roudayna Kouka, GL5 | — |
| 2 | Du monolithe aux microservices | Diagnostic des 5 branches, ce qui casse (relations JPA, transactions, statistiques) | — |
| 3 | Architecture | Diagramme (README), ports, profils Docker Compose | — |
| 4 | Git et JIRA | GitFlow, commits EVT-xx, backlog importé | C1, C2 |
| 5 | Maven et versions | POM parent, Boot 3.5.16 / Cloud 2025.0.3 / Axon 4.13.3, justification | C3, C4 |
| 6 | Config Server | config-repo Git, un YAML par service | _phase 1_ |
| 7 | Eureka | Services enregistrés, 2 instances | _phase 1_ |
| 8 | API Gateway | Routes `lb://`, CORS, sécurité | _phase 1_ |
| 9 | MySQL | Une base et un utilisateur par service | _phase 1_ |
| 10 | Services et Feign | Relations JPA remplacées par des ID et des appels Feign | _phase 2_ |
| 11 | **CQRS Axon (mon module)** | Agrégat, commandes, projections dans statistics-service, replay | _phase 3_ |
| 12 | Résilience4j | Configuration, fallbacks, 5 pannes | _phase 4_ |
| 13 | LoadBalancer | Répartition prouvée | _phase 5_ |
| 14 | Observabilité | Prometheus, Grafana (circuit breaker), Zipkin | _phase 6_ |
| 15 | Swagger et Postman | API agrégées, collection | _phase 7_ |
| 16 | Keycloak | Realm, rôles, JWT | _phase 8_ |
| 17 | Angular | Écrans, **dashboard statistiques** | _phase 9_ |
| 18 | Qualité | Tests, JaCoCo ≥ 70 % sur statistics | _phase 10_ |
| 19 | Jenkins, SonarQube, Trivy | Pipeline multi-services | _phase 11_ |
| 20 | AWS | Cible A, `make aws-up`, coût par heure | _phase 12_ |
| 21 | Démonstration | Scénario de 10 minutes | — |
| 22 | Bilan | Ce qui est fait, ce qui reste | — |
