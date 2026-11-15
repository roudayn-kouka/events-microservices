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

## Socle Spring Cloud

### Q3. À quoi sert le Config Server, et pourquoi un dépôt Git ?

- **Rôle** : il centralise la configuration de tous les services. Chaque service ne connaît que son nom et l'adresse du Config Server ; le reste (port, routes, URL Eureka, Resilience4j…) lui est servi au démarrage. Une modification se fait à un seul endroit, sans reconstruire d'image.
- **Pourquoi Git** : la configuration est versionnée, comme le code. On sait qui a changé quoi et quand, et on peut revenir en arrière. Le Config Server sert le contenu **commité** d'une branche (label), jamais les fichiers non commités du disque (démontré : COMMANDES C5).
- **Ordre de priorité** (profil `composite`) : `config-overrides/` (bascules à chaud des scénarios de panne, non versionnées), puis `config-repo/`. Dans `config-repo/`, `<service>.yml` l'emporte sur `application.yml` (commun).
- Fichiers : `config-server/src/main/resources/application.yml`, `config-repo/`.

### Q4. Pourquoi l'URI Git du Config Server n'a-t-elle pas le préfixe `file:` ?

- Avec une URI `file:`, Spring Cloud Config **ne clone pas** le dépôt : il travaille directement dedans et y fait un `checkout` de la branche demandée. Il aurait donc changé la branche de mon dossier de travail.
- Constaté à la phase 1 : il essayait de créer `/repo/.git/index.lock`, et le montage en lecture seule l'en a empêché.
- Avec un chemin simple (`/repo`), JGit fait un **vrai clone** dans un dossier temporaire (`basedir`). Le dépôt source reste intact, et il est monté en lecture seule dans le conteneur par sécurité.
- Fichiers : `config-server/src/main/resources/application.yml`, `docker-compose.yml`.

### Q5. Qu'est-ce que la découverte de services (Eureka) ?

- Chaque instance **s'enregistre** au démarrage auprès d'Eureka (nom logique, adresse, port), puis envoie un battement de cœur toutes les 5 s. Une instance qui se tait est retirée après 15 s.
- Les clients (Gateway, Feign) ne codent aucune adresse : ils demandent à Eureka les instances de `participant-service`, puis en choisissent une (équilibrage de charge côté client).
- Démonstration : dès qu'un service est arrêté, il disparaît du tableau de bord ; s'il en démarre une deuxième instance, elle y apparaît sans aucune reconfiguration.
- Réglages (démo rapide) : `config-repo/application.yml` (5 s) et `config-repo/eureka-server.yml` (auto-préservation désactivée).

### Q6. Que fait l'API Gateway, et que signifie `lb://` ?

- **Point d'entrée unique** : le frontend et Postman ne connaissent que `localhost:8088`. La Gateway applique les règles transverses : routage, CORS, sécurité (JWT, phase 8), traçage.
- `uri: lb://participant-service` : « lb » signifie *load-balanced*. L'adresse réelle est résolue dans Eureka à chaque requête, et Spring Cloud LoadBalancer répartit entre les instances.
- `StripPrefix=1` : `/api/participants/5` arrive au service sous la forme `/participants/5`.
- Sans instance enregistrée : `503 Service Unavailable` (démontré : COMMANDES W2).
- Fichiers : `config-repo/api-gateway.yml`, test `api-gateway/src/test/java/.../ApiGatewayRoutesTest.java`.

### Q7. Pourquoi une base par microservice, et comment est-ce garanti ici ?

- **Principe** : chaque service est le seul propriétaire de ses données. Un autre service passe par son API (Feign) ou par ses événements (Axon), jamais par sa base. On peut ainsi faire évoluer, redémarrer ou mettre à l'échelle un service sans casser les autres.
- **Ici** : un seul serveur MySQL (accepté par l'énoncé), mais **une base et un utilisateur par service**, avec des droits limités à cette base. `event_user` ne voit que `db_event`, et l'accès à `db_participant` est refusé (`ERROR 1044`, COMMANDES S3).
- **Conséquence** : plus de jointure ni de transaction entre services. Les relations JPA `Event↔Participant`, `Event→Logistics` et `Feedback→Event/Participant` deviennent des identifiants et des appels Feign (phase 2).
- Fichier : `docker/mysql/init/01-bases-par-service.sh`.

### Q8. Que se passe-t-il si un service démarre avant le Config Server ?

- L'import `configserver:` est obligatoire (`fail-fast: true`) : sans configuration, mieux vaut ne pas démarrer que démarrer avec de mauvais réglages.
- Mais avec `spring-retry`, le service **réessaie** (2 s, puis ×1,5 jusqu'à 10 s, 20 essais) avant d'abandonner. Démontré : Config Server et Gateway redémarrés ensemble, la Gateway redevient saine en 12 s (COMMANDES C6).
- Docker Compose ajoute `depends_on: condition: service_healthy` pour l'ordre du premier démarrage.
- Fichiers : `api-gateway/src/main/resources/application.yml`, `docker-compose.yml`.

_Les questions suivantes sont ajoutées au fil des phases : CQRS vs CRUD, event sourcing, états du circuit breaker, Gateway vs LoadBalancer, tracing, Terraform…_
