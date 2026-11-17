#!/usr/bin/env bash
# =============================================================================
#  Les 5 scénarios de panne vérifiés par Newman (phase 7) : le script crée chaque panne, lance le
#  dossier Postman correspondant (postman/events-pannes.postman_collection.json), puis rétablit et
#  attend que le circuit soit refermé avant la panne suivante. Usage : make newman-pannes
# =============================================================================
source "$(dirname "${BASH_SOURCE[0]}")/pannes/lib.sh"
prerequis
INSTANCES=$(conteneurs_de participant-service | tr '\n' ' ')
echecs=0

# Exécute le dossier Postman une fois et compte les échecs
dossier() { # <nom du dossier>
  etape "Newman : $1"
  local sortie
  sortie=$(docker run --rm --network host -v "$RACINE/postman:/etc/newman" postman/newman:6-alpine \
      run events-pannes.postman_collection.json -e local.postman_environment.json --folder "$1" \
      --env-var "ev=$EV" --env-var "part=$PART" --env-var "participantActuator=$PARTICIPANT_ACTUATOR" 2>&1) \
    || echecs=$((echecs + 1))
  grep -E '✓|✗|AssertionError|requests|assertions' <<<"$sortie" || true
  # en cas d'échec : réponse reçue (code, temps, début du corps) pour le diagnostic
  if grep -q AssertionError <<<"$sortie"; then
    grep -E '^\s+(POST|GET) .*\[[0-9]{3}' <<<"$sortie" | sed 's/^/   réponse : /' || true
  fi
}

# Entre deux scénarios : circuit CLOSED ET fenêtre glissante sans échec ni appel lent.
# Constaté : après la panne 2, le circuit restait CLOSED avec 3 échecs sur 4 appels (moins que le
# minimum de 5) ; le premier échec de la panne 3 l'ouvrait aussitôt (503 au lieu de 502). Des appels
# réussis repoussent les anciens échecs hors de la fenêtre (10 appels).
circuit_ferme() {
  local cb etat
  for _ in $(seq 60); do
    curl -s -o /dev/null "$G/events/$EV/details"
    cb=$(curl -s "$EVENT_ACTUATOR/circuitbreakers" | jq -c '.circuitBreakers["participant-service"] // {}')
    etat=$(jq -r '.state // "CLOSED"' <<<"$cb")
    if [[ "$etat" == "CLOSED" && "$(jq -r '(.failedCalls // 0) + (.slowCalls // 0)' <<<"$cb")" == "0" ]]; then
      info "circuit « participant-service » refermé, fenêtre sans échec"
      return 0
    fi
    sleep 1
  done
  info "circuit pas revenu à un état propre : $cb"
  echecs=$((echecs + 1))
}

deverrouiller() { printf "ALTER USER 'participant_user'@'%%' ACCOUNT UNLOCK;\n" | mysql_root >/dev/null || true; }
nettoyer() {
  # shellcheck disable=SC2086
  docker compose --profile core start $INSTANCES >/dev/null 2>&1 || true
  deverrouiller
  for s in participant-service event-service; do
    [[ -f "config-overrides/$s.yml" ]] && rm -f "config-overrides/$s.yml"
  done
  return 0
}
trap nettoyer EXIT

circuit_ferme

etape "Panne 1 : arrêt de $INSTANCES"
# shellcheck disable=SC2086
docker compose stop $INSTANCES >/dev/null 2>&1
sleep 7
dossier "Panne 1 — disponibilité (participant-service arrêté)"
# shellcheck disable=SC2086
docker compose --profile core start $INSTANCES >/dev/null 2>&1
attendre_routable /participants
circuit_ferme

etape "Panne 2 : latence de 7 s (à chaud)"
surcharger participant-service "panne:
  latence-ms: 7000"
dossier "Panne 2 — temps de réponse > 5 s"
retirer_surcharge participant-service "panne:
  latence-ms: 0"
circuit_ferme

etape "Panne 3 : réponse corrompue (à chaud)"
surcharger participant-service "panne:
  reponse-corrompue: true"
dossier "Panne 3 — réponse incorrecte"
retirer_surcharge participant-service "panne:
  reponse-corrompue: false"
circuit_ferme

etape "Panne 4 : URL mal configurée (à chaud)"
surcharger event-service "events:
  clients:
    participant-service:
      url-forcee: http://participant-introuvable:8102"
dossier "Panne 4 — erreur réseau (URL mal configurée)"
retirer_surcharge event-service "events:
  clients:
    participant-service:
      url-forcee: ''"
circuit_ferme

etape "Panne 5 : base de participant-service inaccessible"
printf "ALTER USER 'participant_user'@'%%' ACCOUNT LOCK;\n" | mysql_root
for id in $(printf "SELECT id FROM information_schema.processlist WHERE user='participant_user';\n" | mysql_root); do
  printf 'KILL %s;\n' "$id" | mysql_root || true
done
sleep 2
dossier "Panne 5 — dépendance externe (base inaccessible)"
deverrouiller
sleep 5
circuit_ferme

etape "Bilan"
if (( echecs == 0 )); then
  info "les 5 scénarios de panne sont vérifiés par Newman, et tout est rétabli"
else
  info "$echecs échec(s)"
  exit 1
fi
