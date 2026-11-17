# Backlog JIRA — import

`backlog.csv` contient 89 tickets : 17 epics (une par technologie), 39 stories et 33 tâches. État final : 83 « Done », 6 « Won't Do ». EVT-89 (statistics-service AVANT CQRS) a été ajouté pendant la phase 2 : si le backlog est déjà importé, le créer à la main sous l'epic EVT-4.
La colonne `Issue Id` donne la clé attendue : la ligne `Issue Id = 12` devient **EVT-12**, à condition d'importer dans un projet de clé `EVT` **vide**. Les tickets sont alors créés dans l'ordre du fichier.

## Importer (Jira Cloud)

1. Créer un projet **Scrum**, nom `Events Microservices`, clé **`EVT`**.
2. ⚙ **Paramètres système → Importation externe du système → CSV** (ou, dans le projet : **⋯ → Importer des tickets depuis un CSV**).
3. Choisir `jira/backlog.csv`, encodage UTF-8, séparateur virgule.
4. Correspondance des colonnes :

   | Colonne CSV | Champ Jira |
   |---|---|
   | Issue Id | Issue Id |
   | Parent Id | Parent Id (crée la hiérarchie epic → story → tâche) |
   | Issue Type | Issue Type |
   | Summary | Summary |
   | Description | Description |
   | Priority | Priority |
   | Labels | Labels (`;` sépare plusieurs étiquettes) |
   | Story Points | Story point estimate |
   | Status | Status (To Do / Done) |

5. Valider. Vérifier que les clés vont de **EVT-1** à **EVT-89**. Statuts utilisés : Done et **Won't Do** (Keycloak : EVT-11, 66 à 68 et 71, retiré du périmètre ; WireMock : EVT-73) ; si ce statut n'existe pas dans le workflow JIRA, choisir « Done » avec la résolution « Won't Do », et que les tickets de la phase 0 (EVT-18 à EVT-22) sont **Done**.

## Import réalisé (2026-11-17)

- L'import a créé **EVT-1 à EVT-89**, dans l'ordre du fichier : les clés correspondent à la colonne `Issue Id`.
- Les 33 lignes de type **Task** ont été importées comme **Sous-tâches** (Subtask) : dans Jira, seule une Sous-tâche peut avoir une Story pour parent (une Task ne peut avoir qu'une Epic pour parent).
  - 18 de ces tâches ont une Story pour parent : elles sont des Sous-tâches de leur Story.
  - 15 ont **directement une Epic** pour parent dans le CSV (EVT-21, 22, 42, 53 à 57, 63, 68, 71, 73, 74, 83, 88). Une Sous-tâche ne peut pas non plus avoir d'Epic pour parent : vérifier dans Jira leur rattachement. S'il manque, les passer en type « Tâche » (*Modifier le type de ticket*) avec l'Epic pour parent.

## Lien avec Git

Chaque commit cite sa clé : `feat(statistics): projection CQRS EVT-47`. Chaque branche de phase porte la clé de sa story principale : `feature/EVT-47-projections-statistics`. Si Jira est relié à GitHub (application *GitHub for Jira*), les commits apparaissent dans les tickets après le push.
