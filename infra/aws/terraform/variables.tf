variable "region" {
  description = "Région AWS. Le Learner Lab d'AWS Academy n'autorise que us-east-1 (et us-west-2)."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Préfixe des noms et étiquette « project »"
  type        = string
  default     = "events-ms"
}

variable "admin_cidr" {
  description = "Seule adresse autorisée (SSH et ports de démonstration), par exemple 196.203.10.20/32 : curl -s https://checkip.amazonaws.com"
  type        = string

  validation {
    condition     = can(cidrhost(var.admin_cidr, 0)) && !startswith(var.admin_cidr, "0.0.0.0/")
    error_message = "admin_cidr doit être un CIDR valide et restreint (jamais 0.0.0.0/0)."
  }
}

variable "instance_type" {
  description = "t3.large (8 Go) : profil lite + swap. Repli : t3.medium (4 Go), profil lite seulement."
  type        = string
  default     = "t3.large"

  validation {
    condition     = contains(["t3.medium", "t3.large", "t3.xlarge", "r5.large"], var.instance_type)
    error_message = "Types prévus : t3.medium, t3.large, t3.xlarge, r5.large (limites du Learner Lab)."
  }
}

variable "volume_go" {
  description = "Taille du disque racine (gp3, chiffré)"
  type        = number
  default     = 40
}

variable "ssh_public_key_path" {
  description = "Clé publique SSH déposée sur l'instance (utilisateur ubuntu)"
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "instance_profile" {
  description = "Profil d'instance EXISTANT. AWS Academy : LabInstanceProfile (aucune création IAM n'est autorisée)."
  type        = string
  default     = "LabInstanceProfile"
}

variable "ports_demo" {
  description = "Ports ouverts à admin_cidr seulement : frontend, Gateway, Eureka, Axon Server, Grafana, Prometheus, Zipkin"
  type        = map(number)
  default = {
    frontend   = 4200
    gateway    = 8088
    eureka     = 8761
    axon       = 8024
    grafana    = 3001
    prometheus = 9091
    zipkin     = 9411
  }
}
