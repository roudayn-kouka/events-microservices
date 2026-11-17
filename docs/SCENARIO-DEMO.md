# Scénario de démonstration (10 minutes)

Fil conducteur : **de la création d'un événement jusqu'au tableau de bord statistiques (mon module)**, puis les pannes, vues dans l'interface, dans Grafana et par Newman.
Toutes les commandes ci-dessous ont été jouées en réel. Les numéros renvoient à `COMMANDES.md` et `CAPTURES.md`.

## Préparation (30 minutes avant le jury)

```bash
cd ~/events-microservices
make up && make health          # stack complète (profil core) : tout « OK », 2 instances participant et statistics
make seed                       # jeu [Démo] (dates relatives) ; FORCE=1 pour le recréer
ls config-overrides/            # README.md seulement : aucune panne active
make newman                     # 69 requêtes, 0 échec (répétition générale)
```
Onglets ouverts : frontend http://localhost:4200, Eureka :8761, Axon Server :8024, Grafana :3001 (tableau « Resilience4j »), Zipkin :9411, Swagger :8088/swagger-ui.html, Jenkins :8080/job/events-microservices/.
Pour Jenkins et Sonar (RAM) : `docker start jenkins sonarqube` **après** la démonstration des pannes, ou bien la stack en profil lite (`make up PROFILES="--profile lite"`).

## Déroulé

| Minute | Étape | Où | Ce que le jury voit | Commande / écran |
|---|---|---|---|---|
| 0:00 | Architecture | Slide 3, Eureka | 7 applications, dont 2 instances de participant et de statistics | `make health` |
| 0:45 | Créer un participant, un événement, inscrire | Angular (Participants, Événements, Détail) | Création = commande Axon ; inscription vérifiée par Feign ; badge « servi par event-service… » | C76 |
| 2:00 | **Tableau de bord statistiques (mon module)** | Angular, Statistiques | Indicateurs, anneaux et barres ; badge `source : projections-cqrs` ; valeurs mises à jour par les projections | C75 |
| 2:45 | Event sourcing | Axon Server :8024, terminal A3 | Historique numéroté de l'agrégat créé | C44, C45 |
| 3:15 | CQRS : AVANT / APRÈS | Terminal A6, A7 | 4 appels Feign contre 0 ; statistiques toujours servies quand feedback-service est arrêté | C40/C50, C41/C51 |
| 4:00 | Panne 1 — disponibilité | `make panne N=1` + Angular, Détail + Grafana | Bandeau « Vue dégradée » dans l'UI ; circuit OPEN → HALF_OPEN → CLOSED dans Grafana | C53, C54, C67, C77 |
| 5:15 | Pannes 2 et 3 — latence, réponse corrompue | `make panne N=2`, `N=3` | 504 « TimeLimiter », 502 « réponse incorrecte » ; configuration modifiée à chaud | C55, C56 |
| 6:00 | Bulkhead : le cron ne coupe plus les utilisateurs | slide Résilience, THEORIE Q28 | Circuits `participant-service` et `participant-service-batch` séparés | `curl -s localhost:8101/actuator/circuitbreakers \| jq '.circuitBreakers \| keys'` |
| 6:30 | Les 5 pannes vérifiées automatiquement | `make newman-pannes` (lancé à 4:00 dans un 2e terminal) | 5 dossiers verts | C74 |
| 7:15 | Trace de bout en bout | Zipkin | Gateway → event-service → participant et logistics, 7 spans | C69 |
| 7:45 | Répartition et replay | `make repartition` (partie 1) ; `make replay` | 10 / 10 ; projection reconstruite « identique à avant » | C62, C64 |
| 8:30 | Pipeline | Jenkins | Étapes parallèles, quality gate OK, rapports Trivy, Push et AWS désactivés | C83, C84, C88, C90 |
| 9:30 | AWS | AWS-RUNBOOK, `make aws-check` | Terraform testé sans AWS, plan et confirmation avant tout apply | C92 |

## Plans de secours

| Imprévu | Réaction |
|---|---|
| Un service n'est pas UP | `make restart S=<service>` (environ 40 s), et pendant ce temps, montrer Swagger ou Grafana |
| Une panne reste active | `ls config-overrides/` : `make panne N=<n>` rétablit en fin de script ; à la main : écrire la valeur neutre, `POST /actuator/refresh`, puis supprimer le fichier (THEORIE Q23) |
| `newman-pannes` est rouge | Ne lancer **aucun appel** à l'API pendant son exécution : un appel parallèle pendant une panne ouvre le circuit (constaté en phase 7). Le relancer seul |
| Mémoire saturée | Arrêter Jenkins et Sonar (`docker stop jenkins sonarqube`), ou passer en profil lite |
| Pas de réseau | Tout est local, sauf AWS : montrer le runbook et `make aws-check` |
