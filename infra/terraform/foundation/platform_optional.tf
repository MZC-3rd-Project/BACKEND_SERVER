data "aws_caller_identity" "current" {}

resource "aws_security_group" "msk" {
  count = var.enable_msk ? 1 : 0

  name        = "${var.name_prefix}-${var.environment}-msk-sg"
  description = "MSK access security group"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "Kafka plaintext"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_service.id]
  }

  ingress {
    description     = "Kafka TLS"
    from_port       = 9094
    to_port         = 9094
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_service.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

resource "aws_msk_cluster" "this" {
  count = var.enable_msk ? 1 : 0

  cluster_name           = "${var.name_prefix}-${var.environment}-msk"
  kafka_version          = var.msk_kafka_version
  number_of_broker_nodes = var.msk_broker_node_count
  enhanced_monitoring    = var.msk_enhanced_monitoring

  broker_node_group_info {
    instance_type = var.msk_instance_type
    client_subnets = slice(
      [for s in values(aws_subnet.private) : s.id],
      0,
      min(length(aws_subnet.private), 3)
    )
    security_groups = [aws_security_group.msk[0].id]

    storage_info {
      ebs_storage_info {
        volume_size = var.msk_ebs_volume_size
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"
      in_cluster    = true
    }
  }

  client_authentication {
    unauthenticated = var.msk_client_authentication_unauthenticated
  }

  tags = local.common_tags
}

resource "aws_security_group" "opensearch" {
  count = var.enable_opensearch ? 1 : 0

  name        = "${var.name_prefix}-${var.environment}-opensearch-sg"
  description = "OpenSearch access security group"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "HTTPS from ECS services"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_service.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

data "aws_iam_policy_document" "opensearch_access" {
  count = var.enable_opensearch ? 1 : 0

  statement {
    effect = "Allow"
    actions = [
      "es:ESHttpDelete",
      "es:ESHttpGet",
      "es:ESHttpHead",
      "es:ESHttpPatch",
      "es:ESHttpPost",
      "es:ESHttpPut"
    ]

    resources = [
      "arn:aws:es:${var.aws_region}:${data.aws_caller_identity.current.account_id}:domain/${coalesce(var.opensearch_domain_name, replace("${var.name_prefix}-${var.environment}-search", "_", "-"))}/*"
    ]

    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.task.arn]
    }
  }
}

resource "aws_opensearch_domain" "this" {
  count = var.enable_opensearch ? 1 : 0

  domain_name    = coalesce(var.opensearch_domain_name, replace("${var.name_prefix}-${var.environment}-search", "_", "-"))
  engine_version = var.opensearch_engine_version

  cluster_config {
    instance_type          = var.opensearch_instance_type
    instance_count         = var.opensearch_instance_count
    zone_awareness_enabled = var.opensearch_instance_count > 1

    dynamic "zone_awareness_config" {
      for_each = var.opensearch_instance_count > 1 ? [1] : []
      content {
        availability_zone_count = min(length(aws_subnet.private), 3)
      }
    }
  }

  ebs_options {
    ebs_enabled = true
    volume_size = var.opensearch_ebs_volume_size
  }

  encrypt_at_rest {
    enabled = true
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-2019-07"
  }

  vpc_options {
    subnet_ids         = var.opensearch_instance_count > 1 ? [for s in values(aws_subnet.private) : s.id] : [values(aws_subnet.private)[0].id]
    security_group_ids = [aws_security_group.opensearch[0].id]
  }

  access_policies = data.aws_iam_policy_document.opensearch_access[0].json

  tags = local.common_tags
}
