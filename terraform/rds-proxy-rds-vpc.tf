# RDS Proxy must run in the same VPC as RDS and ECS for connectivity.
# Data sources discover the actual VPC where RDS lives (may differ from Terraform VPC).

data "aws_db_instance" "main" {
  db_instance_identifier = "cg-notification-db"
}

data "aws_db_subnet_group" "rds" {
  name = "cg-notification-db-subnet"
}

data "aws_subnet" "rds_subnet_one" {
  id = tolist(data.aws_db_subnet_group.rds.subnet_ids)[0]
}

locals {
  rds_vpc_id = data.aws_subnet.rds_subnet_one.vpc_id
}

# ECS SG in the RDS VPC (allows proxy to accept connections from ECS tasks)
data "aws_security_group" "ecs_in_rds_vpc" {
  vpc_id = local.rds_vpc_id
  name   = "cg-notification-ecs-sg"
}

# RDS Proxy SG in RDS VPC - allow ECS tasks to connect on 5432
# Unique name to avoid conflict with any existing SG from old proxy
resource "aws_security_group" "rds_proxy_in_rds_vpc" {
  name        = "cg-notification-rds-proxy-sg-rds-vpc"
  description = "RDS Proxy SG in RDS VPC for ECS connectivity"
  vpc_id      = local.rds_vpc_id

  ingress {
    description     = "PostgreSQL from ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [data.aws_security_group.ecs_in_rds_vpc.id]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-rds-proxy-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Allow new RDS Proxy SG to reach RDS (RDS instance may use SG not managed by Terraform)
resource "aws_security_group_rule" "rds_allow_proxy_in_rds_vpc" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.rds_proxy_in_rds_vpc.id
  security_group_id        = data.aws_db_instance.main.vpc_security_groups[0]
  description              = "PostgreSQL from RDS Proxy (in RDS VPC)"
}

# RDS VPC public subnets (for ALB - must be in same VPC as ECS targets)
data "aws_subnets" "rds_vpc_public" {
  filter {
    name   = "vpc-id"
    values = [local.rds_vpc_id]
  }
  filter {
    name   = "map-public-ip-on-launch"
    values = ["true"]
  }
}

# ALB security group in RDS VPC (for ALB to receive traffic and reach ECS)
resource "aws_security_group" "alb_in_rds_vpc" {
  name        = "cg-notification-alb-sg-rds-vpc"
  description = "ALB SG in RDS VPC - HTTP/HTTPS in, reach ECS on 8080"
  vpc_id      = local.rds_vpc_id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "cg-notification-alb-rds-vpc" }
}

# Allow ALB (in RDS VPC) to reach ECS tasks on 8080
resource "aws_security_group_rule" "ecs_from_alb_rds_vpc" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb_in_rds_vpc.id
  security_group_id        = data.aws_security_group.ecs_in_rds_vpc.id
  description              = "ALB to ECS tasks (RDS VPC)"
}
