data "aws_ami" "amazon_linux_2023_elasticsearch" {
  count = var.enable_ec2_elasticsearch && var.ec2_elasticsearch_ami_id == null ? 1 : 0

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

resource "aws_security_group" "ec2_elasticsearch" {
  count = var.enable_ec2_elasticsearch ? 1 : 0

  name        = "${var.name_prefix}-${var.environment}-ec2-elasticsearch-sg"
  description = "EC2 Elasticsearch access security group"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "Elasticsearch HTTP from ECS services"
    from_port       = 9200
    to_port         = 9200
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_service.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-ec2-elasticsearch-sg"
  })
}

resource "aws_iam_role" "ec2_elasticsearch" {
  count = var.enable_ec2_elasticsearch ? 1 : 0

  name               = "${var.name_prefix}-${var.environment}-ec2-elasticsearch-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "ec2_elasticsearch_ssm" {
  count = var.enable_ec2_elasticsearch ? 1 : 0

  role       = aws_iam_role.ec2_elasticsearch[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2_elasticsearch" {
  count = var.enable_ec2_elasticsearch ? 1 : 0

  name = "${var.name_prefix}-${var.environment}-ec2-elasticsearch-profile"
  role = aws_iam_role.ec2_elasticsearch[0].name
}

resource "aws_instance" "ec2_elasticsearch" {
  count = var.enable_ec2_elasticsearch ? 1 : 0

  ami           = coalesce(var.ec2_elasticsearch_ami_id, data.aws_ami.amazon_linux_2023_elasticsearch[0].id)
  instance_type = var.ec2_elasticsearch_instance_type
  subnet_id     = coalesce(var.ec2_elasticsearch_subnet_id, values(aws_subnet.private)[0].id)
  key_name      = var.ec2_elasticsearch_key_name

  vpc_security_group_ids      = [aws_security_group.ec2_elasticsearch[0].id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_elasticsearch[0].name
  associate_public_ip_address = false

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    volume_size = var.ec2_elasticsearch_ebs_volume_size
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

    sysctl -w vm.max_map_count=262144
    cat <<'SYSCTL' >/etc/sysctl.d/99-elasticsearch.conf
    vm.max_map_count=262144
    SYSCTL

    mkdir -p /opt/elasticsearch-data
    chown -R 1000:1000 /opt/elasticsearch-data

    mkdir -p /opt/elasticsearch-image
    cat <<'DOCKERFILE' >/opt/elasticsearch-image/Dockerfile
    FROM docker.elastic.co/elasticsearch/elasticsearch:8.13.4
    RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch analysis-nori
    DOCKERFILE
    docker build -t project03-elasticsearch:8.13.4-nori /opt/elasticsearch-image

    docker rm -f elasticsearch || true
    docker run -d \
      --name elasticsearch \
      --restart always \
      --network host \
      --ulimit nofile=65535:65535 \
      -v /opt/elasticsearch-data:/usr/share/elasticsearch/data \
      -e discovery.type=single-node \
      -e xpack.security.enabled=false \
      -e ES_JAVA_OPTS="-Xms${var.ec2_elasticsearch_heap_size} -Xmx${var.ec2_elasticsearch_heap_size}" \
      project03-elasticsearch:8.13.4-nori
  EOF

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-elasticsearch-ec2-1"
  })

  depends_on = [aws_iam_role_policy_attachment.ec2_elasticsearch_ssm]
}
