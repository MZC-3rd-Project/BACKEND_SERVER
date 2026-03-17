resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-${var.environment}-alb-sg"
  description = "ALB ingress security group"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = var.alb_ingress_cidrs
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = var.alb_ingress_cidrs
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

resource "aws_security_group" "ecs_service" {
  name        = "${var.name_prefix}-${var.environment}-ecs-service-sg"
  description = "ECS service security group"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "From ALB to gateway port"
    from_port       = var.gateway_container_port
    to_port         = var.gateway_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "Inter-service communication in ECS"
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

resource "aws_security_group" "db" {
  name        = "${var.name_prefix}-${var.environment}-db-sg"
  description = "Aurora access security group"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "PostgreSQL from ECS services"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_service.id]
  }

  dynamic "ingress" {
    for_each = var.runtime_ingress_cidr_blocks

    content {
      description = "PostgreSQL from runtime CIDR"
      from_port   = 5432
      to_port     = 5432
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }

  dynamic "ingress" {
    for_each = var.enable_ec2_bastion ? [aws_security_group.ec2_bastion[0].id] : []

    content {
      description     = "PostgreSQL from Bastion host"
      from_port       = 5432
      to_port         = 5432
      protocol        = "tcp"
      security_groups = [ingress.value]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

resource "aws_security_group" "redis" {
  name        = "${var.name_prefix}-${var.environment}-redis-sg"
  description = "ElastiCache Redis access security group"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "Redis from ECS services"
    from_port       = var.redis_port
    to_port         = var.redis_port
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_service.id]
  }

  dynamic "ingress" {
    for_each = var.runtime_ingress_cidr_blocks

    content {
      description = "Redis from runtime CIDR"
      from_port   = var.redis_port
      to_port     = var.redis_port
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}
