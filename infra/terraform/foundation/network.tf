resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-vpc"
  })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-igw"
  })
}

resource "aws_subnet" "public" {
  for_each = local.public_subnet_map

  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value
  availability_zone       = local.azs[tonumber(each.key)]
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name                     = "${var.name_prefix}-${var.environment}-public-${tonumber(each.key) + 1}"
    Tier                     = "public"
    "kubernetes.io/role/elb" = "1"
    }, var.eks_cluster_name != null ? {
    "kubernetes.io/cluster/${var.eks_cluster_name}" = "shared"
  } : {})
}

resource "aws_subnet" "private" {
  for_each = local.private_subnet_map

  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value
  availability_zone       = local.azs[tonumber(each.key)]
  map_public_ip_on_launch = false

  tags = merge(local.common_tags, {
    Name                              = "${var.name_prefix}-${var.environment}-private-${tonumber(each.key) + 1}"
    Tier                              = "private"
    "kubernetes.io/role/internal-elb" = "1"
    }, var.eks_cluster_name != null ? {
    "kubernetes.io/cluster/${var.eks_cluster_name}" = "shared"
  } : {})
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-rt-public"
  })
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_eip" "nat" {
  count = local.nat_gateway_count

  domain = "vpc"

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-nat-eip-${count.index + 1}"
  })
}

resource "aws_nat_gateway" "this" {
  count = local.nat_gateway_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id = values(aws_subnet.public)[
    var.single_nat_gateway ? 0 : count.index
  ].id

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-nat-${count.index + 1}"
  })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_route_table" "private" {
  for_each = aws_subnet.private

  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-rt-private-${tonumber(each.key) + 1}"
  })
}

resource "aws_route" "private_default" {
  for_each = var.enable_nat_gateway ? aws_route_table.private : {}

  route_table_id         = each.value.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id = aws_nat_gateway.this[
    var.single_nat_gateway ? 0 : tonumber(each.key)
  ].id
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private[each.key].id
}

locals {
  private_subnet_nacl_private_rules = {
    for idx, cidr in var.private_subnet_cidrs : idx => {
      cidr            = cidr
      ingress_rule_no = 100 + idx
      egress_rule_no  = 200 + idx
    }
  }

  private_subnet_nacl_public_alb_rules = {
    for rule in flatten([
      for cidr_idx, cidr in var.public_subnet_cidrs : [
        for port_idx, port in var.private_subnet_alb_target_ports : {
          key     = "${cidr_idx}-${port_idx}"
          cidr    = cidr
          port    = port
          rule_no = 300 + (cidr_idx * 10) + port_idx
        }
      ]
    ]) : rule.key => rule
  }

  private_subnet_nacl_public_response_rules = {
    for idx, cidr in var.public_subnet_cidrs : idx => {
      cidr    = cidr
      rule_no = 400 + idx
    }
  }
}

# Private subnets host both EKS workloads and shared data services, so this NACL
# blocks direct public ingress except ALB target ports while preserving private
# east-west traffic and NAT return traffic.
resource "aws_network_acl" "private" {
  count = var.enable_private_subnet_network_acl ? 1 : 0

  vpc_id     = aws_vpc.this.id
  subnet_ids = [for s in values(aws_subnet.private) : s.id]

  dynamic "ingress" {
    for_each = local.private_subnet_nacl_private_rules

    content {
      rule_no    = ingress.value.ingress_rule_no
      protocol   = "-1"
      action     = "allow"
      cidr_block = ingress.value.cidr
      from_port  = 0
      to_port    = 0
    }
  }

  dynamic "ingress" {
    for_each = local.private_subnet_nacl_public_alb_rules

    content {
      rule_no    = ingress.value.rule_no
      protocol   = "tcp"
      action     = "allow"
      cidr_block = ingress.value.cidr
      from_port  = ingress.value.port
      to_port    = ingress.value.port
    }
  }

  ingress {
    rule_no    = 500
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  dynamic "egress" {
    for_each = local.private_subnet_nacl_private_rules

    content {
      rule_no    = egress.value.egress_rule_no
      protocol   = "-1"
      action     = "allow"
      cidr_block = egress.value.cidr
      from_port  = 0
      to_port    = 0
    }
  }

  egress {
    rule_no    = 300
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 80
    to_port    = 80
  }

  egress {
    rule_no    = 310
    protocol   = "tcp"
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 443
    to_port    = 443
  }

  egress {
    rule_no    = 320
    protocol   = "udp"
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 53
    to_port    = 53
  }

  egress {
    rule_no    = 330
    protocol   = "tcp"
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 53
    to_port    = 53
  }

  dynamic "egress" {
    for_each = local.private_subnet_nacl_public_response_rules

    content {
      rule_no    = egress.value.rule_no
      protocol   = "tcp"
      action     = "allow"
      cidr_block = egress.value.cidr
      from_port  = 1024
      to_port    = 65535
    }
  }

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-nacl-private"
  })
}
