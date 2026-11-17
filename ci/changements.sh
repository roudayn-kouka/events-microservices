#!/usr/bin/env bash
# =============================================================================
#  Détection des changements (pipeline Jenkins, phase 11).
#  Usage : ci/changements.sh <commit de référence ou vide> [fichier de sortie] [commit cible, HEAD]
#    SERVICES : images Docker à reconstruire et à scanner (Trivy), séparées par des virgules
#    FRONTEND : true si frontend/ a changé
#    JAVA     : true si du code ou de la configuration Maven a changé (build, tests, Sonar)
#  Un changement commun (POM parent, common, events-api, Jenkinsfile, ci/) reconstruit tout.
#  Sans référence (premier build, ou historique réécrit), tout est reconstruit.
# =============================================================================
set -euo pipefail

SERVICES_JAVA="config-server eureka-server api-gateway participant-service logistics-service event-service feedback-service statistics-service"
REFERENCE="${1:-}"
SORTIE="${2:-ci-changements.properties}"
CIBLE="${3:-HEAD}"

tout() {
  echo "SERVICES=$(tr ' ' ',' <<<"$SERVICES_JAVA"),frontend"
  echo "FRONTEND=true"
  echo "JAVA=true"
}

if [[ -z "$REFERENCE" ]] || ! git cat-file -e "$REFERENCE^{commit}" 2>/dev/null; then
  echo "Pas de commit de référence : tout est reconstruit." >&2
  tout > "$SORTIE"
  cat "$SORTIE"
  exit 0
fi

fichiers=$(git diff --name-only "$REFERENCE" "$CIBLE")
echo "Fichiers modifiés depuis ${REFERENCE:0:7} :" >&2
while IFS= read -r f; do echo "  $f"; done <<<"$fichiers" >&2

if grep -qE '^(pom\.xml|common/|events-api/|Jenkinsfile|ci/)' <<<"$fichiers"; then
  echo "Changement commun : tout est reconstruit." >&2
  tout > "$SORTIE"
else
  services=()
  for s in $SERVICES_JAVA; do
    grep -q "^$s/" <<<"$fichiers" && services+=("$s")
  done
  frontend=false
  if grep -q '^frontend/' <<<"$fichiers"; then
    frontend=true
    services+=("frontend")
  fi
  java=false
  if grep -qE '^([a-z-]+-service|api-gateway|config-server|eureka-server)/' <<<"$fichiers"; then
    java=true
  fi
  {
    echo "SERVICES=$(IFS=,; echo "${services[*]:-}")"
    echo "FRONTEND=$frontend"
    echo "JAVA=$java"
  } > "$SORTIE"
fi
cat "$SORTIE"
