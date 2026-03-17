locals {
  cluster_name    = coalesce(var.cluster_name, "${var.name_prefix}-${var.environment}-eks")
  node_group_name = coalesce(var.node_group_name, "${var.environment}-apps-x86")

  common_tags = merge(
    {
      ManagedBy   = "terraform"
      Environment = var.environment
      Project     = var.name_prefix
      Stack       = "eks-foundation"
    },
    var.default_tags
  )

  managed_addons = {
    for addon_name in var.managed_addons : addon_name => {
      version = lookup(var.managed_addon_versions, addon_name, null)
    }
  }

  oidc_issuer_hostpath = try(
    replace(aws_eks_cluster.this.identity[0].oidc[0].issuer, "https://", ""),
    null
  )

  helm_addons = {
    aws_load_balancer_controller = {
      namespace            = var.system_namespace
      service_account_name = var.aws_load_balancer_controller_service_account_name
      iam_role_arn         = aws_iam_role.aws_load_balancer_controller.arn
    }
    external_secrets = {
      namespace            = var.system_namespace
      service_account_name = var.external_secrets_service_account_name
      iam_role_arn         = aws_iam_role.external_secrets.arn
    }
    metrics_server = {
      namespace            = var.system_namespace
      service_account_name = "metrics-server"
      iam_role_arn         = null
    }
  }
}
