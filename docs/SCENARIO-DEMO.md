# Scénario de démonstration (10 minutes)

Fil conducteur : **de la création d'un événement jusqu'au dashboard statistiques**, avec les pannes et le circuit breaker visibles dans Grafana.
Ce scénario est complété au fil des phases ; les étapes ci-dessous sont la trame validée.

**Préparation (avant le jury)** : `make up`, `make health`, puis `make seed` (jeu `[Démo]` aux dates relatives : deux événements terminés et notés, deux à venir). Pour repartir d'une base vide, `docker compose down -v` supprime les volumes : à réserver à une remise à zéro volontaire.

| Minute | Étape | Où | Ce que le jury voit |
|---|---|---|---|
| 0:00 | Architecture et démarrage (`docker compose … up -d`) | Terminal, Eureka | Tous les services UP dans Eureka (2 instances participant et statistics) |
| 1:00 | Connexion ADMIN | Angular + Keycloak | Page de connexion Keycloak, puis menus ADMIN |
| 1:30 | Créer participants, logistique, événement ; affecter | Angular | Commandes envoyées à event-service (CQRS) |
| 3:00 | Dashboard statistiques (**mon module**) | Angular | Graphiques mis à jour par les projections Axon |
| 4:00 | Événements dans Axon Server | Axon UI :8024 | Flux d'événements de l'agrégat |
| 4:30 | Pannes 1 à 5 (scripts) | Terminal, Postman, Grafana | Fallbacks, circuit OPEN → HALF_OPEN → CLOSED |
| 7:30 | Trace d'un appel | Zipkin | Gateway → event-service → Feign → participant-service |
| 8:30 | Répartition de charge | Terminal | Script : environ 50/50 entre les 2 instances |
| 9:00 | Replay de la projection | Terminal + dashboard | `db_stats` reconstruite à l'identique |
| 9:30 | Pipeline et AWS | Jenkins, runbook | Étapes du pipeline ; `make aws-up` prêt |
