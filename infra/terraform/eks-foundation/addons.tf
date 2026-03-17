resource "aws_eks_addon" "managed" {
  for_each = local.managed_addons

  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = each.key
  addon_version               = each.value.version
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"
  service_account_role_arn    = each.key == "aws-ebs-csi-driver" ? aws_iam_role.ebs_csi.arn : null

  depends_on = [
    aws_eks_node_group.default,
    aws_iam_role_policy_attachment.ebs_csi_AmazonEBSCSIDriverPolicy
  ]

  tags = local.common_tags
}
