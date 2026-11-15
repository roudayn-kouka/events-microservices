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
