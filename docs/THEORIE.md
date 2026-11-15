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

### Q9. Pourquoi avoir réduit les délais d'Eureka, et que ferait-on en production ?

| Réglage | Valeur par défaut | Valeur du projet | Fichier |
|---|---|---|---|
| Battement de cœur (`lease-renewal-interval-in-seconds`) | 30 s | 5 s | `config-repo/application.yml` |
| Expiration d'une instance muette (`lease-expiration-duration-in-seconds`) | 90 s | 15 s | `config-repo/application.yml` |
| Premier enregistrement (`initial-instance-info-replication-interval-seconds`) | 40 s | 5 s | `config-repo/application.yml` |
| Rafraîchissement du registre chez les clients (`registry-fetch-interval-seconds`) | 30 s | 5 s | `config-repo/application.yml` |
| Nettoyage côté serveur (`eviction-interval-timer-in-ms`) | 60 s | 5 s | `config-repo/eureka-server.yml` |
| Auto-préservation (`enable-self-preservation`) | activée | désactivée | `config-repo/eureka-server.yml` |

- **Pourquoi réduire** : en démonstration, chaque seconde compte. Avec les valeurs par défaut, un service démarré n'apparaît qu'après environ 40 s. Pire : un service arrêté (`docker stop`, panne n°1) reste listé jusqu'à 90 s ou plus. La Gateway et Feign continueraient alors à lui envoyer du trafic, et le jury ne verrait pas la reprise.
- **Impact en production** :
  - **trafic** : un battement de cœur toutes les 5 s, multiplié par le nombre d'instances, charge davantage le serveur Eureka et le réseau. Chaque client télécharge aussi le registre 6 fois plus souvent ;
  - **faux positifs** : un ralentissement passager (GC, réseau) de 15 s suffit à retirer une instance pourtant saine ;
  - **auto-préservation désactivée** : lors d'une coupure réseau partielle, Eureka peut vider son registre d'un coup, alors que les instances fonctionnent. Tout le trafic tombe en 503.
- **Ce qu'on ferait en production** :
  - garder les valeurs par défaut (30 s, 90 s) et l'auto-préservation **activée** ;
  - déployer au moins 2 serveurs Eureka répliqués ;
  - compter sur la résilience côté client (Retry et CircuitBreaker, phase 4) pour absorber le délai avant qu'une instance arrêtée ne disparaisse du registre ;
  - sur Kubernetes, on remplacerait souvent Eureka par la découverte native (Services et sondes de disponibilité).
- **Un second cache, côté client** : Spring Cloud LoadBalancer (Gateway et Feign) garde la liste des instances **35 s** par défaut. Constaté à la phase 2 : après un redémarrage d'Eureka, les services étaient de nouveau UP dans le registre, mais la Gateway répondait encore 503 pendant ce délai. Réglé à **5 s** (`spring.cloud.loadbalancer.cache.ttl`, `config-repo/application.yml`). En production, on garderait un cache plus long (moins d'appels à Eureka), compensé par Retry et CircuitBreaker.
- **Le Config Server ne lit pas `config-repo/` pour lui-même** : ses délais Eureka sont donc fixés dans son propre `application.yml`. Sans cela, il mettait 30 s à se réinscrire après un redémarrage d'Eureka.

## Communication entre services

### Q10. Comment remplace-t-on une relation JPA quand les deux entités sont dans deux services ?

- **Le constat** : `Event` avait `@ManyToMany Set<Participant>` et `@OneToMany Set<Logistics>`. Avec une base par service (Q7), une jointure SQL entre `db_event` et `db_participant` est impossible, et une clé étrangère entre deux bases aussi.
- **La solution retenue** : l'événement garde seulement les **identifiants** (`@ElementCollection Set<Integer> participantIds`, table `event_participant` dans `db_event`). Quand il faut les objets, event-service les demande à leur propriétaire par **OpenFeign**, en un seul appel par lot (`GET /participants?ids=1,2`), et non un appel par ID (le problème « N+1 » à travers le réseau).
- **Ce qu'on perd** : l'intégrité référentielle. Un participant supprimé laisse un ID orphelin dans `event_participant`. Ici, l'existence est vérifiée **à l'écriture** (Feign → 404). En production, on ajouterait des **événements de domaine** (« ParticipantSupprimé » → event-service retire l'ID), ce que la phase 6 (Axon) prépare.
- **Pourquoi le calcul du coût réservé est dans logistics-service** : la règle (« réservé seulement », prix × quantité) appartient à la logistique. event-service transmet les ID et reçoit une somme. Un seul appel par événement, et aucune logique de logistique dupliquée.

### Q11. Que se passe-t-il quand participant-service est arrêté ?

- **Démarrage** : event-service n'a pas de `depends_on` sur participant-service ni sur logistics-service. Il démarre, s'inscrit dans Eureka et sert ses lectures. Un service métier absent ne bloque pas les autres (couplage faible).
- **Lecture simple** (`GET /api/events/{id}`) : elle ne lit que `db_event` et répond `200`.
- **Opération qui appelle participant-service** : Feign ne trouve pas d'instance, ou la connexion est refusée. `RemoteCall` répond un **503 explicite** (« participant-service indisponible (pas de réponse) »), au lieu d'un 500 avec une trace Java. Un 404 distant reste un 404 : l'appelant distingue « n'existe pas » de « ne répond pas ».
- **`calculCout` planifié** : l'exécution est journalisée « calculCout reporté », et la suivante (une minute plus tard) rattrape le calcul.
- **Phase 4 (Resilience4j)** : Retry (3 essais à 500 ms) pour une coupure brève, CircuitBreaker pour cesser d'appeler un service en panne, TimeLimiter (5 s) contre un service lent, et des **fallbacks** explicites. La capture C29 (avant) sera comparée à celle de la phase 4 (après).

### Q12. `calculCout` est planifié toutes les minutes : que se passe-t-il avec deux instances d'event-service ?

- Chaque instance a son propre `@Scheduled` : le calcul s'exécuterait **deux fois** par minute. Le résultat reste juste, car le calcul est **idempotent** (il recalcule la somme et ne l'additionne pas), mais le travail est doublé et les écritures concurrentes sont possibles.
- **Solutions** : un verrou partagé (ShedLock, avec une table de verrous dans `db_event`), une seule instance « planificatrice » (propriété `events.cout.cron: "-"` sur les autres, via le Config Server), ou un ordonnanceur externe qui appelle `POST /api/events/cout/recalcul`.
- **Dans ce projet** : event-service n'a qu'une instance (les 2 instances concernent participant-service et statistics-service). Le cron est réglable dans `config-repo/event-service.yml`, et le déclenchement manuel sert à la démonstration. En test, il est désactivé (`"-"`).

### Q13. Comment garantir un seul avis par participant, même avec deux requêtes simultanées ?

- **La vérification applicative ne suffit pas** : `existsByEventIdAndParticipantId` puis `save` est un « vérifier puis agir ». Deux requêtes simultanées peuvent passer toutes les deux la vérification avant que l'une n'écrive. C'est encore plus probable avec plusieurs instances du service.
- **La garantie vient de la base** : la contrainte d'unicité `uk_feedback_event_participant (event_id, participant_id)` de `db_feedback`. Le service écrit avec `saveAndFlush` dans sa transaction : la violation arrive avant la réponse. Elle est traduite en **409**, et seulement si c'est bien cette contrainte (les autres erreurs de base ne sont pas maquillées en doublon).
- **Preuve** : `FeedbackConcurrencyIntegrationTest` lance deux requêtes en même temps et obtient toujours exactement un `201` et un `409`.
- **Ce qui a changé avec les microservices** : la contrainte reste locale, car les deux colonnes sont dans `db_feedback`. En revanche, la clé étrangère vers l'événement n'existe plus : l'existence de l'événement est vérifiée par Feign au moment de l'écriture (Q10).

### Q14. Pourquoi une version « AVANT CQRS » de statistics-service, et quelles sont ses limites ?

- **But** : disposer d'une base de comparaison mesurable. Les statistiques sont d'abord calculées en **composant des appels Feign** (API composition), la solution la plus directe quand les données sont réparties entre plusieurs services. La phase 6 les calcule à partir d'une **vue locale** (CQRS).
- **Ce qui est déjà bien fait** : les objets liés sont lus **par lots** (`?ids=1,2,3`). Une requête coûte 4 appels réseau, qu'il y ait 3 ou 300 événements, au lieu d'un appel par événement (le problème N+1 à travers le réseau).
- **Limites mesurées** :
  - **disponibilité** : la disponibilité des statistiques est le **produit** de celles des 4 services (4 services à 99 % → environ 96 %). Arrêter feedback-service seul donne un 503 (C41) ;
  - **latence** : la somme des allers-retours réseau, environ 40 ms en local (C40), et plus sur un vrai réseau ;
  - **charge** : chaque lecture de tableau de bord interroge les bases des services d'écriture ;
  - **cohérence** : les 4 lectures ne sont pas faites au même instant (pas de transaction distribuée) ; un événement peut changer entre deux appels.
- **Ce que CQRS change à la phase 6** : statistics-service tient sa propre vue (`db_stats`), mise à jour par les événements publiés dans Axon. La lecture devient locale : 0 appel réseau, et elle répond même si les services d'écriture sont arrêtés. En contrepartie, la vue est **cohérente à terme** (un court délai après une écriture), et l'on peut la **reconstruire** par replay.
- **Pourquoi le calcul ne change pas** : il dépend de l'interface `StatsSource`, pas de Feign. La bascule ne touche que la source : c'est le principe d'inversion des dépendances, et ce qui rend la comparaison honnête.

_Les questions suivantes sont ajoutées au fil des phases : CQRS vs CRUD, event sourcing, états du circuit breaker, Gateway vs LoadBalancer, tracing, Terraform…_
