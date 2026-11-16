#!/usr/bin/env bash
# Panne 2 — TEMPS DE RÉPONSE > 5 s : latence de 7 s injectée dans participant-service, À CHAUD
# (config-overrides/participant-service.yml + /actuator/refresh, sans redémarrage).
# Attendu : le TimeLimiter (5 s) d'event-service abandonne l'appel → fallback 504 explicite.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
restaurer() { [[ -f config-overrides/participant-service.yml ]] && retirer_surcharge participant-service "$PARTICIPANT_ACTUATOR" "panne:
  latence-ms: 0" || true; }
trap restaurer EXIT

etape "Panne 2 — temps de réponse : participant-service répond en 7 s"
prerequis
appel POST "/events/$EV/participants/$PART"

etape "Latence activée à chaud par le Config Server"
surcharger participant-service "$PARTICIPANT_ACTUATOR" "panne:
  latence-ms: 7000"

etape "Inscription : sa vérification (GET) dépasse 5 s à chaque tentative → 504"
appel POST "/events/$EV/participants/$PART"
etape "Lecture suivante : le circuit, ouvert par les appels trop lents, répond sans attendre"
appel GET "/events/$EV/details"
etat_circuit "$EVENT_ACTUATOR" participant-service

etape "Retour à la normale, à chaud"
retirer_surcharge participant-service "$PARTICIPANT_ACTUATOR" "panne:
  latence-ms: 0"
trap - EXIT
sleep 10   # si le circuit s'est ouvert (appels lents), fin de la période OPEN
for _ in 1 2 3; do appel POST "/events/$EV/participants/$PART"; done   # 3 appels d'essai : HALF_OPEN → CLOSED
etat_circuit "$EVENT_ACTUATOR" participant-service
