# Théorie — questions probables à l'oral

Chaque réponse renvoie au fichier du projet qui illustre le concept. Environ 30 questions au total, ajoutées au fil des phases.

---

## Choix d'architecture

### Q1. Pourquoi des UUID pour les événements, mais des entiers pour les participants, la logistique et les feedbacks ?

- **Événement = agrégat Axon (event sourcing).** Son état n'est pas une ligne de table : c'est la suite de ses événements dans l'event store (Axon Server). L'identifiant doit exister **avant** tout stockage, au moment où la commande `CreateEventCommand` est émise. Un UUID se génère côté client ou API, sans base de données, et il est unique sur toutes les instances. C'est la pratique standard avec Axon (`@TargetAggregateIdentifier`).
- **Participant, logistique, feedback = entités JPA classiques (CRUD).** Leur base MySQL dédiée attribue l'identifiant (`GenerationType.IDENTITY`, auto-incrément). Ces services ne font pas d'event sourcing : ils publient seulement des événements métier vers Axon, pour les projections de statistics-service.
- **Conséquence pour les autres services** : feedback-service et statistics-service référencent un événement par son UUID (`String eventId`), et un participant par son entier (`Integer participantId`).
- Fichiers : `event-service/.../EventAggregate.java` (phase 3), `participant-service/.../Participant.java` (phase 2).

### Q2. Pourquoi Spring Boot 3.5 et Spring Cloud 2025.0, et pas 3.3 ou 4 ?

- **Spring Boot 3.3** : sa ligne ne reçoit plus de correctifs. Les scanners du pipeline (Trivy, SonarQube) signaleraient des failles connues.
- **Spring Boot 4** : très récent, et Axon Framework 4 n'est pas prévu pour lui.
- **Spring Boot 3.5.16** : dernière ligne 3.x, avec Spring Cloud **2025.0.3** (son appairage officiel) et Axon **4.13.3**. Même modèle de programmation que les TP (Boot 3, Jakarta, Micrometer).
- Fichier : `pom.xml` (POM parent).

_Les questions suivantes sont ajoutées au fil des phases : CQRS vs CRUD, event sourcing, états du circuit breaker, service discovery, Gateway vs LoadBalancer, Config Server, tracing, Terraform…_
