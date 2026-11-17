# Après le push GitHub

Ce qui ne peut se faire qu'une fois le dépôt publié. Tout le reste tourne déjà en local, y compris le pipeline Jenkins (job `events-microservices`, dépôt local servi en lecture seule : `ci/README.md`).
Les commandes sont à lancer **par toi**. Je n'ai fait aucun push et créé aucun dépôt distant.

> Valeurs à remplacer : `<compte-github>` (ton compte, ou l'organisation de l'équipe) et `<compte-dockerhub>`.

---

## 1. Publier le dépôt

```bash
cd ~/events-microservices
git status                      # doit être vide
git remote -v                   # aucun remote pour l'instant
git log --oneline -1 main       # fusion de la release v1.0.1
# Créer le dépôt VIDE sur GitHub (sans README, ni .gitignore, ni licence), puis :
git remote add origin git@github.com:<compte-github>/events-microservices.git
git push -u origin --all        # TOUTES les branches : main, develop, feature/EVT-…, fix/…, chore/…
git push origin --tags          # TOUS les tags : v0.6.0, v0.10.0, v1.0.0, v1.0.1
git ls-remote --heads --tags origin | wc -l   # même nombre que : git for-each-ref refs/heads refs/tags | wc -l
```

Vérifier : toutes les branches (dont `main` et `develop`) et les 4 tags sont visibles sur GitHub ; branche par défaut : `main`. `.env` n'est **pas** publié (il est dans `.gitignore`, contrôle : `git check-ignore .env`).

## 2. Jenkins : passer du dépôt local à GitHub

Le job lit aujourd'hui `git://events-git/events-microservices` (conteneur `events-git`, lecture seule). Après le push :

1. **Job** → *Configure* → *Pipeline* → *Repository URL* : `https://github.com/<compte-github>/events-microservices.git`. Ajouter un identifiant seulement si le dépôt est privé (jeton GitHub en *Username with password*).
2. **Déclencheur** : cocher *GitHub hook trigger for GITScm polling* et décocher *Poll SCM*. Dans le `Jenkinsfile`, décommenter :
   ```groovy
   triggers { githubPush() }
   ```
   puis commiter et pousser.
3. **Webhook GitHub** : *Settings* → *Webhooks* → *Add webhook*.
   - *Payload URL* : `http://<adresse-publique-de-jenkins>:8080/github-webhook/` (avec la barre finale) ;
   - *Content type* : `application/json` ; événement : *Just the push event*.
   - **Ton Jenkins local n'est pas joignable depuis Internet.** Deux possibilités : un relais (`smee.io` : `npx smee-client --url https://smee.io/<canal> --target http://localhost:8080/github-webhook/`), ou un Jenkins sur une machine publique (EC2).
4. Vérifier : un commit poussé sur `develop` déclenche un build en quelques secondes. GitHub affiche une livraison **200** dans *Recent Deliveries*.
5. Arrêter le dépôt local servi à Jenkins : `bash ci/git-daemon.sh stop`.

## 3. GitHub for Jira : relier les commits aux tickets EVT

Les commits citent leur clé (`… EVT-76`) et les branches aussi (`fix/EVT-76-…`) : une fois le dépôt relié, Jira les affiche dans chaque ticket.

1. Jira → **Applications → Explorer d'autres applications** → rechercher **GitHub for Jira** (éditeur Atlassian) → **Installer**.
2. **Démarrer** → *Connect GitHub organization* → se connecter à GitHub → choisir le compte `<compte-github>` → **Install** l'application GitHub sur **le dépôt `events-microservices` seulement** (*Only select repositories*).
3. Retour dans Jira : la connexion apparaît avec l'état **Backfill en cours**, puis **Terminé** (l'historique déjà poussé est importé).
4. Vérifier : ouvrir **EVT-76** → panneau **Développement** → les commits du correctif Sonar (3 commits et 3 fusions citent EVT-76) et la branche `fix/EVT-76-sonar-fiabilite-hotspots` ; ouvrir **EVT-55** → commits du circuit du cron. Le lien mène au commit sur GitHub.
5. Facultatif : dans le tableau Scrum, les tickets affichent l'icône de branche ou de commit.

## 4. Docker Hub : pousser les images

L'étape « Push des images » existe, mais elle est désactivée par défaut (`PUSH_IMAGES=false`).

1. Vérifier l'identifiant Jenkins `dockerHub` (*Username with password*, déjà présent dans ton Jenkins) : compte `<compte-dockerhub>`, avec un **jeton d'accès** Docker Hub plutôt que le mot de passe.
2. *Build with Parameters* → `PUSH_IMAGES=true` (avec `TOUT_RECONSTRUIRE=true` pour toutes les images).
3. Vérifier sur Docker Hub : les dépôts `<compte-dockerhub>/events-<service>` avec le tag `ci-<numéro de build>`.

## 5. SonarQube : quality gate par webhook (facultatif)

Le pipeline attend aujourd'hui le quality gate en interrogeant l'API (`ci/quality-gate.sh`), donc sans webhook. Pour utiliser `waitForQualityGate` à la place :

1. Jenkins → *Manage Jenkins* → *System* → *SonarQube servers* : nom `sonarqube`, URL `http://sonarqube:9000`, jeton = identifiant `sonar-token`.
2. SonarQube → *Administration* → *Webhooks* : `http://jenkins:8080/sonarqube-webhook/`.
3. Dans le `Jenkinsfile`, remplacer l'étape « Quality gate » par :
   ```groovy
   withSonarQubeEnv('sonarqube') { sh 'mvn -B sonar:sonar' }
   timeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }
   ```

## 6. AWS

Voir **[AWS-RUNBOOK.md](AWS-RUNBOOK.md)**. Le déploiement ne dépend pas du push : le code est envoyé par `rsync`. L'étape Jenkins « Déploiement AWS » (`DEPLOY_AWS=true`) utilise les identifiants `aws-access-key-id`, `aws-secret-access-key` et `aws-session-token` de ton Jenkins. Ils sont à mettre à jour à chaque rechargement du Learner Lab.

## 7. Contrôle final

- [ ] dépôt, toutes les branches et les 4 tags visibles sur GitHub ;
- [ ] GitHub for Jira : EVT-76 affiche ses commits (capture C103) ;
- [ ] un push sur `develop` déclenche le job Jenkins par webhook ;
- [ ] (facultatif) images sur Docker Hub avec `PUSH_IMAGES=true` ;
- [ ] captures de CAPTURES.md, section « Après le push ».
