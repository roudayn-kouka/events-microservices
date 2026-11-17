output "ip_publique" {
  description = "IP élastique de l'instance"
  value       = aws_eip.events.public_ip
}

output "ssh" {
  description = "Connexion SSH"
  value       = "ssh ubuntu@${aws_eip.events.public_ip}"
}

output "urls" {
  description = "URL de démonstration (accessibles depuis admin_cidr seulement)"
  value       = { for nom, port in var.ports_demo : nom => "http://${aws_eip.events.public_ip}:${port}" }
}

output "region" {
  value = var.region
}
