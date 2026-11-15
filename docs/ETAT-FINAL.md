# État final

Ce document est complété au fil des phases. Il recense ce qui est fait et testé, et ce qui reste à lancer (push, pipeline Jenkins réel, AWS), avec le fichier qui l'explique.

---

## Reprise des tests des branches d'équipe (phase 2)

### Méthode

Les 5 branches sont des copies du même monolithe : beaucoup de classes de test y sont identiques. J'ai donc compté les **versions distinctes** (même nom de classe et même contenu) : **30 versions, soit 179 tests**. Les 126 tests de `feature/feedback` comprennent ses tests propres et les copies des tests des autres features.

Statuts utilisés :
- **Repris** : la logique de test est identique ; seuls les imports et les annotations Boot 3 changent (`getStatusCode()`, `@MockitoBean`).
- **Adapté** : même intention, mais le chemin, le code HTTP ou la relation testée change (endpoint REST, ID au lieu d'entité, Feign simulé).
- **Supprimé** : le comportement testé n'existe plus. La raison est donnée pour chaque suppression.
- **Ajouté** : nouveau test, pour un comportement nouveau (Feign, lecture par lot, validation, module `common`).

### participant-service — origine `feature/participant`

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `ParticipantServicesImplMockitoTest` | 9 | 9 | 0 | 0 | `getStatus()` → `getStatusCode()` |
| `ParticipantServicesImplIntegrationTest` | 7 | 7 | 0 | 0 | idem (`@DataJpaTest` sur H2) |
| `ParticipantRestControllerMockitoTest` | 8 | 0 | 8 | 0 | chemins REST `/participants`, 201 à la création, 204 à la suppression, `@MockitoBean` |
| `ParticipantRestControllerIntegrationTest` | 6 | 0 | 6 | 0 | chemins REST, 201/204 |
| `MapperTest` (partie participant) | 1 | 0 | 1 | 0 | plus d'« événements courts » dans le participant (relation supprimée) |
| `MapperTest` (parties Event et Logistics) | 2 | — | — | — | déplacés dans event-service et logistics-service |
| **Total d'origine** | **31** | **16** | **15** | **0** | |
| Ajoutés | | | | | 10 : lecture par lot (3), recherche (3), validation (1), erreur JSON commune (1), `X-Instance-Id` (1), `Mapper` null (1) |

La variante de `ParticipantServicesImplMockitoTest` présente sur `feature/feedback` ne diffère que par une variable intermédiaire : elle est couverte par la même reprise.

Résultat : `mvn verify` → **41 tests verts** dans participant-service (31 d'origine + 10 ajoutés), plus **5** dans `common`.

### logistics-service — origine `feature/logistics` (+ 3 tests de `feature/participant`)

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `LogisticsServiceImplMockTest` | 4 | 4 | 0 | 0 | JUnit 4 (`MockitoJUnitRunner`) → JUnit 5 (`MockitoExtension`) |
| `LogisticsServiceImplTest` | 4 | 3 | 1 | 0 | MySQL → H2 ; `testDeleteLogistics` attend désormais une `ResponseStatusException` 404 au lieu d'une `IllegalArgumentException` (HTTP 500) |
| `LogisticsControllerMockTest` | 4 | 4 | 0 | 0 | JUnit 5 ; le JSON du DTO a les mêmes champs que l'entité |
| `LogisticsControllerTest` | 4 | 4 | 0 | 0 | JUnit 5, H2 |
| `LogisticsControllerListMockTest` (feature/participant) | 2 | 2 | 0 | 0 | JUnit 5 |
| `MapperTest` (partie logistique, feature/participant) | 1 | 1 | 0 | 0 | — |
| **Total d'origine** | **19** | **18** | **1** | **0** | |
| Ajoutés | | | | | 10 : 404 (2), coût réservé (4), lecture par lot (2), validation (1), aller-retour du `Mapper` (1) |

Résultat : `mvn verify` → **29 tests verts** dans logistics-service (19 d'origine + 10 ajoutés).

### event-service — origine `feature/event` (+ `feature/event-statistics`, `feature/participant`, `feature/feedback`)

Toutes les classes d'origine sont **adaptées** : l'identifiant devient un UUID, et les repositories Participant et Logistics deviennent des clients Feign simulés.

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `EventServicesMockTest` (feature/event) | 5 | 0 | 5 | 0 | JUnit 4 → 5, `int` → UUID, clients Feign simulés |
| `EventServicesImplTest` (feature/event) | 4 | 0 | 4 | 0 | MySQL → H2, « id > 0 » → « id est un UUID » |
| `EventServicesAffectationMockTest` (feature/event-statistics) | 10 | 0 | 9 | 1 | relations → ensembles d'ID. `addAffectLog` sauvegarde toujours l'événement (les ID sont dans `db_event`). Sans logistique : liste vide au lieu de `null`. Le coût réservé est calculé par logistics-service |
| `EventServicesImplMockitoTest` (feature/feedback) | 10 | 0 | 10 | 0 | participant absent = 404 renvoyé par Feign ; `getStatus()` → `getStatusCode()` |
| `EventRestControllerMockTest` (feature/participant) | 5 | 0 | 4 | 1 | fusionné avec la variante suivante (4 + 4 → 4 tests qui gardent les vérifications des deux) ; chemins `/events…`, 201, JSON avec ID |
| `EventRestControllerMockTest` (feature/event-statistics) | 5 | 0 | 4 | 1 | voir ligne précédente |
| `EventRestControllerMockitoTest` (feature/feedback) | 5 | 0 | 4 | 1 | `EventWriteRequest` → `EventRequest` ; le contrôleur renvoie un `EventDTO` |
| `MapperTest` (partie Event, feature/participant) | 1 | 0 | 1 | 0 | `EventDTO` vide → `EventRequest` vide |
| `DemoDataLoaderMockTest` (feature/event-statistics) | 2 | 0 | 0 | 2 | voir « Suppressions » |
| **Total d'origine** | **47** | **0** | **41** | **6** | 41 tests d'origine → 37 méthodes (4 fusions) |
| Ajoutés | | | | | 28 : `RemoteCall` 404/503 (4), service : UUID et dates (2), affectations par ID, détails, organisateur absent, panne (7), contrôleur : lecture, 404 `ApiError`, validation, mise à jour, suppression, détails, recalcul (9), `Mapper` (3), repository : `@ElementCollection`, doublon de description (3) |

**Suppressions (6) et raisons :**
- `testAddParticipant`, `addParticipant` (×2) et `addParticipantMapsRequestAndReturnsServiceResult` (4 tests) : la création d'un participant (`POST /event/addPart`) appartient désormais à participant-service, où elle est testée (`ParticipantServicesImplMockitoTest`, `ParticipantRestControllerMockitoTest`) ;
- `DemoDataLoaderMockTest` (2 tests) : le `CommandLineRunner` du monolithe écrivait d'un coup dans les tables participant, logistique et événement. Avec une base par service, c'est impossible. Il est remplacé par `scripts/seed-demo.sh`, qui passe par la Gateway (EVT-42).

Résultat : `mvn verify` → **65 tests verts** dans event-service (37 d'origine + 28 ajoutés), couverture JaCoCo **96 %** des instructions.

### feedback-service — origine `feature/feedback`

Les autres classes de test de `feature/feedback` sont des copies des tests des autres branches. Elles sont comptées avec leur service : event-service ci-dessus, et statistics-service pour les tests `EventStats*`. Les tests paramétrés comptent pour une méthode ; le nombre d'exécutions est entre parenthèses.

| Classe d'origine | Tests | Repris | Adaptés | Supprimés | Détail |
|---|---:|---:|---:|---:|---|
| `FeedbackServiceTest` | 20 (24) | 0 | 20 | 0 | repositories Event et Participant → clients Feign simulés ; événement absent = 404 renvoyé par Feign ; inscription lue dans `participantIds` ; `getStatus()` → `getStatusCode()` ; UUID. Dans `doesNotMislabelOtherDatabaseErrorsAsDuplicates`, la contrainte « autre » devient une contrainte NOT NULL, car il n'y a plus de clé étrangère |
| `FeedbackControllerIntegrationTest` | 10 (21) | 0 | 10 | 0 | l'événement et le participant viennent de clients Feign simulés (`@MockitoBean`) au lieu d'entités persistées ; chemins sans context-path ; ID non numérique testé sur le participant (l'événement est un UUID) ; `/event-stats` → `/feedback/moyennes` (source des statistiques) |
| `FeedbackConcurrencyIntegrationTest` | 1 | 0 | 1 | 0 | clients Feign simulés ; toujours exactement un 201 et un 409, garantis par la contrainte d'unicité |
| `FeedbackRepositoryTest` | 6 | 0 | 6 | 0 | identifiants au lieu d'entités `Event` et `Participant` à persister |
| `RequestDtoMappingTest` | 5 | 0 | 0 | 5 | voir « Suppressions » |
| **Total d'origine** | **42 (53)** | **0** | **37** | **5** | |
| Ajoutés | | | | | 10 : `RemoteCall` (3), panne d'event-service et de participant-service (2), `/moyennes` sans appel distant (2), une seule vérification distante par lecture (1), 503 au format `ApiError` (1), moyennes groupées en base (1) |

**Suppressions (5) et raison :** `RequestDtoMappingTest` testait `EventWriteRequest`, `ParticipantWriteRequest` et `LogisticsWriteRequest`. Ce sont les DTO d'écriture des événements, participants et logistique du monolithe, pas ceux du feedback. Leurs remplaçants vivent dans les services propriétaires et y sont testés :
- `EventRequest` → `Event` : `MapperTest` d'event-service, 2 tests ;
- `ParticipantDTO` : `MapperTest` de participant-service ;
- `LogisticsDTO` : `MapperTest` de logistics-service.

Les références d'entités (`toReferenceEntity`, ensembles `participants` et `logistics` imbriqués) n'existent plus, puisque les relations sont devenues des ID.

Résultat : `mvn verify` → **62 exécutions vertes** dans feedback-service (47 méthodes), couverture JaCoCo **96 %** des instructions.

_Les autres services sont ajoutés au fil de la phase 2._
