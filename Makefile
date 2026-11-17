# =============================================================================
#  Events — microservices : commandes locales (make help)
# =============================================================================
SHELL     := /bin/bash
COMPOSE   := docker compose
# Branche lue par le Config Server dans config-repo/ : la branche Git courante
BRANCHE   := $(shell git branch --show-current)
PROFILES  ?= --profile core

.DEFAULT_GOAL := help
.PHONY: help env build up restart down ps logs health seed panne repartition replay newman newman-pannes front-test e2e

help: ## Liste des commandes
	@awk -F ':.*## ' '/^[a-z-]+:.*## / { printf "  make %-8s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

env: ## Crée .env (mots de passe aléatoires) s'il n'existe pas
	@bash scripts/init-env.sh

build: ## Compile et teste tous les modules (mvn verify)
	mvn -B verify

up: env ## Construit les images et démarre la stack (attend que tout soit « healthy »)
	@if ! git diff --quiet HEAD -- config-repo; then echo "Attention : config-repo/ a des modifications non commitées, le Config Server ne les verra pas."; fi
	CONFIG_GIT_LABEL=$(BRANCHE) $(COMPOSE) $(PROFILES) up -d --build --wait

restart: ## Reconstruit et recrée un service, branche Git courante gardée : make restart S=logistics-service
	@test -n "$(S)" || { echo "Préciser le service : make restart S=<service>"; exit 1; }
	CONFIG_GIT_LABEL=$(BRANCHE) $(COMPOSE) $(PROFILES) up -d --build --force-recreate --no-deps --wait $(S)

down: ## Arrête la stack (les volumes de données sont conservés)
	$(COMPOSE) --profile '*' down

ps: ## État des conteneurs
	$(COMPOSE) --profile '*' ps

logs: ## Journaux en continu (Ctrl+C pour quitter)
	$(COMPOSE) --profile '*' logs -f --tail=100

health: ## Vérifie Config Server, Eureka, Gateway et MySQL
	@bash scripts/health.sh

seed: ## Crée le jeu de données de démonstration par les API (FORCE=1 pour recréer)
	@bash scripts/seed-demo.sh

panne: ## Joue un scénario de panne et le rétablit : make panne N=1 (1 à 5, voir scripts/pannes)
	@test -n "$(N)" || { echo "Préciser la panne : make panne N=1 (1 à 5)"; exit 1; }
	@bash scripts/pannes/$(N)-*.sh

repartition: ## Mesure la répartition de charge entre les 2 instances (Gateway, Feign, bascule)
	@bash scripts/repartition.sh

replay: ## Replay coordonné de la projection CQRS de statistics-service (toutes les instances)
	@bash scripts/replay.sh

newman: ## Joue la collection Postman complète (Newman, via la Gateway)
	@python3 postman/generate.py >/dev/null
	@docker run --rm --network host -v "$(CURDIR)/postman:/etc/newman" postman/newman:6-alpine run events-microservices.postman_collection.json -e local.postman_environment.json

newman-pannes: ## Les 5 pannes créées puis vérifiées par Newman, puis rétablies
	@python3 postman/generate.py >/dev/null
	@bash scripts/newman-pannes.sh

front-test: ## Frontend : tests unitaires (Vitest) et build de production, dans node:22
	docker run --rm --user $$(id -u):$$(id -g) -e HOME=/tmp -v "$(CURDIR)/frontend:/w" -w /w node:22-alpine \
	  sh -c "npm ci --no-audit --no-fund && npm test && npm run build"

e2e: ## Frontend : parcours complet dans Chromium (Playwright), captures dans frontend/e2e/captures
	@mkdir -p frontend/e2e/captures
	docker run --rm --network host --user $$(id -u):$$(id -g) -e HOME=/tmp -v "$(CURDIR)/frontend/e2e:/e2e" \
	  -v "$(CURDIR)/frontend/e2e/captures:/shots" -w /e2e mcr.microsoft.com/playwright:v1.55.0-noble \
	  sh -c "test -d node_modules/playwright || (npm init -y >/dev/null && npm i playwright@1.55.0 --no-audit --no-fund >/dev/null); node parcours.mjs /shots"
