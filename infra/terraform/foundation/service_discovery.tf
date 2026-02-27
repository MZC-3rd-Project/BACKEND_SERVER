resource "aws_service_discovery_private_dns_namespace" "this" {
  count = var.enable_service_discovery ? 1 : 0

  name        = var.service_discovery_namespace_name
  description = "Private DNS namespace for ECS service discovery"
  vpc         = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-sd-namespace"
  })
}
