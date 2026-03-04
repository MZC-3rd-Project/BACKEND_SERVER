resource "aws_elasticache_subnet_group" "this" {
  count = var.enable_redis ? 1 : 0

  name       = "${var.name_prefix}-${var.environment}-redis-subnet-group"
  subnet_ids = [for s in values(aws_subnet.private) : s.id]

  tags = local.common_tags
}

resource "aws_elasticache_replication_group" "this" {
  count = var.enable_redis ? 1 : 0

  replication_group_id = substr(replace("${var.name_prefix}-${var.environment}-redis", "_", "-"), 0, 40)
  description          = "${var.name_prefix} ${var.environment} redis"

  engine                     = "redis"
  engine_version             = var.redis_engine_version
  node_type                  = var.redis_node_type
  port                       = var.redis_port
  num_cache_clusters         = var.redis_num_cache_clusters
  automatic_failover_enabled = local.redis_automatic_failover_enabled
  multi_az_enabled           = local.redis_automatic_failover_enabled

  subnet_group_name          = aws_elasticache_subnet_group.this[0].name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = var.redis_at_rest_encryption_enabled
  transit_encryption_enabled = var.redis_transit_encryption_enabled
  snapshot_retention_limit   = var.redis_snapshot_retention_limit
  apply_immediately          = var.redis_apply_immediately

  tags = local.common_tags
}
