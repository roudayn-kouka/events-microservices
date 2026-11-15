#!/bin/bash
# =============================================================================
#  Exécuté UNE fois par l'image MySQL, au premier démarrage (volume vide).
#  Une base et un utilisateur par microservice : chaque utilisateur n'a de droits
#  que sur SA base (isolation des données entre services).
#  Les mots de passe viennent des variables d'environnement (.env, jamais versionné).
# =============================================================================
set -euo pipefail

creer() {
  local base="$1" utilisateur="$2" mot_de_passe="$3"
  echo "Création de la base ${base} (utilisateur ${utilisateur})"
  mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${base}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${utilisateur}'@'%' IDENTIFIED BY '${mot_de_passe}';
GRANT ALL PRIVILEGES ON \`${base}\`.* TO '${utilisateur}'@'%';
SQL
}

creer db_event       event_user       "${EVENT_DB_PASSWORD}"
creer db_participant participant_user "${PARTICIPANT_DB_PASSWORD}"
creer db_logistics   logistics_user   "${LOGISTICS_DB_PASSWORD}"
creer db_feedback    feedback_user    "${FEEDBACK_DB_PASSWORD}"
creer db_stats       stats_user       "${STATS_DB_PASSWORD}"
creer db_keycloak    keycloak_user    "${KEYCLOAK_DB_PASSWORD}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"
echo "Bases et utilisateurs créés."
