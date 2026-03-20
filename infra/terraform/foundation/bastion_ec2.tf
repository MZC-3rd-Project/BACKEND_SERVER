data "aws_ami" "amazon_linux_2023_bastion" {
  count = var.enable_ec2_bastion && var.ec2_bastion_ami_id == null ? 1 : 0

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

resource "aws_security_group" "ec2_bastion" {
  count = var.enable_ec2_bastion ? 1 : 0

  name        = "${var.name_prefix}-${var.environment}-ec2-bastion-sg"
  description = "Bastion host security group"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "SSH from allowed team CIDRs"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ec2_bastion_ingress_cidrs
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-ec2-bastion-sg"
  })
}

resource "aws_iam_role" "ec2_bastion" {
  count = var.enable_ec2_bastion ? 1 : 0

  name               = "${var.name_prefix}-${var.environment}-ec2-bastion-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "ec2_bastion_ssm" {
  count = var.enable_ec2_bastion ? 1 : 0

  role       = aws_iam_role.ec2_bastion[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "ec2_bastion_aurora_secret_read" {
  count = var.enable_ec2_bastion && var.enable_aurora && var.create_aurora_master_secret ? 1 : 0

  statement {
    sid = "ReadAuroraMasterSecret"

    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue"
    ]

    resources = [aws_secretsmanager_secret.aurora_master[0].arn]
  }
}

resource "aws_iam_role_policy" "ec2_bastion_aurora_secret_read" {
  count = var.enable_ec2_bastion && var.enable_aurora && var.create_aurora_master_secret ? 1 : 0

  name   = "${var.name_prefix}-${var.environment}-ec2-bastion-aurora-secret-read"
  role   = aws_iam_role.ec2_bastion[0].id
  policy = data.aws_iam_policy_document.ec2_bastion_aurora_secret_read[0].json
}

resource "aws_iam_instance_profile" "ec2_bastion" {
  count = var.enable_ec2_bastion ? 1 : 0

  name = "${var.name_prefix}-${var.environment}-ec2-bastion-profile"
  role = aws_iam_role.ec2_bastion[0].name
}

resource "aws_instance" "ec2_bastion" {
  count = var.enable_ec2_bastion ? 1 : 0

  ami           = coalesce(var.ec2_bastion_ami_id, data.aws_ami.amazon_linux_2023_bastion[0].id)
  instance_type = var.ec2_bastion_instance_type
  subnet_id     = coalesce(var.ec2_bastion_subnet_id, values(aws_subnet.public)[0].id)
  key_name      = var.ec2_bastion_key_name

  vpc_security_group_ids      = [aws_security_group.ec2_bastion[0].id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_bastion[0].name
  associate_public_ip_address = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    volume_size = var.ec2_bastion_ebs_volume_size
    volume_type = "gp3"
    encrypted   = true
  }

  user_data = <<-EOF
    #!/bin/bash
    set -euxo pipefail

    dnf update -y
    dnf install -y postgresql15 jq
  EOF

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-bastion-ec2-1"
  })

  depends_on = [aws_iam_role_policy_attachment.ec2_bastion_ssm]
}
