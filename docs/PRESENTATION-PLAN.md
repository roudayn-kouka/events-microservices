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
| 10 | Services et Feign | Relations JPA remplacées par des ID et des appels Feign ; 179 tests d'équipe repris | C15 à C43 |
| 11 | **CQRS Axon (mon module)** | Agrégat, commandes, projections dans statistics-service, replay, comparaison AVANT / APRÈS | C44 à C52 |
| 12 | Résilience4j | Configuration, fallbacks, 5 pannes, circuit OPEN → HALF_OPEN → CLOSED ; **bulkhead** : circuit séparé pour le cron (THEORIE Q28) | C53 à C60 |
| 13 | LoadBalancer | 2 instances, répartition 50 / 50 prouvée, bascule invisible, replay à 2 instances | C61 à C64 |
| 14 | Observabilité | Prometheus (découverte Eureka), 3 tableaux de bord Grafana, trace Zipkin de bout en bout, alertes | C65 à C71 |
| 15 | Swagger et Postman | Swagger agrégé (sélecteur des 5 services), 69 requêtes Postman, 5 pannes vérifiées par Newman | C72 à C74 |
| 16 | Sécurité (non implémentée, choix de périmètre) | Comment on sécuriserait la Gateway : OAuth2 / JWT (THEORIE Q19) | — |
| 17 | Angular | 5 écrans via la Gateway, **tableau de bord statistiques** en SVG, vue dégradée pendant une panne, `make e2e` | C75 à C79 |
| 18 | Qualité | 344 tests, seuil JaCoCo **bloquant** (lignes ≥ 70 %, branches ≥ 60 %), statistics à 97,7 % | C80 à C82 |
| 19 | Jenkins, SonarQube, Trivy | Pipeline multi-services sans push (dépôt local en `git://`), détection des changements, étapes parallèles, quality gate ; Sonar tout en **A** (0 bug, hotspot revu, couverture 77,7 %) ; Trivy avant / après | C83 à C91, C101 |
| 20 | AWS | Cible A (EC2 + Compose, ≈ 0,10 $/h), Terraform testé sans AWS, confirmation avant apply | C92, C93 (C95 à C97 après le lab) |
| 21 | Démonstration | Scénario de 10 minutes | — |
| 22 | Bilan | Fait : phases 0 à 7 et 9 à 12 ; retiré : Keycloak (Q19) ; reste : push et AWS réel (APRES-PUSH, AWS-RUNBOOK) | — |

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

### ⭐ statistics-service — mon module (port 8105) — VERSION AVANT CQRS (phase 2 ; version actuelle : section CQRS ci-dessous)

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

## ⭐ CQRS Axon (phase 3) — slide 11 et annexes

**Principe** : on sépare le modèle qui **écrit** (des commandes vérifiées par un agrégat, stockées comme une suite d'événements) du modèle qui **lit** (des vues locales, construites à partir de ces événements). Axon Server transporte les commandes, les événements et les requêtes, et il conserve l'event store.

| Côté | Où | Ce qui est fait |
|---|---|---|
| Commandes | event-service | `EventAggregate` en **event sourcing** : 6 commandes, 6 événements ; règles (dates, aucune commande après suppression, idempotence) ; état reconstruit en rejouant ses événements |
| Lecture locale | event-service | table `event` de `db_event`, tenue à jour par `EventProjection` (mode *subscribing* : à jour quand la commande rend la main) ; API et vérifications Feign inchangées |
| Publication | participant, logistics, feedback | `EventGateway.publish` après chaque écriture, dans la transaction (si Axon Server refuse, l'écriture est annulée) |
| **Requêtes (mon module)** | statistics-service | `StatistiquesProjection` (14 `@EventHandler`, processeur *tracking*, jeton dans `db_stats`) → 4 tables → `ProjectionStatsSource` → **le même calcul** → `@QueryHandler` interrogés par `QueryGateway` ; replay |
| Contrat | module `events-api` | les événements uniquement (records Java sans dépendance) ; commandes et requêtes restent internes |

**Comparaison mesurée sur les mêmes données** (captures C40 et C41 pour l'avant, C50 et C51 pour l'après) :

| Point comparé | AVANT CQRS (`stats.source=feign`) | APRÈS CQRS (défaut) |
|---|---|---|
| Source (`X-Stats-Source`) | `feign-avant-cqrs` : 4 appels Feign par requête | `projections-cqrs` : 0 appel réseau |
| Latence de `/api/stats/summary` | 38 à 47 ms | 19 à 21 ms |
| feedback-service arrêté | **503** | **200** (dernières valeurs projetées) |
| event, participant, logistics et feedback arrêtés | 503 | **200** |
| Fraîcheur | immédiate | cohérence à terme : participant visible après 51 ms |
| Reconstruction | — | replay : résumé identique avant et après |
| Code du calcul `EventStatsServiceImpl` | inchangé | inchangé : seule la `StatsSource` change |

**Avant / après : une écriture d'event-service.**

```java
// AVANT (phase 2) : l'état est écrit directement dans la table event
participantIds(event).add(idParticipant);
return eventRepository.save(event);

// APRÈS (phase 3) : une commande, vérifiée par l'agrégat, qui publie un événement ;
// la table event (modèle de lecture) est mise à jour par la projection
envoyer(new AffecterParticipant(idEvent, idParticipant));
return lire(idEvent);
```

**Avant / après : l'agrégat applique la règle, la projection construit la vue.**

```java
// Agrégat (côté commandes) : idempotent, aucun événement si le participant est déjà inscrit
@CommandHandler
public void handle(AffecterParticipant commande) {
    actif();
    if (!participantIds.contains(commande.idPart())) {
        apply(new ParticipantAffecte(idEvent, commande.idPart()));
    }
}

// Projection de mon module (côté requêtes) : la même information, dans db_stats
@EventHandler
public void on(ParticipantAffecte e) {
    modifier(e.idEvent(), s -> s.getParticipantIds().add(e.idPart()));
}
```

**Avant / après : la source des statistiques (mon module).**

```java
// AVANT : FeignStatsSource — event, participant, logistics et feedback lus à chaque requête
@ConditionalOnProperty(name = "stats.source", havingValue = "feign")
public class FeignStatsSource implements StatsSource { … }

// APRÈS : ProjectionStatsSource — tables locales de db_stats, source par défaut
@ConditionalOnProperty(name = "stats.source", havingValue = "projections", matchIfMissing = true)
public class ProjectionStatsSource implements StatsSource { … }
```

**Trois défauts trouvés en réel et corrigés** (bons sujets d'oral, voir THEORIE Q18) :
- une `List` renvoyée par un `@QueryHandler` perdait le type de ses éléments en traversant Axon Server : elle est enveloppée dans `ListeStats` ;
- un agrégat absent arrivait « sans détails » chez l'appelant (409) : les erreurs sont emballées avec leur code (404) ;
- un événement sans agrégat bloquait tout `calculCout` : le calcul est isolé par événement.

## Résilience4j et pannes (phase 4) — slide 12 et annexes

**Où** : sur **tous les appels OpenFeign** entre services (event → participant et logistics ; feedback → event et participant ; statistics → les 4 en mode AVANT CQRS), sans toucher au code métier. `common/ClientsResilients` enveloppe chaque client `@FeignClient` : une instance par service **appelé**, dans cet ordre :

`Retry( RateLimiter( CircuitBreaker( TimeLimiter( appel ) ) ) )`

| Mécanisme | Réglage (`config-repo/resilience-feign.yml`) | Rôle |
|---|---|---|
| Retry | 3 tentatives, 500 ms d'attente, **lectures (GET) seulement** | coupure brève ; un POST n'est jamais rejoué (risque de doublon) |
| RateLimiter | 5 appels par seconde, 1 s d'attente au plus | protège le service appelé ; au-delà : 429 |
| CircuitBreaker | OPEN à 50 % d'échecs sur 10 appels (5 au minimum) ; 10 s ouvert ; 3 appels d'essai | cesse d'appeler un service en panne : réponse immédiate |
| TimeLimiter | 5 s | abandonne un service lent |
| Gateway | un CircuitBreaker par route, fallback `/indisponible/{service}` | 503 JSON au lieu d'un 500 |

Les erreurs **métier** (404, 400, 409) ne sont ni retentées ni comptées comme une panne.

**Fallbacks explicites** : chaque cause est nommée dans la réponse.

| Cause | HTTP | Message |
|---|---|---|
| circuit ouvert | 503 | « participant-service : circuit ouvert, service considéré en panne (appel non tenté) » |
| trop lent | 504 | « pas de réponse en 5000 ms (TimeLimiter) après 3 tentatives » |
| trop d'appels | 429 | « trop d'appels (RateLimiter : 5 par 1000 ms) » |
| réponse illisible | 502 | « réponse incorrecte, rejetée » |
| réseau | 503 | « injoignable (erreur réseau) après 3 tentatives » |
| **lecture composée** | **200** | vue `/events/{id}/details` **dégradée** : liste vide et `avertissements` |

**Les 5 pannes, mesurées** (`make panne N=1` à `5`, rétablies automatiquement) :

| # | Panne | Comment | Résultat mesuré |
|---|---|---|---|
| 1 | Disponibilité | `docker compose stop participant-service` | 1er appel : 3 tentatives puis fallback ; circuit **OPEN** (60 %) ; appels suivants en **0,01 s** ; inscription 503 ; au redémarrage : HALF_OPEN puis CLOSED |
| 2 | Temps de réponse | latence de 7 s posée **à chaud** (Config Server + `/actuator/refresh`) | **504** « pas de réponse en 5000 ms (TimeLimiter) » ; circuit OPEN ; retour à chaud : 200 en 0,02 s |
| 3 | Réponse incorrecte | JSON tronqué en HTTP 200, à chaud | **502** « réponse incorrecte, rejetée » ; rien d'enregistré ; retour à chaud, CLOSED |
| 4 | Erreur réseau | mauvaise URL de participant-service dans event-service, à chaud | « injoignable (erreur réseau) après 3 tentatives » ; **correction à chaud** par `/actuator/refresh` ; CLOSED |
| 5 | Dépendance externe | compte MySQL de participant verrouillé | santé de participant **DOWN** (`db`) ; HTTP 500 → circuit d'event-service OPEN (83 %) ; déverrouillage : UP puis CLOSED |

**Avant / après : un appel Feign.**

```java
// AVANT (phase 2) : une panne donne un 503 générique, après le délai de connexion, à chaque appel
RemoteCall.call("participant-service", "Participant introuvable", () -> participantClient.getParticipant(id));

// APRÈS (phase 4) : le même code métier ; le client Feign est enveloppé par ClientsResilients
// → Retry ×3, RateLimiter, CircuitBreaker, TimeLimiter 5 s, puis fallback qui nomme la cause
// ex. 503 « participant-service : circuit ouvert, service considéré en panne (appel non tenté) »
```

**Avant / après : la Gateway quand un service est arrêté.**

```yaml
# AVANT : connexion refusée → HTTP 500 sans explication
filters: [StripPrefix=1]
# APRÈS : CircuitBreaker par route → 503 JSON « logistics-service indisponible (Gateway) : … »
filters:
  - StripPrefix=1
  - name: CircuitBreaker
    args: {name: gw-logistics-service, fallbackUri: forward:/indisponible/logistics-service}
```

## Répartition de charge (phase 5) — slide 13

**Montage** : 2 instances de participant-service (8102 et 8112) et de statistics-service (8105 et 8115), profil `core`. Même image, `INSTANCE_ID` différent, même nom dans Eureka. La répartition se fait **côté client** (Spring Cloud LoadBalancer, *round robin*) : dans la Gateway (`lb://…`) et dans Feign (event-service → participant-service). Chaque réponse porte `X-Instance-Id`.

**Mesuré** (`make repartition`, 20 requêtes par test) :

| Chemin | Instance 1 | Instance 2 |
|---|---|---|
| Gateway → participant-service | 10 (50 %) | 10 (50 %) |
| Gateway → statistics-service | 10 (50 %) | 10 (50 %) |
| Feign : event-service → participant-service (compteurs Prometheus) | 10 | 10 |
| participant-service-2 arrêtée | 20 (100 %), **0 erreur** | — |

**Avant / après : la bascule.**

```yaml
# AVANT : pendant les secondes où la Gateway connaît encore l'instance arrêtée
#         → 3 requêtes sur 20 en 503 ; la première attendait 14 s une connexion impossible
# APRÈS : connexion limitée à 2 s, et Retry des lectures DANS le CircuitBreaker de la route
- name: Retry
  args: {retries: 2, methods: GET, series: SERVER_ERROR, exceptions: java.io.IOException, java.util.concurrent.TimeoutException}
# → 20 lectures sur 20 réussies pendant l'arrêt
```

**Mon module avec 2 instances** : elles partagent `db_stats` et le jeton du processeur. **Une seule projette** à un instant donné (un segment), les deux répondent aux requêtes. Le replay se fait par `make replay` : arrêt de l'instance 2, reset sur 8105, compteurs identiques, redémarrage, 2 instances UP (THEORIE Q25).

## Observabilité (phase 6) — slide 14

| Brique | Ce qui est en place | Où |
|---|---|---|
| Métriques | `/actuator/prometheus` sur chaque service ; **Prometheus découvre les instances par Eureka** (`eureka_sd_configs`) : les 2es instances sont scrappées sans configuration ; 11 cibles UP | http://localhost:9091 |
| Tableaux de bord | Grafana provisionné au démarrage (sources Prometheus et Zipkin, 3 tableaux générés) : **JVM et HTTP** (débit, p95, 5xx, heap, CPU, GC, Hikari), **Resilience4j** (état des circuits, taux d'échec, appels refusés, Retry, TimeLimiter, RateLimiter, durée des appels Feign), **Métier** (indicateurs de mon module, répartition de charge par instance) | http://localhost:3001 (consultation sans compte) |
| Traces | Micrometer Tracing (Brave) → Zipkin ; la Gateway et Feign propagent le contexte ; `[traceId-spanId]` dans chaque ligne de log | http://localhost:9411 |
| Alertes | `InstanceInjoignable`, `ServiceAbsent`, `CircuitOuvert` (OPEN ou HALF_OPEN), `LatenceP95Elevee` (plus de 2 s), `Erreurs5xx` | http://localhost:9091/alerts |

**Trace mesurée** : `GET /api/events/{id}/details` donne **7 spans**, sur api-gateway → event-service → participant-service et logistics-service, avec un seul traceId.

**Avant / après : la trace qui traverse le TimeLimiter.**

```java
// AVANT : l'appel Feign s'exécute dans un thread du TimeLimiter → contexte de trace perdu,
//         la trace Zipkin s'arrêterait à event-service
Executors.newCachedThreadPool(...)
// APRÈS : le contexte de l'appelant (trace en cours) est recopié dans le thread de l'appel
ContextExecutorService.wrap(executor, ContextSnapshotFactory.builder().build()::captureAll)
// → logs du thread « feign-timelimiter » : [6afb6de0…-8993d79d…], même traceId que la Gateway
```

**Deux défauts trouvés en réel** :
- les mesures Feign étaient rejetées par Prometheus : un conflit d'étiquettes avec `http.client.requests` de Spring. Elles sont renommées `feign.client.requests` ;
- une alerte sur l'état OPEN seul disparaissait au bout de 10 s (passage à HALF_OPEN) : elle couvre maintenant OPEN et HALF_OPEN.

---

## Correctif après la phase 7 : isolation des circuits (bulkhead) — slide 12

**Symptôme** : à la panne 3, le 502 attendu (« réponse incorrecte ») devenait parfois un 503 (« circuit ouvert »).
**Diagnostic** : en relevant `/actuator/circuitbreakers` toutes les 0,5 s, le circuit était CLOSED, à 0 échec, au début de la panne. C'était le cron `calculCout` (seconde :00) qui, en appelant participant-service pendant la panne, comptait 3 échecs (Retry autour du circuit) dans le circuit **des utilisateurs**.

```java
// AVANT : le cron partage le circuit « participant-service » des requêtes des utilisateurs
public void calculCoutPlanifie() { calculCout(); }
// APRÈS : traitement de fond → circuits « participant-service-batch », sans Retry
public void calculCoutPlanifie() { ClientsResilients.enTraitementDeFond(this::calculCout); }
```
Preuve : le test `lesEchecsDuTraitementDeFondLaissentLeCircuitDesUtilisateursFerme`, et 3 passages de `make newman-pannes` sans échec, pendant lesquels le cron a croisé la panne 3.

## Frontend Angular (phase 9) — slide 17

| Écran | Service | À montrer |
|---|---|---|
| Statistiques | statistics-service (CQRS) | 6 indicateurs, anneaux (tâches, logistique), barres (participants, coût, notes) ; badges `source : projections-cqrs` et instance servie |
| Événements, Détail | event-service (+ Feign) | création = commande Axon ; inscription ; **bandeau « Vue dégradée »** quand participant-service est arrêté |
| Participants, Logistique | participant-, logistics-service | création, changement de tâche, réservation |
| Avis | feedback-service | règle métier : avis refusé pour un événement futur (400, message affiché) |

Choix : base Angular 20 de l'équipe ; graphiques en **SVG sans bibliothèque** (bundle de 101 ko compressé) ; nginx transmet `/api` à la Gateway (même origine, donc pas de CORS) ; `make e2e` joue le parcours dans Chromium (13 contrôles).

## Qualité (phase 10) — slide 18

`jacoco:check` dans le POM parent, à la phase `verify` : **le build échoue** sous 70 % de lignes ou 60 % de branches, dans chaque service métier. Preuve : `-Djacoco.minimum.lignes=0.99` donne `BUILD FAILURE` sur statistics-service (0,97).

| Service | Lignes | Branches |
|---|---:|---:|
| ⭐ statistics-service | 97,7 % | 87,5 % |
| event-service | 95,8 % | 88,9 % |
| participant-service | 96,0 % | 83,3 % |
| logistics-service | 88,6 % | 90,0 % |
| feedback-service | 96,6 % | 97,1 % |

## Jenkins, SonarQube, Trivy (phase 11) — slide 19

```
Changements ─► Build et tests ─► ┌ SonarQube ┐ ─► Quality gate ─► Images + Trivy (9 en parallèle) ─► Push (off) ─► AWS (off)
                                 └ Frontend  ┘
```
- **Sans push** : le Jenkins de `~/devops` clone `git://events-git/events-microservices` (git daemon, dépôt monté en lecture seule). Un commit local déclenche un build par scrutation.
- **Détection des changements** : un commit qui ne touche que `frontend/` ne reconstruit que l'image du frontend. Un changement commun reconstruit tout (THEORIE Q29).
- **Trivy, avant / après** (THEORIE Q30) : statistics-service passe de 18 à 7 (CRITICAL de 8 à 3), le frontend de 57 à 0. Piège : sans `mvn clean`, le JAR gardait les anciennes bibliothèques.

## AWS (phase 12) — slide 20

- Terraform : VPC, sous-réseau public, security group limité à **ton IP**, EC2 `t3.large` avec `LabInstanceProfile` (aucune création IAM), IMDSv2, disque chiffré. Ansible : Docker, swap. Code envoyé par `rsync`, `.env` généré sur l'EC2.
- **Vérifié sans AWS** : `terraform test` sur un fournisseur simulé (5 runs, dont le refus de `0.0.0.0/0`), qui a trouvé un vrai bug (accents refusés par AWS dans les descriptions) ; ansible-lint ; 16 tests des scripts avec de faux outils.
- `make aws-up` : plan affiché, puis **confirmation**, puis apply. `make aws-down` : confirmation « détruire ». Coût ≈ 0,10 $/h.

## Bilan chiffré — slide 22

| Indicateur | Résultat final (build Jenkins n° 9, commit `d910ee1`, 2026-11-17) |
|---|---|
| Tests | **344** verts (`mvn clean verify`, comptés aussi par Sonar) ; seuil JaCoCo respecté dans les 5 services (statistics-service : 97,7 % des lignes) |
| Postman / Newman | **69** requêtes, **137** assertions, 0 échec ; `make newman-pannes` : 5 pannes sur 5 |
| Frontend | 10 tests Vitest ; `make e2e` : 13 contrôles dans Chromium |
| SonarQube | quality gate **Passed** ; 0 bug, fiabilité **A** ; 0 vulnérabilité, sécurité **A** ; 26 code smells, maintenabilité **A** ; couverture **77,7 %** ; duplication **0,7 %** ; 4 356 lignes de code |
| Security Hotspots | 1 seul, revu à **100 %** (**A**) : `java:S3752` (CSRF) sur le fallback `IndisponibleController`, **Safe**, car il accepte toutes les méthodes pour les forward des routes, répond 503 en lecture seule, sans session ni cookie (THEORIE Q31) |
| Trivy (HIGH + CRITICAL corrigeables) | services Java : 6 à 8 (3 CRITICAL, 4 pour eureka-server), dont statistics-service 7 (contre 18 au build 1) ; frontend 0 (contre 57) |
| Jenkins | builds 1 à 9 en SUCCESS ; Push et AWS désactivés par défaut |

