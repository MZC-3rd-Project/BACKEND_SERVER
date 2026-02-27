output "vpc_id" {
  value = aws_vpc.this.id
}

output "public_subnet_ids" {
  value = [for s in values(aws_subnet.public) : s.id]
}

output "private_subnet_ids" {
  value = [for s in values(aws_subnet.private) : s.id]
}

output "ecs_cluster_arn" {
  value = aws_ecs_cluster.this.arn
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecs_task_execution_role_arn" {
  value = aws_iam_role.task_execution.arn
}

output "ecs_task_role_arn" {
  value = aws_iam_role.task.arn
}

output "ecs_service_security_group_id" {
  value = aws_security_group.ecs_service.id
}

output "alb_arn" {
  value = var.create_alb ? aws_lb.this[0].arn : null
}

output "alb_dns_name" {
  value = var.create_alb ? aws_lb.this[0].dns_name : null
}

output "gateway_target_group_arn" {
  value = var.create_alb ? aws_lb_target_group.gateway[0].arn : null
}

output "gateway_listener_arn" {
  value = var.create_alb ? aws_lb_listener.http[0].arn : null
}

output "aurora_cluster_id" {
  value = var.enable_aurora ? aws_rds_cluster.aurora[0].id : null
}

output "aurora_writer_endpoint" {
  value = var.enable_aurora ? aws_rds_cluster.aurora[0].endpoint : null
}

output "aurora_reader_endpoint" {
  value = var.enable_aurora ? aws_rds_cluster.aurora[0].reader_endpoint : null
}

output "aurora_port" {
  value = var.enable_aurora ? aws_rds_cluster.aurora[0].port : null
}

output "aurora_master_secret_arn" {
  value = var.enable_aurora && var.create_aurora_master_secret ? aws_secretsmanager_secret.aurora_master[0].arn : null
}

output "redis_primary_endpoint" {
  value = var.enable_redis ? aws_elasticache_replication_group.this[0].primary_endpoint_address : null
}

output "redis_reader_endpoint" {
  value = var.enable_redis ? aws_elasticache_replication_group.this[0].reader_endpoint_address : null
}

output "redis_port" {
  value = var.enable_redis ? aws_elasticache_replication_group.this[0].port : null
}

output "ec2_kafka_instance_id" {
  value = var.enable_ec2_kafka ? aws_instance.ec2_kafka[0].id : null
}

output "ec2_kafka_private_ip" {
  value = var.enable_ec2_kafka ? aws_instance.ec2_kafka[0].private_ip : null
}

output "ec2_kafka_private_dns" {
  value = var.enable_ec2_kafka ? aws_instance.ec2_kafka[0].private_dns : null
}

output "ec2_kafka_bootstrap_server" {
  value = var.enable_ec2_kafka ? "${aws_instance.ec2_kafka[0].private_dns}:9092" : null
}

output "ec2_elasticsearch_instance_id" {
  value = var.enable_ec2_elasticsearch ? aws_instance.ec2_elasticsearch[0].id : null
}

output "ec2_elasticsearch_private_ip" {
  value = var.enable_ec2_elasticsearch ? aws_instance.ec2_elasticsearch[0].private_ip : null
}

output "ec2_elasticsearch_private_dns" {
  value = var.enable_ec2_elasticsearch ? aws_instance.ec2_elasticsearch[0].private_dns : null
}

output "ec2_elasticsearch_endpoint" {
  value = var.enable_ec2_elasticsearch ? "http://${aws_instance.ec2_elasticsearch[0].private_ip}:9200" : null
}

output "service_discovery_namespace_id" {
  value = var.enable_service_discovery ? aws_service_discovery_private_dns_namespace.this[0].id : null
}

output "service_discovery_namespace_name" {
  value = var.enable_service_discovery ? aws_service_discovery_private_dns_namespace.this[0].name : null
}

output "ecr_repository_urls" {
  value = {
    for name, repo in aws_ecr_repository.this : name => repo.repository_url
  }
}

output "ecs_app_inputs" {
  value = {
    cluster_arn              = aws_ecs_cluster.this.arn
    subnet_ids               = [for s in values(aws_subnet.private) : s.id]
    security_group_ids       = [aws_security_group.ecs_service.id]
    task_execution_role_arn  = aws_iam_role.task_execution.arn
    task_role_arn            = aws_iam_role.task.arn
    gateway_target_group_arn = var.create_alb ? aws_lb_target_group.gateway[0].arn : null
    kafka_bootstrap_server   = var.enable_ec2_kafka ? "${aws_instance.ec2_kafka[0].private_dns}:9092" : null
    elasticsearch_endpoint   = var.enable_ec2_elasticsearch ? "http://${aws_instance.ec2_elasticsearch[0].private_ip}:9200" : null
    sd_namespace_id          = var.enable_service_discovery ? aws_service_discovery_private_dns_namespace.this[0].id : null
    sd_namespace_name        = var.enable_service_discovery ? aws_service_discovery_private_dns_namespace.this[0].name : null
  }
}
