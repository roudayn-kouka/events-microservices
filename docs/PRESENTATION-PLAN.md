# Plan de la présentation (par technologie)

Présentation individuelle. Fil rouge : **statistics-service est mon module** (côté requêtes CQRS, projections, dashboard). Chaque slide indique les commandes, l'extrait de code et les captures associées (numéros de `CAPTURES.md`). Le contenu est complété à chaque phase.

| # | Slide | Contenu | Captures |
|---|---|---|---|
| 1 | Titre | Events FST en microservices Spring Cloud — Roudayna Kouka, GL5 | — |
| 2 | Du monolithe aux microservices | Diagnostic des 5 branches, ce qui casse (relations JPA, transactions, statistiques) | — |
| 3 | Architecture | Diagramme (README), ports, profils Docker Compose | — |
| 4 | Git et JIRA | GitFlow, commits EVT-xx, backlog importé | C1, C2 |
| 5 | Maven et versions | POM parent, Boot 3.5.16 / Cloud 2025.0.3 / Axon 4.13.3, justification | C3, C4 |
| 6 | Config Server | `config-repo/` versionné dans Git (un YAML par service + `application.yml` commun) ; profil `composite` avec surcharges à chaud ; le serveur lit le contenu commité (preuve C5) ; piège `file:` (THEORIE Q4). Commandes C2 à C5 | C7, C8, C9 |
| 7 | Eureka | Enregistrement et battements de cœur toutes les 5 s, retrait après 15 s ; tableau de bord ; registre JSON. Commandes E1, E2 | C10 |
| 8 | API Gateway | Routes `lb://` (extrait de `api-gateway.yml`), `StripPrefix`, CORS Angular (200 contre 403), 503 sans instance, routes en lecture seule. Commandes W1 à W5 | C11, C12 |
| 9 | MySQL | Une base et un utilisateur par service (script d'initialisation), isolation prouvée par `ERROR 1044`, port 3307. Commandes S1 à S3 | C13 |
| 9 bis | Docker Compose du socle | Healthchecks, `depends_on: service_healthy`, `.env` généré, `make up` / `make health`, redémarrage tolérant (fail-fast + retry). Commandes D1 à D6 et C6 | C5, C6, C14 |
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

---

## Détail des services métier (slide 10 et annexes)

Pour chaque service : sa branche d'origine, ce qui a changé par rapport au monolithe, la correspondance des endpoints et un exemple de code avant/après. Les mêmes tableaux figurent dans chaque dossier de la collection Postman (`postman/`).

### participant-service (port 8102, base `db_participant`)

- **Branche d'origine** : `feature/participant` (Farah Baraket).
- **Changements** :
  - la relation `@ManyToMany Set<Event> events` est supprimée. Le participant ne connaît plus ses événements : c'est event-service qui stocke les identifiants des participants inscrits ;
  - le DTO perd `Set<EventDTO> events`, et gagne la validation (`@NotBlank`, `@NotNull`) ;
  - deux endpoints sont ajoutés pour les appels Feign : la lecture par lot (`?ids=`) et la recherche par nom, prénom et tâche (pour `calculCout`) ;
  - les erreurs sont en JSON homogène, et l'en-tête `X-Instance-Id` est ajouté (module `common`).
- **Tests** : 31 tests d'origine (16 repris tels quels, 15 adaptés, 0 supprimé) et 10 ajoutés, soit 41 au total (détail dans `ETAT-FINAL.md`).

| Monolithe (`/events`, port 8089) | Microservices (via la Gateway, port 8088) |
|---|---|
| `POST /events/participant/add` | `POST /api/participants` (201) |
| `GET /events/participant/all` | `GET /api/participants` |
| `GET /events/participant/{id}` | `GET /api/participants/{id}` |
| `PUT /events/participant/update/{id}` | `PUT /api/participants/{id}` |
| `PUT /events/participant/{id}/tache/{tache}` | `PUT /api/participants/{id}/tache/{tache}` |
| `DELETE /events/participant/delete/{id}` | `DELETE /api/participants/{id}` (204) |
| `POST /events/event/addPart` | `POST /api/participants` |
| — | `GET /api/participants?ids=1,2` (lecture par lot, Feign) |
| — | `GET /api/participants/search?nom=&prenom=&tache=` (calculCout) |

**Avant / après : l'entité.**

```java
// AVANT — monolithe (feature/participant) : relation JPA vers Event
@Entity
public class Participant implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    int idPart;
    String nom;
    String prenom;
    @Enumerated(EnumType.STRING)
    Tache tache;
    @ManyToMany
    @JsonIgnoreProperties({"participants", "logistics"}) // évite la boucle JSON Participant -> Event -> Participant
    Set<Event> events;
}

// APRÈS — participant-service : plus aucune référence à Event (autre service, autre base)
@Entity
public class Participant implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer idPart;
    String nom;
    String prenom;
    @Enumerated(EnumType.STRING)
    Tache tache;
}
```

**Avant / après : la requête de `calculCout`.**

```java
// AVANT — une requête JPA qui traverse la jointure Event↔Participant (même base)
eventRepository.findByParticipants_NomAndParticipants_PrenomAndParticipants_Tache("Tounsi", "Ahmed", Tache.ORGANISATEUR);

// APRÈS — participant-service expose la recherche ; event-service l'appellera par Feign
@GetMapping("/search")
public List<ParticipantDTO> search(@RequestParam String nom, @RequestParam String prenom, @RequestParam Tache tache)
```

### logistics-service (port 8103, base `db_logistics`)

- **Branche d'origine** : `feature/logistics` (Ahlem Mastouri). Le DTO et la partie logistique du `Mapper` viennent de `feature/participant` (Farah).
- **Changements** :
  - l'entité ne change pas. C'est le **côté événement** qui perd sa relation `@OneToMany Set<Logistics>` : event-service gardera les identifiants (`event_logistics`) ;
  - « logistique introuvable » renvoie **404 au lieu de 500**. Le monolithe levait `IllegalArgumentException` : un appelant Feign ne pouvait pas distinguer une logistique inexistante d'une panne du service ;
  - le contrôleur expose des DTO validés (`@NotBlank`, `@PositiveOrZero`) au lieu de l'entité JPA ;
  - deux endpoints sont ajoutés pour event-service : la lecture par lot (`?ids=`) et le coût réservé d'un ensemble (`/cout-reserve`).
- **Tests** : 19 d'origine (18 repris, 1 adapté, 0 supprimé) et 10 ajoutés, soit 29 au total (détail dans `ETAT-FINAL.md`).

| Monolithe (`/events`, port 8089) | Microservices (via la Gateway, port 8088) |
|---|---|
| `POST /events/logistics` | `POST /api/logistics` (201) |
| `PUT /events/logistics` | `PUT /api/logistics` |
| `GET /events/logistics` | `GET /api/logistics` |
| `GET /events/logistics/{id}` | `GET /api/logistics/{id}` (404 si introuvable ; 500 avant) |
| `DELETE /events/logistics/{id}` | `DELETE /api/logistics/{id}` (204) |
| `GET /events/logistics/reserved` | `GET /api/logistics/reserved` |
| `GET /events/logistics/{id}/cout` | `GET /api/logistics/{id}/cout` |
| — | `GET /api/logistics?ids=1,2` (lecture par lot, Feign) |
| — | `GET /api/logistics/cout-reserve?ids=1,2` (calculCout) |

**Avant / après : l'erreur « introuvable ».**

```java
// AVANT — monolithe (feature/logistics) : HTTP 500 pour une simple logistique inexistante
return logisticsRepository.findById(idLog)
        .orElseThrow(() -> new IllegalArgumentException("Logistics introuvable : " + idLog));

// APRÈS — logistics-service : 404 explicite ; l'appelant Feign sait que ce n'est pas une panne
return logisticsRepository.findById(idLog)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Logistics introuvable : " + idLog));
```

**Avant / après : le coût de la logistique réservée d'un événement.**

```java
// AVANT — calculCout (feature/event) parcourt la relation JPA dans la même base
for (Logistics l : event.getLogistics()) { if (l.isReserve()) somme += l.getPrixUnit() * l.getQuantite(); }

// APRÈS — logistics-service calcule à partir des ID que lui transmet event-service (Feign)
@GetMapping("/cout-reserve")
public float getCoutReserve(@RequestParam("ids") List<Integer> ids)
```

### event-service (port 8101, base `db_event`)

- **Branche d'origine** : `feature/event` (Nour Mustapha). Les corrections de `feature/feedback` sont reprises : participant introuvable → 404, participants obligatoires → 400, `getLogisticsDates` ne renvoie plus `null`. Les tests viennent aussi de `feature/event-statistics` et `feature/participant`.
- **Changements** :
  - identifiant `int` → **UUID `String`**, prêt pour l'agrégat Axon de la phase 3 (THEORIE Q1) ;
  - les relations `@ManyToMany(mappedBy = "events") Set<Participant>` et `@OneToMany Set<Logistics>` deviennent des **ensembles d'ID** (`@ElementCollection Set<Integer> participantIds / logisticsIds`, tables `event_participant` et `event_logistics` dans `db_event`) ;
  - les accès directs à `ParticipantRepository` et `LogisticsRepository` (autres bases) sont remplacés par **deux clients OpenFeign** (`participant-service`, `logistics-service`, résolus par Eureka). `RemoteCall` traduit les erreurs : 404 distant → 404, service en panne → **503 explicite** (avant Resilience4j, phase 4) ;
  - `calculCout` : la jointure JPA `findByParticipants_Nom…` devient une recherche Feign de l'organisateur, puis un appel Feign `cout-reserve` par événement. Le cron est réglable dans `config-repo/event-service.yml`, et un déclenchement manuel `POST /events/cout/recalcul` est ajouté ;
  - `addParticipant` (créer un participant) quitte ce service : c'est le rôle de participant-service ;
  - ajouts : lecture, mise à jour, suppression, inscription par ID, et une vue composée `GET /events/{id}/details` (3 services) ;
  - `findByDescription` → `findFirstByDescription` : dans le monolithe, deux événements de même description faisaient échouer `addAffectLog` (HTTP 500).
- **Tests** : 47 d'origine. 41 sont conservés (33 adaptés un pour un, 8 fusionnés en 4) et 6 supprimés, avec raison ; 28 sont ajoutés, soit **65 au total** (détail dans `ETAT-FINAL.md`).

| Monolithe (`/events`, port 8089) | Microservices (via la Gateway, port 8088) |
|---|---|
| `POST /events/event/addEvent` | `POST /api/events` (201, `participantIds` vérifiés par Feign) |
| `POST /events/event/addEvent/{id}` | `POST /api/events/participant/{idPart}` (201) |
| `PUT /events/event/addAffectLog/{description}` | `PUT /api/events/by-description/{description}/logistics` |
| `GET /events/event/getLogs/{d1}/{d2}` | `GET /api/events/logistics/{d1}/{d2}` |
| `POST /events/event/addPart` | `POST /api/participants` (participant-service) |
| — (aucune lecture) | `GET /api/events`, `GET /api/events/{id}` |
| — | `GET /api/events/{id}/details` (participants + logistique par Feign) |
| — | `PUT /api/events/{id}`, `DELETE /api/events/{id}` (204) |
| — | `POST /api/events/{id}/participants/{idPart}` |
| — | `POST /api/events/{id}/logistics` (201, créée dans logistics-service) |
| `calculCout` toutes les 60 s | idem (cron configurable) + `POST /api/events/cout/recalcul` |

**Avant / après : l'entité.**

```java
// AVANT — monolithe (feature/event) : objets d'une autre table, dans la même base
@ManyToMany(mappedBy = "events")
Set<Participant> participants;
@OneToMany(fetch = FetchType.EAGER)
Set<Logistics> logistics;

// APRÈS — event-service : seulement des identifiants ; les objets sont dans d'autres bases
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "event_participant", joinColumns = @JoinColumn(name = "event_id"))
@Column(name = "participant_id")
Set<Integer> participantIds = new HashSet<>();
```

**Avant / après : inscrire un participant.**

```java
// AVANT — lecture directe d'une autre table ; NullPointerException si le participant n'existe pas
Participant participant = participantRepository.findById(idParticipant).orElse(null);
if (participant.getEvents() == null) { … participant.setEvents(events); } else { participant.getEvents().add(event); }
return eventRepository.save(event);

// APRÈS — existence vérifiée par Feign (404 distant → 404 ; panne → 503), puis ajout de l'ID
RemoteCall.call("participant-service", "Participant not found with id " + idParticipant,
        () -> participantClient.getParticipant(idParticipant));
participantIds(event).add(idParticipant);
return addEvent(event);
```

**Avant / après : `calculCout`.**

```java
// AVANT — une jointure JPA sur trois tables de la même base, puis une boucle sur la logistique
List<Event> events = eventRepository.findByParticipants_NomAndParticipants_PrenomAndParticipants_Tache(
        "Tounsi", "Ahmed", Tache.ORGANISATEUR);
for (Logistics l : event.getLogistics()) if (l.isReserve()) somme += l.getPrixUnit() * l.getQuantite();

// APRÈS — 1) Feign : qui est l'organisateur ? 2) db_event : ses événements 3) Feign : coût réservé
Set<Integer> organisateurs = participantClient.search("Tounsi", "Ahmed", "ORGANISATEUR")…;
List<Event> events = eventRepository.findDistinctByParticipantIdsIn(organisateurs);
float somme = ids.isEmpty() ? 0f : logisticsClient.coutReserve(ids);   // aucun appel si pas de logistique
```

### feedback-service (port 8104, base `db_feedback`)

- **Branche d'origine** : `feature/feedback` (Emna, ouemnaa).
- **Changements** :
  - `@ManyToOne Event` et `@ManyToOne Participant` deviennent `eventId` (UUID) et `participantId`. La contrainte d'unicité `uk_feedback_event_participant` est conservée sur les mêmes colonnes : un seul avis par participant et par événement, garanti par la base, même avec deux requêtes simultanées ;
  - les règles et leur ordre ne changent pas (note de 1 à 5 → événement existant → participant existant **et** inscrit → événement terminé → pas de doublon). Mais l'événement et le participant sont lus par **OpenFeign** (event-service, participant-service) ;
  - « participant inscrit » se lit maintenant dans `participantIds` de l'événement : c'est event-service qui porte l'inscription (Q10) ;
  - le `FeedbackExceptionHandler` d'origine est remplacé par le format d'erreur commun (`ApiError`, module `common`), identique dans les 5 services ;
  - ajouts : `GET /feedback/moyennes` (moyenne et nombre d'avis par événement, source de statistics-service) et le fuseau horaire de la règle « terminé », réglable dans `config-repo/feedback-service.yml` (Africa/Tunis ; un conteneur Docker est en UTC).
- **Tests** : 42 méthodes d'origine (53 exécutions avec les tests paramétrés) : 37 adaptées et 5 supprimées, avec raison ; 10 ajoutées. Soit **62 exécutions vertes** (détail dans `ETAT-FINAL.md`).

| Monolithe (`/events`, port 8089) | Microservices (via la Gateway, port 8088) |
|---|---|
| `POST /events/feedback/{idEvent}/{idPart}` | `POST /api/feedback/{idEvent}/{idPart}` (201 ; 400, 404, 409 ; 503 si un service vérifié est en panne) |
| `GET /events/feedback/event/{id}` | `GET /api/feedback/event/{id}` |
| `GET /events/feedback/event/{id}/moyenne` | `GET /api/feedback/event/{id}/moyenne` |
| `DELETE /events/feedback/{id}` | `DELETE /api/feedback/{id}` (204) |
| `GET /events/event-stats` (moyenne calculée par jointure) | `GET /api/feedback/moyennes`, lu par statistics-service |

**Avant / après : l'entité.**

```java
// AVANT — monolithe (feature/feedback) : clés étrangères vers les tables event et participant
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "event_id", nullable = false)
private Event event;

// APRÈS — feedback-service : mêmes colonnes, sans clé étrangère (les tables sont dans d'autres bases)
@Column(name = "event_id", nullable = false, length = 36)
private String eventId;          // UUID de l'événement (event-service)
```

**Avant / après : « le participant est-il inscrit à l'événement ? ».**

```java
// AVANT — lecture de la table participant et de sa relation events, dans la même base
Participant participant = participantRepository.findById(participantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));
boolean associated = participant.getEvents() != null && participant.getEvents().stream()
        .anyMatch(event -> event.getIdEvent() == eventId);

// APRÈS — existence demandée à participant-service, inscription lue dans l'événement (event-service)
RemoteCall.call("participant-service", "Participant not found",
        () -> participantClient.getParticipant(participantId));
boolean associated = event.participantIds() != null && event.participantIds().contains(participantId);
```

### ⭐ statistics-service — mon module (port 8105) — VERSION AVANT CQRS

- **Branche d'origine** : `feature/event-statistics` (Roudayna Kouka), plus la note moyenne des avis ajoutée sur `feature/feedback`.
- **Ce qui ne change pas** : toutes les règles de calcul de mon module :
  - durée inclusive ;
  - répartition par tâche, avec toutes les tâches présentes, même à 0 ;
  - nombre, coût et taux de la logistique **réservée** ;
  - tri par date de début ;
  - événements à venir ;
  - intervalle invalide → 400 ;
  - métriques `fst.*` pour Grafana.
- **Changements** :
  - les trois repositories (une seule base) sont remplacés par une interface **`StatsSource`**. Le calcul (`EventStatsServiceImpl`) ne connaît que cette interface ;
  - **AVANT CQRS** (cette version, phase 2) : `FeignStatsSource` lit les 4 services métier à chaque requête. Les objets liés sont lus **en lots** : 4 appels par requête, quel que soit le nombre d'événements, au lieu d'un appel par événement ;
  - **APRÈS CQRS** (phase 3) : une source lira les projections locales de `db_stats`, alimentées par les événements Axon. Le calcul reste identique : la comparaison porte sur la seule source ;
  - chaque réponse porte l'en-tête **`X-Stats-Source`** (`feign-avant-cqrs` ici), pour montrer la version en démonstration ;
  - ajouts : note moyenne et nombre d'avis, par événement et global (moyenne **pondérée** par le nombre d'avis) ;
  - `db_stats` existe déjà, mais reste vide jusqu'à la phase 3.
- **Limites mesurées de la version AVANT CQRS**, à comparer à la phase 3 :
  - **disponibilité** : si feedback-service seul est arrêté, `/api/stats/summary` répond **503** (capture C41) ;
  - **latence** : environ **40 ms** par `/summary`, pour 4 appels réseau (capture C40) ;
  - **charge** : chaque lecture de statistiques sollicite les bases des services d'écriture.
- **Tests** : 35 tests d'origine (2 variantes du test de service fusionnées) : tous adaptés, aucun supprimé ; 19 ajoutés, soit **45 au total**. Couverture **98 %** des instructions (objectif ≥ 70 %, EVT-74).

| Monolithe (`/events`, port 8089) | Microservices (via la Gateway, port 8088) |
|---|---|
| `GET /events/event-stats` | `GET /api/stats/events` |
| `GET /events/event-stats/{id}` | `GET /api/stats/events/{id}` (UUID) |
| `GET /events/event-stats/summary` | `GET /api/stats/summary` |
| `GET /events/event-stats/upcoming` | `GET /api/stats/upcoming` |
| `GET /events/event-stats/between/{d1}/{d2}` | `GET /api/stats/between/{d1}/{d2}` |
| — | en-tête `X-Stats-Source` : `feign-avant-cqrs` (phase 2), projections (phase 3) |

**Avant / après : d'où viennent les données.**

```java
// AVANT — monolithe (feature/event-statistics) : trois repositories, une seule base
EventRepository eventRepository;
ParticipantRepository participantRepository;
LogisticsRepository logisticsRepository;
Set<Participant> participants = event.getParticipants();      // relation JPA

// APRÈS (phase 2, AVANT CQRS) — une source interchangeable ; ici, 4 services lus par Feign
StatsSource source;                                            // FeignStatsSource
Map<Integer, ParticipantDTO> participants = source.participants(ids(events, EventDTO::participantIds))…;
// un seul lot de participants, un seul lot de logistique, une seule lecture des moyennes
```

**Avant / après : le même calcul, la même règle.**

```java
// AVANT : boucle sur la relation JPA
for (Logistics l : logistics) { if (l.isReserve()) { nbReservees++; cout += l.getPrixUnit() * l.getQuantite(); } }

// APRÈS : mêmes règles, sur les DTO lus par lots
List<LogisticsDTO> reservees = logistics.stream().filter(LogisticsDTO::reserve).toList();
float cout = coutReserve(reservees);                           // somme prixUnit × quantite
```
