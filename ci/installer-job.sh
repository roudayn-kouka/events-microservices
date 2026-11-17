#!/usr/bin/env bash
# =============================================================================
#  Installe le job « events-microservices » dans le Jenkins de ~/devops (phase 11), sans mot de
#  passe administrateur : la définition du job (ci/jenkins/config.xml) est copiée dans
#  /var/jenkins_home/jobs/events-microservices/, puis Jenkins est redémarré pour la charger
#  (docker restart : le conteneur et ses volumes sont conservés).
#  Garde-fous : seul le dossier de CE job est écrit ; aucun autre job, conteneur ou volume n'est
#  modifié ni supprimé. Le premier build est lancé par la scrutation (pollSCM, 2 minutes).
# =============================================================================
set -euo pipefail
RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JOB=events-microservices
# Branche construite : develop par défaut (BRANCHE=feature/ci pour tester une branche de travail)
BRANCHE="${BRANCHE:-develop}"

docker ps --format '{{.Names}}' | grep -qx jenkins || { echo "Le conteneur jenkins doit tourner : docker start jenkins"; exit 1; }

echo "Jobs existants (non modifiés) : $(docker exec jenkins ls /var/jenkins_home/jobs | tr '\n' ' ')"
docker exec jenkins mkdir -p "/var/jenkins_home/jobs/$JOB"
tmp=$(mktemp)
sed "s#<name>\*/develop</name>#<name>*/$BRANCHE</name>#" "$RACINE/ci/jenkins/config.xml" > "$tmp"
docker cp "$tmp" "jenkins:/var/jenkins_home/jobs/$JOB/config.xml"
rm -f "$tmp"
echo "Définition copiée : /var/jenkins_home/jobs/$JOB/config.xml (branche $BRANCHE)"

docker restart jenkins >/dev/null
for _ in $(seq 60); do
  [[ "$(curl -s -o /dev/null -w '%{http_code}' localhost:8080/login)" == "200" ]] && break
  sleep 3
done
echo "Jenkins redémarré : http://localhost:8080/job/$JOB/ (premier build par scrutation sous 2 minutes)"
