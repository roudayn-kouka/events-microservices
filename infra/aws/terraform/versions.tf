# Phase 12 — Cible A : une EC2 avec Docker Compose (docs/PLAN.md, section 8).
# Préparé et validé SANS apply (fmt, validate, terraform test avec fournisseur simulé).
terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.100" # même version majeure que la plateforme de l'équipe (5.100.0)
    }
  }

  # État local par défaut (une seule personne, Learner Lab éphémère).
  # Pour un état partagé : backend S3 (voir AWS-RUNBOOK.md, « État Terraform »).
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      project    = var.project
      owner      = "roudayna-kouka"
      managed-by = "terraform"
    }
  }
}
