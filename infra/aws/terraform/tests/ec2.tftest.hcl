# terraform test : plan avec un fournisseur AWS SIMULÉ (aucun identifiant, aucun appel AWS, aucun apply).
# Vérifie les garde-fous : accès réservé à admin_cidr, pas d'IAM, disque chiffré, IMDSv2.

mock_provider "aws" {
  mock_data "aws_ami" {
    defaults = { id = "ami-0123456789abcdef0" }
  }
  mock_data "aws_availability_zones" {
    defaults = { names = ["us-east-1a", "us-east-1b"] }
  }
}

variables {
  admin_cidr          = "196.203.10.20/32"
  ssh_public_key_path = "tests/fixtures/id_ed25519.pub"
}

run "instance_conforme_au_lab" {
  command = plan

  assert {
    condition     = aws_instance.events.instance_type == "t3.large"
    error_message = "Type par défaut attendu : t3.large"
  }
  assert {
    condition     = aws_instance.events.iam_instance_profile == "LabInstanceProfile"
    error_message = "L'instance doit utiliser le LabInstanceProfile existant"
  }
  assert {
    condition     = aws_instance.events.root_block_device[0].encrypted && aws_instance.events.root_block_device[0].volume_type == "gp3"
    error_message = "Disque racine gp3 chiffré attendu"
  }
  assert {
    condition     = aws_instance.events.metadata_options[0].http_tokens == "required"
    error_message = "IMDSv2 obligatoire"
  }
  assert {
    condition     = var.region == "us-east-1"
    error_message = "Région du Learner Lab attendue : us-east-1"
  }
}

run "acces_reserve_a_admin_cidr" {
  command = plan

  assert {
    condition     = alltrue([for r in aws_security_group.events.ingress : length(r.cidr_blocks) == 1 && contains(r.cidr_blocks, "196.203.10.20/32")])
    error_message = "Toutes les entrées doivent être limitées à admin_cidr"
  }
  assert {
    condition     = length(aws_security_group.events.ingress) == 1 + length(var.ports_demo)
    error_message = "SSH + un port par service de démonstration, rien d'autre"
  }
  assert {
    condition     = contains([for r in aws_security_group.events.ingress : r.from_port], 4200) && contains([for r in aws_security_group.events.ingress : r.from_port], 8088)
    error_message = "Frontend (4200) et Gateway (8088) doivent être ouverts à admin_cidr"
  }
}

run "refuse_un_acces_ouvert_a_tous" {
  command = plan

  variables {
    admin_cidr = "0.0.0.0/0"
  }
  expect_failures = [var.admin_cidr]
}

run "refuse_un_type_hors_lab" {
  command = plan

  variables {
    instance_type = "m5.24xlarge"
  }
  expect_failures = [var.instance_type]
}

run "repli_t3_medium" {
  command = plan

  variables {
    instance_type = "t3.medium"
  }
  assert {
    condition     = aws_instance.events.instance_type == "t3.medium"
    error_message = "Le repli t3.medium doit être accepté"
  }
}
