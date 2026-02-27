data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = length(var.availability_zones) > 0 ? var.availability_zones : slice(
    data.aws_availability_zones.available.names,
    0,
    length(var.public_subnet_cidrs)
  )

  public_subnet_map = {
    for idx, cidr in var.public_subnet_cidrs : idx => cidr
  }

  private_subnet_map = {
    for idx, cidr in var.private_subnet_cidrs : idx => cidr
  }

  nat_gateway_count = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : length(var.public_subnet_cidrs)) : 0

  common_tags = merge(
    {
      ManagedBy   = "terraform"
      Environment = var.environment
      Project     = var.name_prefix
    },
    var.default_tags
  )

  aurora_master_password = var.enable_aurora ? (
    var.aurora_master_password != null ? var.aurora_master_password : random_password.aurora_master[0].result
  ) : null

  redis_automatic_failover_enabled = var.redis_num_cache_clusters > 1

  ecr_repository_names = toset(var.ecr_repositories)
}
