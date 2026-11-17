#!/usr/bin/env bash
# =============================================================================
#  Vérifications de fumée de la stack locale (make health). Code de sortie 1 si un
#  contrôle échoue. Pour chaque service métier présent dans docker-compose.yml :
#    1. enregistré UP dans Eureka ;
#    2. joignable VIA LA GATEWAY : réponse non 5xx portant l'en-tête X-Instance-Id
#       (ajouté par le service lui-même : prouve que la réponse vient bien de lui).
# =============================================================================
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
GATEWAY="${GATEWAY_URL:-http://localhost:8088}"
echecs=0

controle() {
  local libelle="$1"; shift
  if "$@"; then
    printf '  OK     %s\n' "$libelle"
  else
    printf '  ÉCHEC  %s\n' "$libelle"
    echecs=$((echecs + 1))
  fi
}
est_up()      { curl -s --max-time 5 "$1" | grep -q '"status":"UP"'; }
sert_config() { curl -s --max-time 5 "http://localhost:8888/$1/default" | grep -q '"propertySources":\[{'; }
registre()    { curl -s --max-time 5 -H 'Accept: application/json' http://localhost:8761/eureka/apps; }
apps_eureka() {
  registre | python3 -c 'import json,sys; a=json.load(sys.stdin)["applications"]["application"]; print(" ".join(sorted("%s(%d)" % (x["name"], len(x["instance"])) for x in a)))' 2>/dev/null
}
# Nombre d'instances UP d'une application dans Eureka
instances_up() {
  registre | python3 -c 'import json,sys
nom = sys.argv[1].upper()
apps = json.load(sys.stdin)["applications"]["application"]
print(sum(1 for a in apps if a["name"] == nom for i in a["instance"] if i["status"] == "UP"))' "$1" 2>/dev/null
}
enregistre()  { [[ "$(instances_up "$1")" -ge 1 ]]; }
via_gateway() {
  local entetes code
  entetes="$(curl -s --max-time 10 -o /dev/null -D - "$GATEWAY$1")"
  code="$(awk 'NR==1 {print $2}' <<<"$entetes")"
  [[ -n "$code" && "$code" -lt 500 ]] && grep -qi '^X-Instance-Id:' <<<"$entetes"
}
bases_mysql() {
  docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW DATABASES LIKE \"db\\_%\";"' 2>/dev/null | tr '\n' ' '
}

echo "Socle Spring Cloud"
controle "Config Server UP (8888)"                          est_up http://localhost:8888/actuator/health
controle "Config Server sert api-gateway.yml (config-repo)" sert_config api-gateway
controle "Eureka UP (8761)"                                 est_up http://localhost:8761/actuator/health
controle "API Gateway UP (8088)"                            est_up http://localhost:8088/actuator/health

for socle in config-server api-gateway; do
  for _ in $(seq 1 12); do enregistre "$socle" && break; sleep 5; done
  controle "$socle : UP dans Eureka"                        enregistre "$socle"
done

bases="$(bases_mysql)"
controle "MySQL : ${bases:-aucune base db_*}"               grep -q db_stats <<<"$bases"

# Services métier : nom Eureka -> chemin de lecture via la Gateway
declare -A ROUTES=(
  [participant-service]="/api/participants"
  [logistics-service]="/api/logistics"
  [event-service]="/api/events"
  [feedback-service]="/api/feedback/event/00000000-0000-0000-0000-000000000000"
  [statistics-service]="/api/stats/summary"
)
presents="$(docker compose --profile '*' config --services 2>/dev/null)"

echo "Services métier"
for service in participant-service logistics-service event-service feedback-service statistics-service; do
  if ! grep -qx "$service" <<<"$presents"; then
    printf '  —      %s : pas encore dans docker-compose.yml\n' "$service"
    continue
  fi
  # Un service s'enregistre quelques secondes après son démarrage : jusqu'à 60 s d'attente
  for _ in $(seq 1 12); do enregistre "$service" && break; sleep 5; done
  controle "$service : UP dans Eureka ($(instances_up "$service") instance(s))" enregistre "$service"
  controle "$service : répond via la Gateway (${ROUTES[$service]})"        via_gateway "${ROUTES[$service]}"
done

# Phase 5 (LoadBalancer) : si la 2e instance tourne (profil core), 2 instances UP attendues
deux_instances() { [[ "$(instances_up "$1")" -ge 2 ]]; }
for service in participant-service statistics-service; do
  if docker compose ps --services --status running 2>/dev/null | grep -qx "${service}-2"; then
    for _ in $(seq 1 12); do deux_instances "$service" && break; sleep 5; done
    controle "$service : 2 instances UP dans Eureka (répartition de charge)" deux_instances "$service"
  fi
done

# Phase 6 (observabilité) : contrôles faits seulement si les conteneurs tournent
tourne() { docker compose ps --services --status running 2>/dev/null | grep -qx "$1"; }
prometheus_pret() { curl -s localhost:9091/-/ready | grep -q Ready; }
cibles_up() { [[ "$(curl -s localhost:9091/api/v1/targets | jq '[.data.activeTargets[] | select(.health == "up")] | length')" -ge 8 ]]; }
grafana_ok() { curl -s localhost:3001/api/health | grep -q '"database": *"ok"'; }
zipkin_ok() { curl -s localhost:9411/health | grep -q UP; }
if tourne prometheus; then
  controle "Prometheus prêt (9091)" prometheus_pret
  controle "Prometheus : au moins 8 cibles UP (découvertes par Eureka)" cibles_up
fi
tourne grafana && controle "Grafana (3001)" grafana_ok
tourne zipkin && controle "Zipkin (9411)" zipkin_ok

# Phase 9 (frontend) : nginx sert Angular et transmet /api à la Gateway
front_ok() { curl -s localhost:4200/healthz | grep -q ok; }
front_api() { curl -s localhost:4200/api/stats/summary | jq -e '.nbEvents >= 0' >/dev/null; }
if tourne frontend; then
  controle "Frontend Angular (4200)" front_ok
  controle "Frontend : /api transmis à la Gateway (statistiques)" front_api
fi

echo "Registre Eureka : $(apps_eureka)"
if (( echecs == 0 )); then
  echo "Tout est opérationnel."
else
  echo "$echecs contrôle(s) en échec."
  exit 1
fi
