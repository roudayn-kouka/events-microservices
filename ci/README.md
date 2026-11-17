# Intégration continue (phase 11)

Pipeline Jenkins déclaratif (`Jenkinsfile`, à la racine) exécuté par le Jenkins de `~/devops`. Il ne demande **ni push ni GitHub** : il lit ce dépôt local, en lecture seule.

```
Changements ─► Build et tests ─► ┌ SonarQube ┐ ─► Quality gate ─► Images Docker + Trivy ─► Push (off) ─► AWS (off)
 (git diff)     (mvn verify,     └ Frontend  ┘     (API Sonar)     (en parallèle, services
                 JaCoCo bloquant)  (en parallèle)                    modifiés seulement)
```

## Mise en place (déjà faite, rejouable)

```bash
docker start jenkins sonarqube           # conteneurs existants de ~/devops (jamais recréés)
bash ci/git-daemon.sh start              # conteneur events-git : dépôt monté en :ro, servi en git://
bash ci/installer-job.sh                 # copie ci/jenkins/config.xml dans jenkins_home/jobs/events-microservices/, puis docker restart jenkins
```

| Élément | Choix | Pourquoi |
|---|---|---|
| Source | `git://events-git/events-microservices`, branche `develop` | Le conteneur Jenkins n'a pas de montage vers `~/events-microservices`, et le recréer pour en ajouter un était exclu. Le protocole `git://` est en lecture seule et le dépôt est monté en `:ro` dans `events-git`. `file://` est bloqué par le plugin Git (SECURITY-2478). |
| Création du job | `config.xml` copié dans `jenkins_home`, puis redémarrage | Pas besoin du mot de passe administrateur. Aucun autre job n'est touché. |
| Déclenchement | Scrutation `H/2 * * * *` | Un commit local déclenche un build. Après le push : webhook GitHub (`docs/APRES-PUSH.md`). |
| Sonar | identifiant Jenkins `sonar-token` (existant), `http://sonarqube:9000` | Mêmes conventions que le job de l'équipe. Le jeton n'est jamais affiché. |
| Quality gate | `ci/quality-gate.sh` (API Sonar, `ceTaskUrl` puis `project_status`) | Pas de serveur Sonar déclaré dans Jenkins ni de webhook Sonar → Jenkins : `waitForQualityGate` est impossible pour l'instant. |
| Trivy | `aquasec/trivy:0.65.0` via le socket Docker, HIGH et CRITICAL corrigeables, rapports archivés | Bloquant seulement avec `TRIVY_BLOQUANT=true` (CRITICAL corrigeables). |
| Push, AWS | `PUSH_IMAGES=false`, `DEPLOY_AWS=false` | Rien n'est publié ni déployé sans décision explicite. |

## Détection des changements (`ci/changements.sh`)

- Référence : `GIT_PREVIOUS_SUCCESSFUL_COMMIT` (dernier build réussi). Sans référence, ou avec `TOUT_RECONSTRUIRE=true`, tout est reconstruit.
- Un changement commun (`pom.xml`, `common/`, `events-api/`, `Jenkinsfile`, `ci/`) reconstruit tout.
- Sinon, seuls les services modifiés sont reconstruits en image et scannés par Trivy. Le frontend n'est construit que si `frontend/` a changé, et le build Java est sauté si aucun code Java n'a changé (documentation seule).
- Les **tests Maven portent toujours sur tout le réacteur** quand du Java a changé : un service dépend de `common` et de `events-api`, Sonar analyse le projet entier, et le seuil JaCoCo est vérifié partout. Le gain de la détection porte sur les étapes coûteuses : images, Trivy et frontend.

Exemples vérifiés : un commit qui ne touche que `frontend/` donne `SERVICES=frontend`, `JAVA=false` ; un commit qui touche le POM parent reconstruit tout.
