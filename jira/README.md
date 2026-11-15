# Backlog JIRA — import

`backlog.csv` contient 88 tickets : 17 epics (une par technologie), 38 stories et 33 tâches.
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

5. Valider. Vérifier que les clés vont de **EVT-1** à **EVT-88**, et que les tickets de la phase 0 (EVT-18 à EVT-22) sont **Done**.

## Lien avec Git

Chaque commit cite sa clé : `feat(statistics): projection CQRS EVT-47`. Chaque branche de phase porte la clé de sa story principale : `feature/EVT-47-projections-statistics`. Si Jira est relié à GitHub (application *GitHub for Jira*), les commits apparaissent dans les tickets après le push.
