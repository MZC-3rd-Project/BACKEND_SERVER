locals {
  common_tags = merge(
    {
      ManagedBy   = "terraform"
      Environment = var.environment
      Project     = var.name_prefix
    },
    var.default_tags
  )
}
