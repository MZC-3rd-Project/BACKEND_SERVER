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
