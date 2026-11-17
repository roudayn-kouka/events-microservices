#!/usr/bin/env bash
# =============================================================================
#  Revue versionnée des Security Hotspots (pipeline Jenkins, après l'analyse Sonar).
#  Pour chaque hotspot « TO_REVIEW » dont la règle et le fichier figurent dans
#  ci/sonar-hotspots-revus.txt : POST api/hotspots/change_status → REVIEWED / SAFE, avec la
#  justification en commentaire. Les autres restent à revoir, et sont listés dans le journal.
#  Variables : SONAR_TOKEN (identifiant Jenkins), SERVEUR, PROJET. Sans jq (absent de Jenkins).
# =============================================================================
set -euo pipefail
REVUES="${REVUES:-ci/sonar-hotspots-revus.txt}"
: "${SONAR_TOKEN:?}" "${SERVEUR:?}" "${PROJET:?}"

champ() { # <clé> : valeur texte d'un champ JSON sur la ligne lue
  sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p"
}

reponse=$(curl -s -u "$SONAR_TOKEN:" "$SERVEUR/api/hotspots/search?projectKey=$PROJET&status=TO_REVIEW&ps=500")
a_revoir=0
marques=0
# un hotspot par ligne : on coupe avant chaque {"key":
while IFS= read -r hotspot; do
  [[ "$hotspot" == *'"key":'* ]] || continue
  cle=$(champ key <<<"$hotspot" | head -1)
  regle=$(champ ruleKey <<<"$hotspot")
  [[ -n "$regle" ]] || continue   # objets « components » de la réponse : pas des hotspots
  composant=$(champ component <<<"$hotspot")
  fichier="${composant#"$PROJET":}"
  justification=$(grep -v '^#' "$REVUES" | awk -F'|' -v r="$regle" -v f="$fichier" '$1 == r && $2 == f { print $3; exit }')
  if [[ -z "$justification" ]]; then
    echo "  À REVOIR : $regle $fichier ($cle)"
    a_revoir=$((a_revoir + 1))
    continue
  fi
  code=$(curl -s -o /dev/null -w '%{http_code}' -u "$SONAR_TOKEN:" -X POST "$SERVEUR/api/hotspots/change_status" \
    --data-urlencode "hotspot=$cle" --data-urlencode "status=REVIEWED" --data-urlencode "resolution=SAFE" \
    --data-urlencode "comment=Revue versionnée (ci/sonar-hotspots-revus.txt) : $justification")
  if [[ "$code" == "204" ]]; then
    echo "  SAFE : $regle $fichier ($cle)"
    marques=$((marques + 1))
  else
    echo "  ÉCHEC HTTP $code pour $cle : le jeton doit avoir « Administer Security Hotspots » sur le projet"
    a_revoir=$((a_revoir + 1))
  fi
done <<<"${reponse//'{"key":'/$'\n''{"key":'}"
echo "Security Hotspots : $marques marqué(s) Safe, $a_revoir restant(s) à revoir"
