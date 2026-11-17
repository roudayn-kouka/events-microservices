# Réseau minimal : un VPC, un sous-réseau public, une passerelle Internet. Pas de NAT (coût).
data "aws_availability_zones" "zones" {
  state = "available"
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
}

resource "aws_vpc" "events" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "${var.project}-vpc" }
}

resource "aws_internet_gateway" "events" {
  vpc_id = aws_vpc.events.id
  tags   = { Name = "${var.project}-igw" }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.events.id
  cidr_block              = "10.42.1.0/24"
  availability_zone       = data.aws_availability_zones.zones.names[0]
  map_public_ip_on_launch = false # l'IP publique est l'IP élastique ci-dessous
  tags                    = { Name = "${var.project}-public" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.events.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.events.id
  }
  tags = { Name = "${var.project}-public" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Pare-feu : SSH et ports de démonstration ouverts à admin_cidr SEULEMENT.
# Descriptions en ASCII : AWS refuse les accents (erreur détectée par terraform test).
resource "aws_security_group" "events" {
  name        = "${var.project}-ec2"
  description = "Events microservices - SSH et demonstration reserves a admin_cidr"
  vpc_id      = aws_vpc.events.id

  ingress {
    description = "SSH (Ansible, rsync)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr]
  }

  dynamic "ingress" {
    for_each = var.ports_demo
    content {
      description = "Demo ${ingress.key}"
      from_port   = ingress.value
      to_port     = ingress.value
      protocol    = "tcp"
      cidr_blocks = [var.admin_cidr]
    }
  }

  egress {
    description = "Sorties - paquets, images Docker, Maven Central"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project}-ec2" }
}

resource "aws_key_pair" "events" {
  key_name   = "${var.project}-cle"
  public_key = file(pathexpand(var.ssh_public_key_path))
}

resource "aws_instance" "events" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.events.id]
  key_name               = aws_key_pair.events.key_name
  iam_instance_profile   = var.instance_profile # existant (AWS Academy) : aucune ressource IAM créée ici

  metadata_options {
    http_tokens   = "required" # IMDSv2 obligatoire
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.volume_go
    encrypted             = true
    delete_on_termination = true
  }

  tags = { Name = "${var.project}-ec2" }
}

resource "aws_eip" "events" {
  domain   = "vpc"
  instance = aws_instance.events.id
  tags     = { Name = "${var.project}-eip" }

  depends_on = [aws_internet_gateway.events]
}
