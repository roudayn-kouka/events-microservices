#!/usr/bin/env bash
# =============================================================================
#  Sert le dépôt LOCAL au Jenkins de ~/devops, en LECTURE SEULE, sans push ni GitHub (phase 11).
#  Conteneur « events-git » : git daemon (protocole git://, lecture seule par nature), dépôt monté
#  en :ro, sur le réseau devops_default (Jenkins le joint par git://events-git/events-microservices)
#  et publié sur 9418 pour un test depuis WSL. Le conteneur Jenkins n'est ni modifié ni recréé.
#  Usage : ci/git-daemon.sh [start|stop|status]
# =============================================================================
set -euo pipefail
RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NOM=events-git
RESEAU=devops_default

case "${1:-start}" in
  start)
    if docker ps --format '{{.Names}}' | grep -qx "$NOM"; then
      echo "$NOM tourne déjà"
    elif docker ps -a --format '{{.Names}}' | grep -qx "$NOM"; then
      docker start "$NOM" >/dev/null && echo "$NOM redémarré"
    else
      docker run -d --name "$NOM" --restart unless-stopped --network "$RESEAU" -p 9418:9418 \
        --user "$(id -u):$(id -g)" -v "$RACINE:/git/events-microservices:ro" \
        alpine/git:v2.49.1 daemon --reuseaddr --export-all --base-path=/git --informative-errors /git >/dev/null
      echo "$NOM démarré : git://events-git/events-microservices (réseau $RESEAU), git://localhost:9418/events-microservices"
    fi
    ;;
  stop) docker stop "$NOM" >/dev/null && echo "$NOM arrêté" ;;
  status) docker ps --filter "name=^${NOM}$" --format '{{.Names}} {{.Status}}' ;;
  *) echo "Usage : $0 [start|stop|status]"; exit 1 ;;
esac
