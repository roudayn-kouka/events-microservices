# AWS — runbook de déploiement (cible A : une EC2 + Docker Compose)

Tout est préparé et vérifié **sans rien créer sur AWS** (`make aws-check`). Le déploiement réel est à lancer **par toi**, Learner Lab démarré, avec `make aws-up`. Il affiche le plan Terraform et attend ta confirmation avant toute création.

| | |
|---|---|
| Région | `us-east-1` (Learner Lab) |
| Instance | `t3.large` (8 Go) + swap 4 Go, profil Compose `lite`. Repli : `INSTANCE=t3.medium` |
| Coût estimé | ≈ **0,10 $/h** (instance 0,083 $ + disque gp3 40 Go + IPv4 publique). Détruire après la démonstration |
| IAM | aucune création : `LabInstanceProfile` existant |
| Accès | SSH et ports de démonstration réservés à **ton IP** (`admin_cidr`, détectée automatiquement) |
| Code | envoyé par `rsync` (sources, `.git` pour le Config Server, JAR construits en local). **Ni GitHub ni Docker Hub** |
| Secrets | `.env` **généré sur l'EC2** (`scripts/init-env.sh`), jamais envoyé |

```
make aws-up : identifiants (sts) → terraform plan → confirmation « oui » → apply → Ansible (Docker, swap)
              → rsync → docker compose --profile lite up --build --wait → tests de fumée → URL
make aws-down : confirmation « détruire » → terraform destroy
```

---

## 0. Vérifications sans AWS (déjà vertes)

```bash
make aws-check
```
Cette commande lance :
- Terraform : `fmt`, `validate`, et `terraform test` avec 5 tests sur un fournisseur **simulé** (instance du lab, IMDSv2, disque chiffré, accès limité à `admin_cidr`, refus de `0.0.0.0/0` et des types hors lab) ;
- Ansible : `--syntax-check` et `ansible-lint` (profil `production`) ;
- ShellCheck sur tous les scripts ;
- les 16 tests des scripts de déploiement avec de faux `aws`, `terraform`, `ssh` et `rsync` (ordre des étapes, confirmation obligatoire, arrêt si les identifiants manquent, mode à blanc).

## 1. Préparer le poste (une fois)

```bash
aws --version && terraform version && ansible --version     # déjà installés dans WSL
ls ~/.ssh/id_ed25519 || ssh-keygen -t ed25519               # clé SSH pour l'EC2
cd ~/events-microservices && make build                      # JAR à jour (le rsync envoie target/*.jar)
git status                                                   # config-repo/ commité (lu par le Config Server)
```

## 2. Démarrer le Learner Lab et copier les identifiants

1. AWS Academy → *Learner Lab* → **Start Lab** (voyant vert).
2. *AWS Details* → *AWS CLI* → **Show** : copier le bloc dans `~/.aws/credentials` (section `[default]`).
3. Vérifier :
   ```bash
   aws sts get-caller-identity     # Arn …:assumed-role/voclabs/…
   ```
Les identifiants expirent à l'arrêt du lab (environ 4 h) : recopier à chaque session.

## 3. Déployer

```bash
make aws-up
# variantes :
INSTANCE=t3.medium make aws-up                 # si t3.large est refusé
PROFILS="lite observability" make aws-up       # avec Prometheus, Grafana, Zipkin (t3.large au minimum)
ADMIN_CIDR=196.203.10.20/32 make aws-up        # IP imposée (sinon : checkip.amazonaws.com)
DRY_RUN=1 make aws-up                          # tout afficher sans rien exécuter côté AWS
```
Le script affiche le plan, puis demande : *Créer ces ressources sur AWS (facturées) ? Taper « oui »*. Prévoir environ 10 à 15 minutes : instance, Docker, envoi d'environ 700 Mo au premier passage, puis build des images sur place.

À la fin, il affiche les URL (`terraform output urls`) :

| Service | URL |
|---|---|
| Frontend Angular | `http://<IP>:4200` |
| Gateway (API, Swagger) | `http://<IP>:8088/api/…`, `http://<IP>:8088/swagger-ui.html` |
| Eureka | `http://<IP>:8761` |
| Axon Server | `http://<IP>:8024` |
| Grafana, Prometheus, Zipkin (si `observability`) | `:3001`, `:9091`, `:9411` |

## 4. Vérifier la démonstration sur AWS

```bash
IP=$(terraform -chdir=infra/aws/terraform output -raw ip_publique)
curl -s http://$IP:8088/actuator/health | jq .status          # "UP"
ssh ubuntu@$IP 'cd /opt/events-microservices && docker compose ps --format "{{.Name}} {{.Status}}"'
ssh ubuntu@$IP 'cd /opt/events-microservices && make seed'    # jeu de démonstration
```
- **Postman / Newman** : environnement `postman/aws.postman_environment.json`, remplacer `IP_PUBLIQUE_EC2` par `$IP`, puis lancer la collection complète.
- **Pannes** : sur l'EC2, `make panne N=1` à `N=5` fonctionnent de la même façon (profil lite : une instance de participant-service).

## 5. Détruire (à ne pas oublier)

```bash
make aws-down     # taper « détruire »
aws ec2 describe-instances --filters Name=tag:project,Values=events-ms \
  --query 'Reservations[].Instances[].State.Name'            # [] ou "terminated"
```

## 6. Depuis Jenkins (facultatif)

Paramètre `DEPLOY_AWS=true` du job `events-microservices`. Il faut d'abord :
1. mettre à jour les identifiants Jenkins `aws-access-key-id`, `aws-secret-access-key` et `aws-session-token` avec ceux du lab ;
2. installer dans le conteneur Jenkins `terraform` (déjà présent), `ansible`, `rsync` et une clé SSH (`SSH_CLE`) : ils ne sont pas tous dans l'image de `~/devops`. **Non fait**, pour ne pas modifier ton Jenkins.

L'étape lance `CONFIRMER=oui infra/aws/deployer.sh` : cocher `DEPLOY_AWS` vaut confirmation, puisqu'aucune question n'est possible en CI.

## 7. Problèmes connus et parades

| Symptôme | Cause | Parade |
|---|---|---|
| `UnauthorizedOperation` sur `RunInstances` | type d'instance refusé par le lab | `INSTANCE=t3.medium make aws-up` |
| `ExpiredToken` | lab arrêté ou identifiants de la session précédente | Start Lab, puis recopier *AWS Details* |
| Un service reste `starting` | mémoire (t3.medium) | `PROFILS=lite` seulement ; `docker stats` sur l'EC2 |
| URL injoignables depuis un autre réseau | `admin_cidr` = IP d'origine | relancer `make aws-up` (le plan ne modifie que le security group) |
| Le Config Server ne trouve pas la configuration | `config-repo/` non commité, ou branche absente | commiter, puis relancer `make aws-up` |

## 8. État Terraform

L'état est **local** (`infra/aws/terraform/terraform.tfstate`, ignoré par Git) : un seul poste, et un lab éphémère. Pour le partager, ajouter un backend S3 (bucket créé à la main dans le lab) dans `versions.tf`, puis `terraform init -migrate-state`.
