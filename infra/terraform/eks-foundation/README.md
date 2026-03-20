# EKS Foundation

`infra/terraform/eks-foundation` is the Terraform stack for the Kubernetes control plane layer.

This stack is intentionally scoped to:

- `EKS` cluster
- managed node group
- cluster/node IAM roles
- OIDC provider and IRSA bootstrap roles
- managed addons (`coredns`, `kube-proxy`, `vpc-cni`, `aws-ebs-csi-driver`)

This stack does not create the shared stateful layer. Reuse outputs from `infra/terraform/foundation` for:

- `vpc_id`
- `private_subnet_ids`
- `Aurora`, `Redis`, `Kafka`, `Secrets Manager`, `ECR`
- `search_ai_enrichment_queue_arn` when enabling search AI enrichment publishing from search-service

## Files

- `versions.tf`: Terraform and provider requirements
- `providers.tf`: AWS provider
- `variables.tf`: input variables
- `locals.tf`: naming, tags, addon metadata
- `network.tf`: EKS security groups
- `iam.tf`: cluster, node, and IRSA bootstrap roles (including search-service SQS publish role)
- `cluster.tf`: EKS cluster, launch template, managed node group
- `addons.tf`: managed EKS addons
- `outputs.tf`: cluster and addon outputs

## Usage

1. Copy `backend.hcl.example` to `backend.hcl` and adjust the state key.
2. Copy `terraform.tfvars.example` to `terraform.tfvars`.
3. Fill `vpc_id` and `private_subnet_ids` from `infra/terraform/foundation` outputs.
4. Run `terraform init -backend-config=backend.hcl`
5. Run `terraform plan`

This is a foundation skeleton only. Helm-installed controllers such as `aws-load-balancer-controller`, `external-secrets`, and `metrics-server` are wired for follow-up work, not fully installed by this stack yet.
