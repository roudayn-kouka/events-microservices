#!/usr/bin/env bash
# Vérifications de fumée de la stack locale. Code de sortie 1 si un contrôle échoue.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
echecs=0

# controle <libellé> <commande…> : affiche OK ou ÉCHEC selon le code de retour de la commande
controle() {
  local libelle="$1"; shift
  if "$@"; then
    printf '  OK     %s\n' "$libelle"
  else
    printf '  ÉCHEC  %s\n' "$libelle"
    echecs=$((echecs + 1))
  fi
}
est_up()       { curl -s --max-time 5 "$1" | grep -q '"status":"UP"'; }
sert_config()  { curl -s --max-time 5 "http://localhost:8888/$1/default" | grep -q '"propertySources":\[{'; }
apps_eureka()  {
  curl -s --max-time 5 -H 'Accept: application/json' http://localhost:8761/eureka/apps \
    | python3 -c 'import json,sys; a=json.load(sys.stdin)["applications"]["application"]; print(" ".join(sorted("%s(%d)" % (x["name"], len(x["instance"])) for x in a)))' 2>/dev/null
}
bases_mysql()  {
  docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW DATABASES LIKE \"db\\_%\";"' 2>/dev/null | tr '\n' ' '
}

echo "Socle Spring Cloud"
controle "Config Server UP (8888)"                     est_up http://localhost:8888/actuator/health
controle "Config Server sert api-gateway.yml (config-repo)" sert_config api-gateway
controle "Eureka UP (8761)"                            est_up http://localhost:8761/actuator/health
controle "API Gateway UP (8088)"                       est_up http://localhost:8088/actuator/health

# Un service s'enregistre quelques secondes après son démarrage : on patiente jusqu'à 60 s
for _ in $(seq 1 12); do
  apps="$(apps_eureka)"
  [[ "$apps" == *API-GATEWAY* ]] && break
  sleep 5
done
controle "Eureka : ${apps:-registre vide}"             test -n "$apps"

bases="$(bases_mysql)"
controle "MySQL : ${bases:-aucune base db_*}"          grep -q db_stats <<<"$bases"

if (( echecs == 0 )); then
  echo "Tout est opérationnel."
else
  echo "$echecs contrôle(s) en échec."
  exit 1
fi
