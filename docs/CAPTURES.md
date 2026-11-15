# Captures d'écran à réaliser

Dans l'ordre des slides (voir `PRESENTATION-PLAN.md`). Cocher au fur et à mesure.
Pour chaque capture : ce qu'il faut ouvrir, et ce qui doit être visible.

## Git et JIRA

- [ ] **C1 — Backlog JIRA importé** : projet `EVT` → Backlog (ou Liste). Visible : les epics par technologie, des clés EVT-18 à EVT-22 au statut *Terminé*.
- [ ] **C2 — Historique GitFlow** : terminal WSL, `git log --oneline --graph --all`. Visible : `main`, `develop`, `feature/EVT-18-init-monorepo`, la fusion `--no-ff`, des messages qui citent EVT-xx.
- [ ] **C3 — POM parent** : `pom.xml` ouvert dans l'éditeur. Visible : Spring Boot 3.5.16, `spring-cloud.version` 2025.0.3, `axon.version` 4.13.3.
- [ ] **C4 — `mvn validate`** : terminal. Visible : `BUILD SUCCESS` et la version Spring Cloud affichée par `help:evaluate`.

_Les captures des phases suivantes sont ajoutées à la fin de chaque phase._
