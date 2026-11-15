#!/usr/bin/env bash
# =============================================================================
#  Jeu de données de démonstration, créé par les API via la Gateway (EVT-42).
#  Remplace le DemoDataLoader du monolithe : avec une base par service, aucun
#  programme ne peut écrire dans les 5 bases d'un coup ; on passe donc par les
#  API, ce qui exerce aussi Feign (vérifications d'existence, calculCout).
#
#  Usage : make seed            (ou bash scripts/seed-demo.sh)
#          FORCE=1 make seed    recrée un jeu même si des données [Démo] existent
#          GATEWAY=http://IP:8088 make seed   (instance AWS)
#  Dates relatives au jour du lancement : les événements « terminés » le restent
#  le jour de la présentation (les avis sont acceptés), les autres sont à venir.
# =============================================================================
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8088}"
API="$GATEWAY/api"
JSON='Content-Type: application/json'
PREFIXE='[Démo]'

command -v jq >/dev/null || { echo "jq est requis (sudo apt install jq)"; exit 1; }

# POST/PUT JSON ; échoue avec le message de l'API si le code HTTP n'est pas 2xx
appel() {
  local methode=$1 chemin=$2 corps=${3:-}
  local sortie code
  sortie=$(mktemp)
  if [[ -n "$corps" ]]; then
    code=$(curl -s -o "$sortie" -w '%{http_code}' -X "$methode" "$API$chemin" -H "$JSON" -d "$corps")
  else
    code=$(curl -s -o "$sortie" -w '%{http_code}' -X "$methode" "$API$chemin")
  fi
  if [[ "$code" != 2* ]]; then
    echo "ÉCHEC $methode $chemin -> HTTP $code : $(jq -r '.message? // .' "$sortie" 2>/dev/null || cat "$sortie")" >&2
    rm -f "$sortie"
    exit 1
  fi
  cat "$sortie"
  rm -f "$sortie"
}

jour() { date -d "$1 days" +%F; }

# ------------------------------------------------------------------ garde-fou
curl -sf "$API/events" >/dev/null || { echo "La Gateway ne répond pas sur $GATEWAY (lancer make up)"; exit 1; }
deja=$(curl -s "$API/events" | jq --arg p "$PREFIXE" '[.[] | select(.description | startswith($p))] | length')
if [[ "$deja" -gt 0 && "${FORCE:-0}" != 1 ]]; then
  echo "$deja événement(s) $PREFIXE déjà présents : rien à faire (FORCE=1 pour recréer un jeu)."
  exit 0
fi

# ------------------------------------------------------------------ participants
participant() { # nom prénom tâche -> idPart (réutilise un participant identique)
  local id
  id=$(curl -s -G "$API/participants/search" --data-urlencode "nom=$1" --data-urlencode "prenom=$2" \
        --data-urlencode "tache=$3" | jq '.[0].idPart // empty')
  if [[ -z "$id" ]]; then
    id=$(appel POST /participants "$(jq -nc --arg n "$1" --arg p "$2" --arg t "$3" '{nom:$n, prenom:$p, tache:$t}')" | jq '.idPart')
  fi
  echo "$id"
}

echo "Participants (participant-service)"
TOUNSI=$(participant Tounsi Ahmed ORGANISATEUR)   # l'organisateur de calculCout
AMIRA=$(participant "Ben Salah" Amira INVITE)
SARRA=$(participant Jaziri Sarra INVITE)
YOUSSEF=$(participant Trabelsi Youssef ANIMATEUR)
KARIM=$(participant Mansouri Karim ANIMATEUR)
NOUR=$(participant Gharbi Nour SERVEUR)
echo "  Tounsi=$TOUNSI Amira=$AMIRA Sarra=$SARRA Youssef=$YOUSSEF Karim=$KARIM Nour=$NOUR"

# ------------------------------------------------------------------ événements + logistique
evenement() { # description début fin ids... -> idEvent
  local description=$1 debut=$2 fin=$3
  shift 3
  appel POST /events "$(jq -nc --arg d "$PREFIXE $description" --arg a "$debut" --arg b "$fin" \
      --argjson ids "$(printf '%s\n' "$@" | jq -s .)" '{description:$d, dateDebut:$a, dateFin:$b, participantIds:$ids}')" \
    | jq -r '.idEvent'
}

logistique() { # idEvent description réservée prix quantité
  appel POST "/events/$1/logistics" "$(jq -nc --arg d "$2" --argjson r "$3" --argjson p "$4" --argjson q "$5" \
      '{description:$d, reserve:$r, prixUnit:$p, quantite:$q}')" >/dev/null
}

echo "Événements (event-service) et logistique (logistics-service, via Feign)"
JPO=$(evenement "Journée Portes Ouvertes FST" "$(jour -30)" "$(jour -29)" "$TOUNSI" "$AMIRA" "$SARRA" "$YOUSSEF")
logistique "$JPO" Chaises true 2.5 200
logistique "$JPO" Stands true 120 6
logistique "$JPO" Goodies false 4 100

HACK=$(evenement "Hackathon IA" "$(jour -12)" "$(jour -10)" "$TOUNSI" "$KARIM" "$NOUR")
logistique "$HACK" "Salle machines" true 300 2
logistique "$HACK" Pizzas true 8 60

FORUM=$(evenement "Forum Entreprises" "$(jour 25)" "$(jour 26)" "$TOUNSI" "$AMIRA" "$NOUR")
logistique "$FORUM" Sonorisation true 250 1
logistique "$FORUM" Badges false 1 300

CONF=$(evenement "Conférence Cloud et DevOps" "$(jour 60)" "$(jour 60)" "$YOUSSEF" "$SARRA")
logistique "$CONF" Projecteur true 50 2
echo "  4 événements, 8 logistiques"

# ------------------------------------------------------------------ avis (événements terminés)
avis() { # idEvent idPart note commentaire
  appel POST "/feedback/$1/$2" "$(jq -nc --argjson n "$3" --arg c "$4" '{note:$n, commentaire:$c}')" >/dev/null
}

echo "Avis (feedback-service, vérifiés par Feign)"
avis "$JPO" "$AMIRA" 5 "Très bien organisée"
avis "$JPO" "$SARRA" 4 "Stands variés"
avis "$JPO" "$YOUSSEF" 4 "Bonne affluence"
avis "$HACK" "$KARIM" 5 "Sujets passionnants"
avis "$HACK" "$NOUR" 3 "Salle trop petite"
echo "  5 avis"

# ------------------------------------------------------------------ coût et résumé
echo "calculCout (event-service → participant-service + logistics-service)"
appel POST /events/cout/recalcul | jq -r --arg p "$PREFIXE" \
  '.[] | select(.description | startswith($p)) | "  \(.description) : \(.cout) DT"'

echo "Résumé (statistics-service)"
curl -s "$API/stats/summary" | jq -c '{nbEvents, nbEventsAVenir, nbParticipants, tauxReservation, coutTotalLogistiqueReservee, nbFeedbacks, noteMoyenne}'
