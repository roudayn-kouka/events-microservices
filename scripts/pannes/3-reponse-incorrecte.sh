#!/usr/bin/env bash
# Panne 3 — RÉPONSE INCORRECTE : participant-service renvoie un JSON tronqué (HTTP 200), À CHAUD.
# Attendu : event-service ne peut pas lire la réponse (DecodeException) → la rejette → fallback
# 502 « réponse incorrecte, rejetée » ; aucune donnée invalide n'est enregistrée.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
restaurer() { [[ -f config-overrides/participant-service.yml ]] && retirer_surcharge participant-service "panne:
  reponse-corrompue: false" || true; }
trap restaurer EXIT

etape "Panne 3 — réponse incorrecte : participant-service renvoie un JSON corrompu"
prerequis
surcharger participant-service "panne:
  reponse-corrompue: true"

etape "Ce que renvoie participant-service (HTTP 200, mais illisible)"
info "$(curl -s -w '  -> HTTP %{http_code}' "$G/participants/$PART")"
curl -s "$G/participants/$PART" | jq . >/dev/null 2>&1 || info "jq : JSON invalide"

etape "event-service rejette la réponse et déclenche le fallback"
appel POST "/events/$EV/participants/$PART"
appel GET "/events/$EV/details"
etat_circuit "$EVENT_ACTUATOR" participant-service

etape "Retour à la normale, à chaud"
retirer_surcharge participant-service "panne:
  reponse-corrompue: false"
trap - EXIT
sleep 10
for _ in 1 2 3; do appel POST "/events/$EV/participants/$PART"; done   # 3 appels d'essai : HALF_OPEN → CLOSED
etat_circuit "$EVENT_ACTUATOR" participant-service
