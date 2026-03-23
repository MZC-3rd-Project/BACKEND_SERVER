output "cluster_name" {
  value = aws_eks_cluster.this.name
}

output "cluster_arn" {
  value = aws_eks_cluster.this.arn
}

output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint
}

output "cluster_version" {
  value = aws_eks_cluster.this.version
}

output "cluster_oidc_issuer_url" {
  value = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.this.arn
}

output "cluster_security_group_id" {
  value = aws_security_group.cluster.id
}

output "node_security_group_id" {
  value = aws_security_group.nodes.id
}

output "node_group_name" {
  value = aws_eks_node_group.default.node_group_name
}

output "node_group_role_arn" {
  value = aws_iam_role.node_group.arn
}

output "managed_addons" {
  value = {
    for name, addon in aws_eks_addon.managed : name => {
      arn     = addon.arn
      version = addon.addon_version
    }
  }
}

output "helm_addons" {
  value = local.helm_addons
}

output "external_secrets_role_arn" {
  value = aws_iam_role.external_secrets.arn
}

output "search_service_role_arn" {
  value = aws_iam_role.search_service.arn
}

output "cart_service_role_arn" {
  value = try(aws_iam_role.cart_service[0].arn, null)
}

output "ebs_csi_role_arn" {
  value = aws_iam_role.ebs_csi.arn
}

output "kubeconfig_update_command" {
  value = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.this.name}"
}
