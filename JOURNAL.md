# Journal de réalisation

Heures locales (Tunis). Les durées réelles sont mesurées de la fin de la phase précédente au commit de fusion de la phase. Pour les phases 0 à 3, ce journal a été créé à la fin de la phase 3 : les heures viennent des horodatages des commits Git (`git log --date=format:%H:%M`). Les estimations sont celles de `docs/PLAN.md` (section 7), en jours de travail humain.

| Phase | Début | Fin | Durée réelle | Estimation du plan | Résultat |
|---|---|---|---|---|---|
| 0 — Initialisation | 2026-11-15, non mesuré (avant le 1er commit) | 2026-11-15 20:46 | — | 0,5 j | monorepo, POM parent, backlog JIRA, squelette des livrables |
| 1 — Socle Spring Cloud | 2026-11-15 20:46 | 2026-11-15 22:10 | 1 h 24 | 1 j | Config Server, Eureka, Gateway, MySQL, Compose |
| 2 — Services métier + Feign | 2026-11-15 22:10 | 2026-11-15 23:59 | 1 h 49 | 2 j | 5 services, 179 tests d'origine repris, `make seed` |
| 3 — CQRS Axon | 2026-11-16 15:35 | 2026-11-16 17:10 | 1 h 35 | 2 j | agrégat, publications, projections et requêtes de statistics-service, replay |

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

## Périmètre révisé (2026-11-16, à la demande)

- Phases 4 à 7 : inchangées. Phase 8 (Keycloak) : retirée, tickets « Won't do », sécurisation de la Gateway expliquée dans THEORIE.
- Phase 9 : Angular allégé (0,5 à 1 j), sans connexion. Phase 10 : un seul commit (seuil JaCoCo bloquant).
- Phase 11 : arrêt avant toute action sur `~/devops`. Phase 12 : 0,5 j, sans `terraform apply`. Phase 13 : inchangée.
