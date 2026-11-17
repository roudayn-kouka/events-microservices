#!/usr/bin/env bash
# =============================================================================
#  Destruction de l'EC2 et de son réseau (phase 12) : make aws-down. Confirmation obligatoire
#  (sauf CONFIRMER=oui). Les données de la démonstration (MySQL, Axon) disparaissent avec l'instance.
# =============================================================================
set -euo pipefail
RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF="$RACINE/infra/aws/terraform"
export AWS_REGION="${AWS_REGION:-us-east-1}"

faire() { if [[ "${DRY_RUN:-0}" == "1" ]]; then printf '   [à blanc] %s\n' "$*"; else "$@"; fi; }

aws sts get-caller-identity --query Arn --output text || { echo "Identifiants AWS absents ou expirés"; exit 1; }
faire terraform -chdir="$TF" init -input=false
faire terraform -chdir="$TF" state list || true

if [[ "${CONFIRMER:-}" != "oui" ]]; then
  read -r -p "DÉTRUIRE l'EC2 events et son réseau ? Taper « détruire » : " reponse
  [[ "$reponse" == "détruire" ]] || { echo "Abandon : rien n'a été détruit."; exit 1; }
fi
# Les variables obligatoires n'influencent pas la destruction : valeurs neutres
faire terraform -chdir="$TF" destroy -input=false -auto-approve \
  -var "admin_cidr=192.0.2.1/32" -var "ssh_public_key_path=$TF/tests/fixtures/id_ed25519.pub"
echo "Ressources détruites."
