#!/usr/bin/env bash
# Panne 1 — DISPONIBILITÉ : participant-service est arrêté.
# Attendu : event-service retente (lectures), puis son circuit « participant-service » passe
# OPEN (appels refusés sans attendre) ; la vue détaillée se dégrade, l'inscription est refusée (503).
# Au redémarrage : HALF_OPEN après 10 s, puis CLOSED dès que les appels d'essai réussissent.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
restaurer() { docker compose --profile core start participant-service >/dev/null 2>&1 || true; }
trap restaurer EXIT

etape "Panne 1 — disponibilité : participant-service arrêté"
prerequis
etat_circuit "$EVENT_ACTUATOR" participant-service
appel GET "/events/$EV/details"

etape "docker compose stop participant-service"
docker compose stop participant-service >/dev/null 2>&1
sleep 6   # cache du LoadBalancer (5 s) : l'instance arrêtée disparaît des listes

etape "Appels pendant la panne (lecture dégradée, puis circuit ouvert)"
for _ in 1 2 3 4 5 6; do appel GET "/events/$EV/details"; done
etat_circuit "$EVENT_ACTUATOR" participant-service
appel POST "/events/$EV/participants/$PART"

etape "Redémarrage de participant-service"
docker compose --profile core start participant-service >/dev/null 2>&1
attendre_routable /participants
info "participant-service de nouveau routable ; attente de la fin de la période OPEN (10 s)"
sleep 10
etat_circuit "$EVENT_ACTUATOR" participant-service
for _ in 1 2 3; do appel GET "/events/$EV/details"; done
etat_circuit "$EVENT_ACTUATOR" participant-service
