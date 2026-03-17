variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "Optional shared AWS profile name for local Terraform execution"
  type        = string
  default     = null
}

variable "environment" {
  description = "Environment name such as dev, stage, or prod"
  type        = string
}

variable "name_prefix" {
  description = "Project prefix used in naming"
  type        = string

  validation {
    condition     = startswith(var.name_prefix, "donmoa")
    error_message = "name_prefix must start with 'donmoa'."
  }
}

variable "default_tags" {
  description = "Additional tags"
  type        = map(string)
  default     = {}
}

variable "cluster_name" {
  description = "Optional EKS cluster name override"
  type        = string
  default     = null
}

variable "cluster_version" {
  description = "EKS Kubernetes version"
  type        = string
}

variable "vpc_id" {
  description = "Existing VPC id to place the cluster into"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet ids for EKS control plane and node groups"
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "private_subnet_ids must include at least two subnets."
  }
}

variable "cluster_endpoint_private_access" {
  description = "Whether the EKS API endpoint is reachable privately"
  type        = bool
  default     = true
}

variable "cluster_endpoint_public_access" {
  description = "Whether the EKS API endpoint is reachable publicly"
  type        = bool
  default     = true
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDRs allowed to access the public EKS API endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "enabled_cluster_log_types" {
  description = "Control plane log types to enable"
  type        = list(string)
  default     = ["api", "audit", "authenticator"]
}

variable "node_group_name" {
  description = "Optional node group name override"
  type        = string
  default     = null
}

variable "node_group_instance_types" {
  description = "Instance types for the managed node group"
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_group_capacity_type" {
  description = "Managed node group capacity type"
  type        = string
  default     = "ON_DEMAND"

  validation {
    condition     = contains(["ON_DEMAND", "SPOT"], var.node_group_capacity_type)
    error_message = "node_group_capacity_type must be ON_DEMAND or SPOT."
  }
}

variable "node_group_desired_size" {
  description = "Desired node count"
  type        = number
  default     = 3
}

variable "node_group_min_size" {
  description = "Minimum node count"
  type        = number
  default     = 1
}

variable "node_group_max_size" {
  description = "Maximum node count"
  type        = number
  default     = 4
}

variable "node_group_root_volume_size" {
  description = "Root volume size in GiB for worker nodes"
  type        = number
  default     = 80
}

variable "node_group_root_volume_type" {
  description = "Root volume type for worker nodes"
  type        = string
  default     = "gp3"
}

variable "node_group_max_unavailable" {
  description = "Maximum unavailable nodes during managed updates"
  type        = number
  default     = 1
}

variable "managed_addons" {
  description = "Managed EKS addons to install"
  type        = set(string)
  default = [
    "coredns",
    "kube-proxy",
    "vpc-cni",
    "aws-ebs-csi-driver"
  ]
}

variable "managed_addon_versions" {
  description = "Optional explicit addon versions keyed by addon name"
  type        = map(string)
  default     = {}
}

variable "system_namespace" {
  description = "Namespace for platform controllers installed by Helm"
  type        = string
  default     = "donmoa-system"
}

variable "aws_load_balancer_controller_service_account_name" {
  description = "Service account name that AWS Load Balancer Controller will use"
  type        = string
  default     = "aws-load-balancer-controller"
}

variable "external_secrets_service_account_name" {
  description = "Service account name that External Secrets will use"
  type        = string
  default     = "external-secrets"
}

variable "external_secrets_secret_arns" {
  description = "Optional list of secret ARNs External Secrets may read"
  type        = list(string)
  default     = []
}
