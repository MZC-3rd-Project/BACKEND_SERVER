data "aws_ami" "amazon_linux_2023" {
  count = var.enable_ec2_kafka && var.ec2_kafka_ami_id == null ? 1 : 0

  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_security_group" "ec2_kafka" {
  count = var.enable_ec2_kafka ? 1 : 0

  name        = "${var.name_prefix}-${var.environment}-ec2-kafka-sg"
  description = "EC2 Kafka access security group"
  vpc_id      = aws_vpc.this.id

  dynamic "ingress" {
    for_each = var.eks_node_security_group_id != null ? [var.eks_node_security_group_id] : []

    content {
      description     = "Kafka from EKS worker nodes"
      from_port       = 9092
      to_port         = 9092
      protocol        = "tcp"
      security_groups = [ingress.value]
    }
  }

  ingress {
    description = "Controller listener (self)"
    from_port   = 9093
    to_port     = 9093
    protocol    = "tcp"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-ec2-kafka-sg"
  })
}

data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2_kafka" {
  count = var.enable_ec2_kafka ? 1 : 0

  name               = "${var.name_prefix}-${var.environment}-ec2-kafka-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "ec2_kafka_ssm" {
  count = var.enable_ec2_kafka ? 1 : 0

  role       = aws_iam_role.ec2_kafka[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2_kafka" {
  count = var.enable_ec2_kafka ? 1 : 0

  name = "${var.name_prefix}-${var.environment}-ec2-kafka-profile"
  role = aws_iam_role.ec2_kafka[0].name
}

resource "aws_instance" "ec2_kafka" {
  count = var.enable_ec2_kafka ? 1 : 0

  ami           = coalesce(var.ec2_kafka_ami_id, data.aws_ami.amazon_linux_2023[0].id)
  instance_type = var.ec2_kafka_instance_type
  subnet_id     = coalesce(var.ec2_kafka_subnet_id, values(aws_subnet.private)[0].id)
  key_name      = var.ec2_kafka_key_name

  vpc_security_group_ids      = [aws_security_group.ec2_kafka[0].id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_kafka[0].name
  associate_public_ip_address = false

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    volume_size = var.ec2_kafka_ebs_volume_size
    volume_type = "gp3"
    encrypted   = true
  }

  user_data = <<-EOF
    #!/bin/bash
    set -euxo pipefail

    dnf update -y
    dnf install -y docker
    systemctl enable docker
    systemctl start docker

    mkdir -p /opt/kafka-data

    TOKEN=$(curl -sS -X PUT "http://169.254.169.254/latest/api/token" \
      -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
    PRIVATE_IP=$(curl -sS -H "X-aws-ec2-metadata-token: $${TOKEN}" \
      "http://169.254.169.254/latest/meta-data/local-ipv4")

    CLUSTER_ID=$(docker run --rm apache/kafka:3.8.0 /opt/kafka/bin/kafka-storage.sh random-uuid | tr -d '\r\n')

    docker rm -f kafka || true
    docker run -d \
      --name kafka \
      --restart always \
      --network host \
      -v /opt/kafka-data:/tmp/kraft-combined-logs \
      -e CLUSTER_ID="$${CLUSTER_ID}" \
      -e KAFKA_NODE_ID=1 \
      -e KAFKA_PROCESS_ROLES=broker,controller \
      -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
      -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://$${PRIVATE_IP}:9092 \
      -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT \
      -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
      -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
      -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@$${PRIVATE_IP}:9093 \
      -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
      -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
      -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
      apache/kafka:3.8.0
  EOF

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-kafka-ec2-1"
  })

  depends_on = [aws_iam_role_policy_attachment.ec2_kafka_ssm]
}
