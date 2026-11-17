#!/usr/bin/env bash
# =============================================================================
#  Replay de la projection CQRS de statistics-service (mon module), avec 2 instances.
#  Les deux instances partagent le jeton du processeur « statistiques » (token_entry, db_stats) :
#  une seule détient le segment à un instant donné (THEORIE Q25). Le plus simple à démontrer :
#  1. arrêter statistics-service-2 (son arrêt propre libère son segment) ;
#  2. rejouer sur l'instance 1 directement (port 8105, sans la Gateway) ;
#  3. attendre la reconstruction (compteurs de chaque table, avant / après) ;
#  4. redémarrer statistics-service-2 et vérifier les 2 instances UP dans Eureka.
#  Usage : make replay
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
INSTANCE1=http://localhost:8105/stats/admin
compteurs() { curl -s "$INSTANCE1/projection" | jq -c '{evenements, participants, logistiques, avis}'; }
instances_up() {
  curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps/STATISTICS-SERVICE \
    | jq '[.application.instance[] | select(.status == "UP")] | length' 2>/dev/null || echo 0
}

avant=$(compteurs)
echo "compteurs avant le replay : $avant"

deux=false
if docker compose ps --services --status running | grep -qx statistics-service-2; then
  deux=true
  echo "1. arrêt de statistics-service-2"
  docker compose stop statistics-service-2 >/dev/null 2>&1
fi

echo "2. replay sur l'instance 1 (8105) : $(curl -s -X POST "$INSTANCE1/replay" | jq -c '{processeur, enCours}')"

echo "3. reconstruction de db_stats depuis l'event store"
for i in $(seq 60); do
  sleep 1
  maintenant=$(compteurs)
  replay=$(curl -s "$INSTANCE1/projection" | jq -r .replay)
  if [[ "$maintenant" == "$avant" && "$replay" == "false" ]]; then
    echo "   reconstruite en ${i} s : $maintenant (identique à avant)"
    break
  fi
  [[ $i -eq 60 ]] && { echo "   pas reconstruite en 60 s : $maintenant (attendu $avant)"; exit 1; }
done

if $deux; then
  echo "4. redémarrage de statistics-service-2"
  docker compose --profile core start statistics-service-2 >/dev/null 2>&1
  for _ in $(seq 60); do [[ "$(instances_up)" -ge 2 ]] && break; sleep 2; done
  echo "   instances de statistics-service UP dans Eureka : $(instances_up)"
fi
