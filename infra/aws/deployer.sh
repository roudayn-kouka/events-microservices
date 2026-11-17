#!/usr/bin/env bash
# =============================================================================
#  Déploiement AWS — cible A (phase 12) : une EC2 + Docker Compose, code envoyé par rsync.
#  make aws-up  (ou : bash infra/aws/deployer.sh). Voir docs/AWS-RUNBOOK.md.
#
#  Étapes : identifiants AWS → terraform plan → CONFIRMATION → apply → Ansible → rsync →
#           docker compose up (profil lite) sur l'EC2 → tests de fumée → URL.
#  Variables :
#    ADMIN_CIDR   CIDR autorisé (défaut : ton IP publique /32, via checkip.amazonaws.com)
#    SSH_CLE      clé privée SSH (défaut ~/.ssh/id_ed25519 ; la clé publique est dérivée)
#    INSTANCE     type d'instance (défaut t3.large ; t3.medium en repli)
#    PROFILS      profils Compose sur l'EC2 (défaut « lite » ; « lite observability » avec Grafana…)
#    CONFIRMER=oui  pas de question avant l'apply (Jenkins, DEPLOY_AWS=true)
#    DRY_RUN=1    affiche les commandes AWS/Terraform/SSH sans les exécuter
#  Rien n'est jamais lancé sur AWS sans plan affiché puis confirmation (ou CONFIRMER=oui).
# =============================================================================
set -euo pipefail

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF="$RACINE/infra/aws/terraform"
ANS="$RACINE/infra/aws/ansible"
export AWS_REGION="${AWS_REGION:-us-east-1}"
SSH_CLE="${SSH_CLE:-$HOME/.ssh/id_ed25519}"
INSTANCE="${INSTANCE:-t3.large}"
PROFILS="${PROFILS:-lite}"
DOSSIER=/opt/events-microservices
BRANCHE="$(git -C "$RACINE" branch --show-current)"

etape() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
faire() { # exécute, ou affiche seulement en DRY_RUN
  if [[ "${DRY_RUN:-0}" == "1" ]]; then printf '   [à blanc] %s\n' "$*"; else "$@"; fi
}

etape "Prérequis"
for outil in aws terraform ansible-playbook ansible-galaxy rsync ssh ssh-keygen curl git; do
  command -v "$outil" >/dev/null || { echo "Outil manquant : $outil"; exit 1; }
done
[[ -f "$SSH_CLE" ]] || { echo "Clé SSH introuvable : $SSH_CLE (ssh-keygen -t ed25519)"; exit 1; }
CLE_PUB="$(mktemp)"
trap 'rm -f "$CLE_PUB"' EXIT
ssh-keygen -y -f "$SSH_CLE" > "$CLE_PUB"
for jar in config-server eureka-server api-gateway participant-service logistics-service event-service feedback-service statistics-service; do
  compgen -G "$RACINE/$jar/target/$jar-*.jar" >/dev/null || { echo "JAR absent pour $jar : lancer d'abord make build"; exit 1; }
done
if ! git -C "$RACINE" diff --quiet HEAD -- config-repo; then
  echo "config-repo/ a des modifications non commitées : le Config Server de l'EC2 lit la branche $BRANCHE commitée."
  exit 1
fi
ADMIN_CIDR="${ADMIN_CIDR:-$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]')/32}"
echo "   région $AWS_REGION ; instance $INSTANCE ; accès réservé à $ADMIN_CIDR ; branche $BRANCHE ; profils : $PROFILS"

etape "Identifiants AWS (Learner Lab démarré ?)"
aws sts get-caller-identity --query Arn --output text || {
  echo "Identifiants absents ou expirés : recopier « AWS Details » du Learner Lab dans ~/.aws/credentials"; exit 1; }

etape "Plan Terraform"
TFVARS=(-var "admin_cidr=$ADMIN_CIDR" -var "instance_type=$INSTANCE" -var "ssh_public_key_path=$CLE_PUB" -var "region=$AWS_REGION")
faire terraform -chdir="$TF" init -input=false
faire terraform -chdir="$TF" plan -input=false "${TFVARS[@]}" -out=events.tfplan

if [[ "${CONFIRMER:-}" != "oui" ]]; then
  read -r -p "Créer ces ressources sur AWS (facturées) ? Taper « oui » : " reponse
  [[ "$reponse" == "oui" ]] || { echo "Abandon : rien n'a été créé."; exit 1; }
fi

etape "Création (terraform apply du plan affiché)"
faire terraform -chdir="$TF" apply -input=false events.tfplan
if [[ "${DRY_RUN:-0}" == "1" ]]; then IP=203.0.113.10; else IP="$(terraform -chdir="$TF" output -raw ip_publique)"; fi
echo "   IP publique : $IP"

SSH=(ssh -i "$SSH_CLE" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 "ubuntu@$IP")
etape "Attente de SSH"
if [[ "${DRY_RUN:-0}" != "1" ]]; then
  for _ in $(seq 40); do "${SSH[@]}" true 2>/dev/null && break; sleep 5; done
fi

etape "Préparation de l'hôte (Ansible)"
printf '[events]\nec2 ansible_host=%s ansible_user=ubuntu ansible_ssh_private_key_file=%s\n' "$IP" "$SSH_CLE" > "$ANS/inventory.ini"
faire ansible-galaxy collection install -r "$ANS/requirements.yml"
faire env ANSIBLE_CONFIG="$ANS/ansible.cfg" ansible-playbook -i "$ANS/inventory.ini" "$ANS/site.yml"

etape "Envoi du code (rsync : sources, .git pour le Config Server, JAR déjà construits)"
faire rsync -az --delete -e "ssh -i $SSH_CLE -o StrictHostKeyChecking=accept-new" \
  --exclude node_modules --exclude .angular --exclude 'frontend/dist' --exclude 'frontend/e2e' \
  --exclude '.env' --exclude 'infra/aws/terraform/.terraform' --exclude '*.tfstate*' --exclude '*.tfplan' \
  --include '*/' --include '*/target/*.jar' --exclude '*/target/**' \
  "$RACINE/" "ubuntu@$IP:$DOSSIER/"

etape "Démarrage de la stack sur l'EC2 (.env généré sur place, profils : $PROFILS)"
PROFILS_COMPOSE=""
for p in $PROFILS; do PROFILS_COMPOSE+=" --profile $p"; done
faire "${SSH[@]}" "cd $DOSSIER && bash scripts/init-env.sh && CONFIG_GIT_LABEL=$BRANCHE docker compose$PROFILS_COMPOSE up -d --build --wait"

etape "Tests de fumée (depuis ton poste, autorisé par admin_cidr)"
ok=0
for url in "http://$IP:8088/actuator/health" "http://$IP:4200/healthz" "http://$IP:8088/api/stats/summary"; do
  if faire curl -fsS -m 10 -o /dev/null "$url"; then echo "   OK  $url"; else echo "   KO  $url"; ok=1; fi
done

etape "URL de démonstration"
if [[ "${DRY_RUN:-0}" != "1" ]]; then terraform -chdir="$TF" output urls; fi
echo "   Postman : environnement postman/aws.postman_environment.json, IP_PUBLIQUE_EC2 = $IP"
echo "   Penser à détruire après la démonstration : make aws-down"
exit "$ok"
