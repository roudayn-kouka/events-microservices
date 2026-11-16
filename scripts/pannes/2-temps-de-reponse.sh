#!/usr/bin/env bash
# Panne 2 — TEMPS DE RÉPONSE > 5 s : latence de 7 s injectée dans participant-service, À CHAUD
# (config-overrides/participant-service.yml + /actuator/refresh, sans redémarrage).
# Attendu : le TimeLimiter (5 s) d'event-service abandonne l'appel → fallback 504 explicite.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
restaurer() { [[ -f config-overrides/participant-service.yml ]] && retirer_surcharge participant-service "$PARTICIPANT_ACTUATOR" || true; }
trap restaurer EXIT

etape "Panne 2 — temps de réponse : participant-service répond en 7 s"
prerequis
appel POST "/events/$EV/participants/$PART"

etape "Latence activée à chaud par le Config Server"
surcharger participant-service "$PARTICIPANT_ACTUATOR" "panne:
  latence-ms: 7000"

etape "Écriture (pas de Retry) : abandon au bout de 5 s"
appel POST "/events/$EV/participants/$PART"
etape "Lecture (Retry 3 fois) : vue détaillée dégradée après 3 × 5 s"
appel GET "/events/$EV/details"
etat_circuit "$EVENT_ACTUATOR" participant-service

etape "Retour à la normale, à chaud"
retirer_surcharge participant-service "$PARTICIPANT_ACTUATOR"
trap - EXIT
sleep 10   # si le circuit s'est ouvert (appels lents), fin de la période OPEN
appel POST "/events/$EV/participants/$PART"
etat_circuit "$EVENT_ACTUATOR" participant-service
