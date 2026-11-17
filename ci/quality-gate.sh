#!/usr/bin/env bash
# =============================================================================
#  Quality gate SonarQube sans webhook (phase 11) : attend la fin du traitement de l'analyse
#  (ceTaskUrl de target/sonar/report-task.txt), puis lit le statut du quality gate.
#  Échoue (code 1) si le statut est ERROR. Variables : SONAR_TOKEN (identifiant Jenkins).
#  Sans jq ni python (absents de l'image Jenkins) : extraction par sed.
#  Après le push, le webhook SonarQube → Jenkins permettra waitForQualityGate (APRES-PUSH.md).
# =============================================================================
set -euo pipefail

RAPPORT="${1:-target/sonar/report-task.txt}"
[[ -f "$RAPPORT" ]] || { echo "Rapport Sonar introuvable : $RAPPORT"; exit 1; }
CE_URL=$(sed -n 's/^ceTaskUrl=//p' "$RAPPORT")
SERVEUR=$(sed -n 's/^serverUrl=//p' "$RAPPORT")
PROJET=$(sed -n 's/^projectKey=//p' "$RAPPORT")

valeur() { # <clé JSON> : première valeur texte trouvée
  sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p" | head -1
}

statut="" analyse=""
for _ in $(seq 60); do
  reponse=$(curl -s -u "$SONAR_TOKEN:" "$CE_URL")
  statut=$(valeur status <<<"$reponse")
  analyse=$(valeur analysisId <<<"$reponse")
  [[ "$statut" == "SUCCESS" || "$statut" == "FAILED" || "$statut" == "CANCELED" ]] && break
  sleep 5
done
echo "Traitement de l'analyse par SonarQube : $statut"
[[ "$statut" == "SUCCESS" ]] || exit 1

reponse=$(curl -s -u "$SONAR_TOKEN:" "$SERVEUR/api/qualitygates/project_status?analysisId=$analyse")
gate=$(sed -n 's/.*"projectStatus":{"status":"\([A-Z_]*\)".*/\1/p' <<<"$reponse")
echo "Quality gate « $PROJET » : $gate   ($SERVEUR/dashboard?id=$PROJET)"
echo "$reponse" | tr '{' '\n' | grep -E '"metricKey"' | sed 's/^/  /' | cut -c1-160 || true
[[ "$gate" == "OK" || "$gate" == "NONE" ]]
