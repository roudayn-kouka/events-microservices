#!/usr/bin/env bash
# Panne 4 — ERREUR RÉSEAU : event-service est configuré, À CHAUD, avec une mauvaise adresse pour
# participant-service (hôte inexistant), puis corrigé à chaud (/actuator/refresh), sans redémarrage.
# Attendu : vraie erreur réseau (hôte inconnu) → Retry 3 fois → fallback « injoignable ».
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
restaurer() { [[ -f config-overrides/event-service.yml ]] && retirer_surcharge event-service "$EVENT_ACTUATOR" || true; }
trap restaurer EXIT

etape "Panne 4 — erreur réseau : URL de participant-service mal configurée dans event-service"
prerequis
appel GET "/events/$EV/details"

etape "Mauvaise URL posée à chaud"
surcharger event-service "$EVENT_ACTUATOR" "events:
  clients:
    participant-service:
      url-forcee: http://participant-introuvable:8102"

etape "Appels avec la mauvaise URL"
appel POST "/events/$EV/participants/$PART"
appel GET "/events/$EV/details"
etat_circuit "$EVENT_ACTUATOR" participant-service

etape "Correction à chaud : la surcharge est retirée, puis /actuator/refresh"
retirer_surcharge event-service "$EVENT_ACTUATOR"
trap - EXIT
sleep 10
appel GET "/events/$EV/details"
etat_circuit "$EVENT_ACTUATOR" participant-service
