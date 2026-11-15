#!/usr/bin/env bash
# Crée .env à partir de .env.example en remplaçant chaque « change-me » par un mot de
# passe aléatoire. Ne touche jamais un .env existant. Aucun secret n'est affiché.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ -f .env ]]; then
  echo ".env existe déjà : conservé tel quel."
  exit 0
fi

while IFS= read -r ligne; do
  if [[ "$ligne" =~ ^([A-Z_]+)=change-me$ ]]; then
    printf '%s=%s\n' "${BASH_REMATCH[1]}" "$(openssl rand -hex 16)"
  else
    printf '%s\n' "$ligne"
  fi
done < .env.example > .env
chmod 600 .env
echo ".env créé avec des mots de passe aléatoires ($(grep -c '=' .env) variables, droits 600)."
