#!/usr/bin/env bash
# =============================================================================
#  Tests de deployer.sh et detruire.sh SANS AWS (phase 12) : aws, terraform, ansible, rsync, ssh et
#  curl sont remplacés par de faux outils qui journalisent leurs appels. On vérifie l'ORDRE des
#  étapes, la confirmation obligatoire avant toute création ou destruction, et l'arrêt si les
#  identifiants AWS sont absents.   Usage : bash infra/aws/tests/test-deployer.sh
# =============================================================================
set -uo pipefail
RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"
JOURNAL="$TMP/appels.log"
echc=0

# Faux outils : chaque appel est journalisé ; quelques réponses minimales
for outil in aws terraform ansible-playbook ansible-galaxy rsync ssh curl; do
  cat > "$TMP/bin/$outil" <<EOF
#!/usr/bin/env bash
echo "$outil \$*" >> "$JOURNAL"
case "$outil \$*" in
  "aws sts"*) [[ "\${FAUX_AWS_KO:-0}" == "1" ]] && exit 254; echo "arn:aws:sts::123456789012:assumed-role/voclabs/user" ;;
  *"output -raw ip_publique"*) echo "203.0.113.10" ;;
esac
exit 0
EOF
  chmod +x "$TMP/bin/$outil"
done
ssh-keygen -q -t ed25519 -N "" -f "$TMP/cle" >/dev/null
export PATH="$TMP/bin:$PATH" SSH_CLE="$TMP/cle" ADMIN_CIDR="196.203.10.20/32"

verifier() { # <description> <condition shell>
  if eval "$2"; then echo "OK   $1"; else echo "KO   $1"; echc=$((echc + 1)); fi
}
ligne() { grep -n -m1 -E "$1" "$JOURNAL" | cut -d: -f1; }

# 1. Refus de la confirmation : rien n'est créé
: > "$JOURNAL"
echo non | bash "$RACINE/infra/aws/deployer.sh" >/dev/null 2>&1
code=$?
verifier "refus de la confirmation : code d'erreur" "[[ $code -ne 0 ]]"
verifier "refus de la confirmation : plan affiché" "grep -q 'terraform .* plan ' '$JOURNAL'"
verifier "refus de la confirmation : aucun apply" "! grep -q ' apply ' '$JOURNAL'"

# 2. Identifiants AWS absents : arrêt avant Terraform
: > "$JOURNAL"
FAUX_AWS_KO=1 CONFIRMER=oui bash "$RACINE/infra/aws/deployer.sh" >/dev/null 2>&1
code=$?
verifier "identifiants absents : code d'erreur" "[[ $code -ne 0 ]]"
verifier "identifiants absents : Terraform jamais appelé" "! grep -q '^terraform' '$JOURNAL'"

# 3. Déploiement confirmé : toutes les étapes, dans l'ordre
: > "$JOURNAL"
CONFIRMER=oui bash "$RACINE/infra/aws/deployer.sh" > "$TMP/sortie.log" 2>&1
code=$?
verifier "déploiement confirmé : succès" "[[ $code -eq 0 ]]"
verifier "ordre : identifiants → plan → apply → Ansible → rsync → compose → tests de fumée" \
  "(( $(ligne '^aws sts') < $(ligne ' plan ') && $(ligne ' plan ') < $(ligne ' apply ') && $(ligne ' apply ') < $(ligne '^ansible-playbook') && $(ligne '^ansible-playbook') < $(ligne '^rsync') && $(ligne '^rsync') < $(ligne 'docker compose') && $(ligne 'docker compose') < $(ligne '^curl') ))"
verifier "plan limité à admin_cidr et au type d'instance" "grep -q 'admin_cidr=196.203.10.20/32' '$JOURNAL' && grep -q 'instance_type=t3.large' '$JOURNAL'"
verifier "apply du plan affiché (fichier de plan)" "grep -q 'apply -input=false events.tfplan' '$JOURNAL'"
verifier "profil lite et .env généré sur l'EC2" "grep -q 'init-env.sh && CONFIG_GIT_LABEL=.* docker compose --profile lite up' '$JOURNAL'"
verifier "rsync sans .env ni état Terraform" "grep -q \"exclude .env\" '$JOURNAL' && grep -q 'tfstate' '$JOURNAL'"
verifier "3 tests de fumée" "[[ $(grep -c '^curl' "$JOURNAL") -eq 3 ]]"

# 4. Mode à blanc : aucun appel Terraform réel
: > "$JOURNAL"
DRY_RUN=1 CONFIRMER=oui bash "$RACINE/infra/aws/deployer.sh" > "$TMP/blanc.log" 2>&1
verifier "DRY_RUN : aucun appel à terraform" "! grep -q '^terraform' '$JOURNAL'"
verifier "DRY_RUN : commandes affichées" "grep -q '\[à blanc\] terraform' '$TMP/blanc.log'"

# 5. Destruction : confirmation obligatoire
: > "$JOURNAL"
echo non | bash "$RACINE/infra/aws/detruire.sh" >/dev/null 2>&1
verifier "destruction refusée : aucun destroy" "! grep -q ' destroy ' '$JOURNAL'"
: > "$JOURNAL"
echo détruire | bash "$RACINE/infra/aws/detruire.sh" >/dev/null 2>&1
verifier "destruction confirmée : destroy appelé" "grep -q ' destroy ' '$JOURNAL'"

echo
if (( echc == 0 )); then echo "Tous les tests de déploiement passent."; else echo "$echc test(s) en échec"; exit 1; fi
