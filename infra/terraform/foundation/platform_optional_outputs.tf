output "msk_cluster_arn" {
  value = var.enable_msk ? aws_msk_cluster.this[0].arn : null
}

output "msk_bootstrap_brokers" {
  value = var.enable_msk ? aws_msk_cluster.this[0].bootstrap_brokers : null
}

output "msk_bootstrap_brokers_tls" {
  value = var.enable_msk ? aws_msk_cluster.this[0].bootstrap_brokers_tls : null
}

output "opensearch_domain_arn" {
  value = var.enable_opensearch ? aws_opensearch_domain.this[0].arn : null
}

output "opensearch_endpoint" {
  value = var.enable_opensearch ? aws_opensearch_domain.this[0].endpoint : null
}
