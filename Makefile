# =============================================================================
#  Events — microservices : commandes locales (make help)
# =============================================================================
SHELL     := /bin/bash
COMPOSE   := docker compose
# Branche lue par le Config Server dans config-repo/ : la branche Git courante
BRANCHE   := $(shell git branch --show-current)
PROFILES  ?= --profile core

.DEFAULT_GOAL := help
.PHONY: help env build up restart down ps logs health seed

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
