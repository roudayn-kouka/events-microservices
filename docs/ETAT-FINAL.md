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

_Les autres services sont ajoutés au fil de la phase 2._
