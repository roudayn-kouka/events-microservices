#!/usr/bin/env bash
# Panne 5 — DÉPENDANCE EXTERNE : la base de participant-service devient inaccessible (compte MySQL
# participant_user verrouillé, connexions coupées). Les autres bases ne sont pas touchées.
# Attendu : participant-service health DOWN (composant db) ; ses réponses sont des erreurs →
# le circuit « participant-service » d'event-service s'OUVRE ; retour à la normale au déverrouillage.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
deverrouiller() {
  printf "ALTER USER 'participant_user'@'%%' ACCOUNT UNLOCK;\n" | mysql_root >/dev/null || true
}
trap deverrouiller EXIT

etape "Panne 5 — dépendance externe : MySQL inaccessible pour participant-service"
prerequis
info "santé de participant-service : $(curl -s "$PARTICIPANT_ACTUATOR/health" | jq -c '{status, db: .components.db.status}')"

etape "Compte participant_user verrouillé et connexions ouvertes coupées"
printf "ALTER USER 'participant_user'@'%%' ACCOUNT LOCK;\n" | mysql_root
for id in $(printf "SELECT id FROM information_schema.processlist WHERE user='participant_user';\n" | mysql_root); do
  printf 'KILL %s;\n' "$id" | mysql_root || true
done
sleep 2
info "santé de participant-service : $(curl -s "$PARTICIPANT_ACTUATOR/health" | jq -c '{status, db: .components.db.status}')"

etape "Appels via event-service : erreurs de participant-service, puis circuit ouvert"
for _ in 1 2 3 4; do appel GET "/events/$EV/details"; done
etat_circuit "$EVENT_ACTUATOR" participant-service
appel POST "/events/$EV/participants/$PART"

etape "Base de nouveau accessible"
deverrouiller
trap - EXIT
sleep 12   # reconnexion du pool + fin de la période OPEN
info "santé de participant-service : $(curl -s "$PARTICIPANT_ACTUATOR/health" | jq -c '{status, db: .components.db.status}')"
for _ in 1 2 3; do appel GET "/events/$EV/details"; done
etat_circuit "$EVENT_ACTUATOR" participant-service
