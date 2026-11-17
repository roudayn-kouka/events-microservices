#!/usr/bin/env bash
# =============================================================================
#  Outils communs aux 5 scripts de panne (phase 4). Sourcé, jamais exécuté seul.
#  Les pannes 2, 3 et 4 sont posées « à chaud » : un fichier config-overrides/<service>.yml
#  (prioritaire dans le Config Server), puis POST /actuator/refresh sur le service, sans redémarrage.
# =============================================================================
set -euo pipefail

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$RACINE"
G="http://localhost:8088/api"
J='Content-Type: application/json'
# ports directs des services (actuator) : event 8101, participant 8102
# shellcheck disable=SC2034  # utilisées par les scripts qui sourcent ce fichier
EVENT_ACTUATOR="http://localhost:8101/actuator"
# shellcheck disable=SC2034
PARTICIPANT_ACTUATOR="http://localhost:8102/actuator"

etape() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
info() { printf '   %s\n' "$*"; }

# État d'un circuit dans le service APPELANT (actuator/circuitbreakers)
etat_circuit() { # <url actuator> <nom du circuit>
  curl -s "$1/circuitbreakers" | jq -r --arg n "$2" \
    '.circuitBreakers[$n] // {state: "pas encore créé"} | "circuit « \($n) » : \(.state)  (échecs \(.failureRate // "-"), appels refusés \(.notPermittedCalls // 0))"'
}

# Appel via la Gateway : code HTTP, durée, puis le message (ou l'avertissement de la vue dégradée)
appel() { # <méthode> <chemin> [corps]
  local sortie code duree
  sortie=$(mktemp)
  if [[ $# -ge 3 ]]; then
    read -r code duree < <(curl -s -o "$sortie" -w '%{http_code} %{time_total}
' -X "$1" "$G$2" -H "$J" -d "$3")
  else
    read -r code duree < <(curl -s -o "$sortie" -w '%{http_code} %{time_total}
' -X "$1" "$G$2")
  fi
  printf '   %-6s %-55s -> HTTP %s en %5.2f s  %s\n' "$1" "$2" "$code" "$duree" \
    "$(jq -r 'if type=="object" then (.message // (.avertissements // [] | join(" ; "))) else "" end' "$sortie" 2>/dev/null | cut -c1-110)"
  rm -f "$sortie"
}

# Conteneurs en cours d'exécution d'un service (phase 5 : participant-service et sa 2e instance)
conteneurs_de() { # <service>
  docker compose ps --services --status running | grep -E "^$1(-2)?$" || true
}

# Ports actuator de toutes les instances en cours d'exécution d'un service
actuators_de() { # <service>
  case "$1" in
    participant-service)
      echo "$PARTICIPANT_ACTUATOR"
      if conteneurs_de participant-service | grep -qx participant-service-2; then echo "http://localhost:8112/actuator"; fi
      ;;
    event-service) echo "$EVENT_ACTUATOR" ;;
  esac
}

# Surcharge à chaud : écrit config-overrides/<service>.yml puis rafraîchit TOUTES ses instances
surcharger() { # <service> <contenu YAML>
  printf '%s\n' "$2" > "config-overrides/$1.yml"
  info "config-overrides/$1.yml : $(tr '\n' ' ' < "config-overrides/$1.yml")"
  for url in $(actuators_de "$1"); do rafraichir "$url"; done
}

# Retour à la normale : écrire d'abord une valeur NEUTRE puis rafraîchir, et seulement ensuite
# supprimer le fichier. Constaté en phase 4 : si on supprime directement le fichier, le refresh
# du client (spring.config.import) répond [] et garde l'ancienne source de propriétés jusqu'au
# prochain redémarrage — la panne resterait active.
retirer_surcharge() { # <service> <contenu YAML neutre>
  printf '%s\n' "$2" > "config-overrides/$1.yml"
  info "valeur neutre : $(tr '\n' ' ' < "config-overrides/$1.yml")"
  for url in $(actuators_de "$1"); do rafraichir "$url"; done
  rm -f "config-overrides/$1.yml"
  info "config-overrides/$1.yml retiré"
}

rafraichir() { # <url actuator>
  info "POST $1/refresh -> $(curl -s -X POST "$1/refresh" | jq -c .)"
}

# Attend qu'un service soit routable via la Gateway (Eureka + cache du LoadBalancer : quelques secondes)
attendre_routable() { # <chemin via la Gateway>
  for _ in $(seq 60); do
    [[ "$(curl -s -o /dev/null -w '%{http_code}' "$G$1")" =~ ^2 ]] && return 0
    sleep 1
  done
  echo "   $1 toujours injoignable après 60 s" >&2
  return 1
}

# Un événement [Démo] qui a des participants (créé par make seed)
evenement_demo() {
  curl -s "$G/events" | jq -r '[.[] | select((.participantIds | length) > 0)][0].idEvent // empty'
}

participant_demo() {
  curl -s "$G/participants" | jq -r '.[0].idPart // empty'
}

mysql_root() { # SQL sur l'entrée standard
  docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N 2>/dev/null'
}

prerequis() {
  command -v jq >/dev/null || { echo "jq est requis"; exit 1; }
  EV=$(evenement_demo)
  PART=$(participant_demo)
  [[ -n "$EV" && -n "$PART" ]] || { echo "Aucune donnée : lancer make seed"; exit 1; }
  info "événement de démonstration : $EV ; participant : $PART"
}
