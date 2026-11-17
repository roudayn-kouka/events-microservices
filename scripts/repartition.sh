#!/usr/bin/env bash
# =============================================================================
#  Répartition de charge (phase 5) : 2 instances de participant-service et de statistics-service.
#  1. Gateway → services : X-Instance-Id de chaque réponse (Spring Cloud LoadBalancer, round robin)
#  2. Feign (event-service → participant-service) : requêtes reçues par chaque instance (Prometheus)
#  3. Bascule : une instance arrêtée, toutes les requêtes vont vers l'autre
#  Usage : make repartition   (ou N=40 bash scripts/repartition.sh)
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
G=http://localhost:8088/api
N="${N:-20}"

titre() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

# Compte les X-Instance-Id de N requêtes GET via la Gateway
compter() { # <chemin>
  for _ in $(seq "$N"); do
    curl -s -D - -o /dev/null "$G$1" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-instance-id" {print $2}'
  done | sort | uniq -c | awk -v n="$N" '{printf "   %-45s %3d requêtes  (%3.0f %%)\n", $2, $1, 100*$1/n}'
}

# Requêtes reçues par une instance de participant-service (compteur Prometheus de Spring MVC)
recues() { # <port>
  curl -s "localhost:$1/actuator/prometheus" \
    | awk '/^http_server_requests_seconds_count/ && /uri="\/participants"/ {s+=$2} END {print s+0}'
}

for c in participant-service-2 statistics-service-2; do
  docker compose ps --services --status running | grep -qx "$c" \
    || { echo "$c n'est pas démarré : make up (profil core)"; exit 1; }
done

titre "1. Gateway → participant-service (lb://participant-service), $N requêtes"
compter /participants
titre "1. Gateway → statistics-service (lb://statistics-service), $N requêtes"
compter /stats/summary

titre "2. Feign : event-service → participant-service, $N vues détaillées"
EV=$(curl -s "$G/events" | jq -r '[.[] | select((.participantIds | length) > 0)][0].idEvent // empty')
[[ -n "$EV" ]] || { echo "   aucune donnée : make seed"; exit 1; }
a1=$(recues 8102); a2=$(recues 8112)
for _ in $(seq "$N"); do curl -s -o /dev/null "$G/events/$EV/details"; done
d1=$(( $(recues 8102) - a1 )); d2=$(( $(recues 8112) - a2 ))
printf '   participant-service-1 (8102) : %3d requêtes reçues\n   participant-service-2 (8112) : %3d requêtes reçues\n' "$d1" "$d2"

titre "3. Bascule : participant-service-2 arrêtée"
docker compose stop participant-service-2 >/dev/null 2>&1
trap 'docker compose --profile core start participant-service-2 >/dev/null 2>&1 || true' EXIT
sleep 7   # cache du LoadBalancer (5 s) : l'instance arrêtée sort de la liste
compter /participants
docker compose --profile core start participant-service-2 >/dev/null 2>&1
trap - EXIT
echo "   participant-service-2 redémarrée : elle reprend sa part dès son enregistrement dans Eureka (environ 10 s)"
