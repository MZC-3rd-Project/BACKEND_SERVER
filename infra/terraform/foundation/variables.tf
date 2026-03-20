variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "AWS CLI profile name"
  type        = string
  default     = null
}

variable "environment" {
  description = "Environment name (e.g. dev, stage, prod)"
  type        = string
}

variable "name_prefix" {
  description = "Project prefix used in naming"
  type        = string

  validation {
    condition     = startswith(var.name_prefix, "donmoa")
    error_message = "name_prefix must start with 'donmoa' (e.g. donmoa, donmoa-dev)."
  }
}

variable "default_tags" {
  description = "Additional tags"
  type        = map(string)
  default     = {}
}

variable "availability_zones" {
  description = "Optional AZ override. If empty, first N AZs are selected automatically"
  type        = list(string)
  default     = []
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.30.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDR blocks"
  type        = list(string)
  default     = ["10.30.0.0/20", "10.30.16.0/20"]
}

variable "private_subnet_cidrs" {
  description = "Private subnet CIDR blocks"
  type        = list(string)
  default     = ["10.30.128.0/20", "10.30.144.0/20"]

  validation {
    condition     = length(var.private_subnet_cidrs) == length(var.public_subnet_cidrs)
    error_message = "public_subnet_cidrs and private_subnet_cidrs must have the same length."
  }
}

variable "runtime_ingress_cidr_blocks" {
  description = "CIDR blocks allowed to access runtime dependencies from app workloads"
  type        = list(string)
  default     = []
}

variable "eks_node_security_group_id" {
  description = "Optional EKS worker node security group allowed to reach shared dependencies"
  type        = string
  default     = null
}

variable "eks_cluster_name" {
  description = "Optional EKS cluster name for tagging shared subnets"
  type        = string
  default     = null
}

variable "enable_nat_gateway" {
  description = "Whether to create NAT gateway for private subnet egress"
  type        = bool
  default     = true
}

variable "single_nat_gateway" {
  description = "Use single NAT gateway (cost-optimized)"
  type        = bool
  default     = true
}

variable "enable_service_discovery" {
  description = "Whether to create private DNS namespace for ECS service discovery"
  type        = bool
  default     = true
}

variable "service_discovery_namespace_name" {
  description = "Private DNS namespace name for ECS service discovery"
  type        = string
  default     = "internal"
}

variable "create_alb" {
  description = "Whether to create internet-facing ALB for gateway"
  type        = bool
  default     = true
}

variable "alb_ingress_cidrs" {
  description = "CIDRs allowed to access ALB"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "gateway_container_port" {
  description = "Gateway container port"
  type        = number
  default     = 8080
}

variable "gateway_health_check_path" {
  description = "Gateway target group health check path"
  type        = string
  default     = "/actuator/health"
}

variable "ecs_cluster_container_insights" {
  description = "Enable ECS cluster container insights"
  type        = bool
  default     = true
}

variable "ecs_task_execution_role_name" {
  description = "Optional task execution role name override"
  type        = string
  default     = null
}

variable "ecs_task_role_name" {
  description = "Optional task role name override"
  type        = string
  default     = null
}

variable "ecr_repositories" {
  description = "ECR repository suffix names"
  type        = list(string)
  default = [
    "client-gateway",
    "keycloak",
    "product-service",
    "stock-service",
    "search-service",
    "sales-service",
    "funding-service",
    "hot-deal-service",
    "analytics-dashboard-service"
  ]
}

variable "ecr_image_tag_mutability" {
  description = "ECR image tag mutability"
  type        = string
  default     = "MUTABLE"

  validation {
    condition     = contains(["MUTABLE", "IMMUTABLE"], var.ecr_image_tag_mutability)
    error_message = "ecr_image_tag_mutability must be MUTABLE or IMMUTABLE."
  }
}

variable "ecr_lifecycle_max_image_count" {
  description = "Maximum images to retain per ECR repository"
  type        = number
  default     = 100
}

variable "enable_aurora" {
  description = "Whether to create Aurora PostgreSQL cluster"
  type        = bool
  default     = true
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL engine version"
  type        = string
  default     = "16.11"
}

variable "aurora_database_name" {
  description = "Initial database name for Aurora cluster"
  type        = string
  default     = "commerce"
}

variable "aurora_master_username" {
  description = "Aurora master username"
  type        = string
  default     = "postgres"
}

variable "aurora_master_password" {
  description = "Aurora master password override (leave null to auto-generate)"
  type        = string
  default     = null
  sensitive   = true
}

variable "aurora_instance_class" {
  description = "Aurora instance class for writer/reader"
  type        = string
  default     = "db.t4g.medium"
}

variable "aurora_reader_instance_count" {
  description = "Aurora reader instance count"
  type        = number
  default     = 1
}

variable "aurora_backup_retention_days" {
  description = "Aurora backup retention days"
  type        = number
  default     = 7
}

variable "aurora_preferred_backup_window" {
  description = "Aurora preferred backup window"
  type        = string
  default     = "18:00-19:00"
}

variable "aurora_preferred_maintenance_window" {
  description = "Aurora preferred maintenance window"
  type        = string
  default     = "sun:19:00-sun:20:00"
}

variable "aurora_deletion_protection" {
  description = "Enable Aurora deletion protection"
  type        = bool
  default     = false
}

variable "aurora_apply_immediately" {
  description = "Apply Aurora modifications immediately"
  type        = bool
  default     = true
}

variable "aurora_skip_final_snapshot" {
  description = "Skip final snapshot on cluster destroy"
  type        = bool
  default     = true
}

variable "create_aurora_master_secret" {
  description = "Whether to store Aurora master credential in Secrets Manager"
  type        = bool
  default     = true
}

variable "aurora_master_secret_name" {
  description = "Optional secret name override for Aurora master credential"
  type        = string
  default     = null
}

variable "secret_recovery_window_in_days" {
  description = "Secrets Manager recovery window"
  type        = number
  default     = 0
}

variable "enable_redis" {
  description = "Whether to create ElastiCache Redis replication group"
  type        = bool
  default     = true
}

variable "redis_engine_version" {
  description = "Redis engine version"
  type        = string
  default     = "7.1"
}

variable "redis_node_type" {
  description = "Redis node type"
  type        = string
  default     = "cache.t4g.small"
}

variable "redis_port" {
  description = "Redis port"
  type        = number
  default     = 6379
}

variable "redis_num_cache_clusters" {
  description = "Redis cache node count"
  type        = number
  default     = 1
}

variable "redis_at_rest_encryption_enabled" {
  description = "Enable Redis at-rest encryption"
  type        = bool
  default     = true
}

variable "redis_transit_encryption_enabled" {
  description = "Enable Redis transit encryption"
  type        = bool
  default     = false
}

variable "redis_snapshot_retention_limit" {
  description = "Redis snapshot retention days"
  type        = number
  default     = 1
}

variable "redis_apply_immediately" {
  description = "Apply Redis modifications immediately"
  type        = bool
  default     = true
}

variable "enable_ec2_kafka" {
  description = "Whether to create single-node Kafka on EC2"
  type        = bool
  default     = false
}

variable "ec2_kafka_instance_type" {
  description = "EC2 instance type for single-node Kafka"
  type        = string
  default     = "t3.small"
}

variable "ec2_kafka_ebs_volume_size" {
  description = "Root EBS volume size for Kafka EC2 in GiB"
  type        = number
  default     = 30
}

variable "ec2_kafka_subnet_id" {
  description = "Optional subnet id override for Kafka EC2 (defaults to first private subnet)"
  type        = string
  default     = null
}

variable "ec2_kafka_key_name" {
  description = "Optional EC2 key pair name for Kafka instance"
  type        = string
  default     = null
}

variable "ec2_kafka_ami_id" {
  description = "Optional AMI id override for Kafka EC2"
  type        = string
  default     = null
}

variable "enable_ec2_bastion" {
  description = "Whether to create Bastion EC2 for SSH tunneling from local DB tools"
  type        = bool
  default     = false
}

variable "ec2_bastion_instance_type" {
  description = "EC2 instance type for Bastion host"
  type        = string
  default     = "t3.micro"
}

variable "ec2_bastion_ebs_volume_size" {
  description = "Root EBS volume size for Bastion EC2 in GiB"
  type        = number
  default     = 20
}

variable "ec2_bastion_subnet_id" {
  description = "Optional subnet id override for Bastion EC2 (defaults to first public subnet)"
  type        = string
  default     = null
}

variable "ec2_bastion_key_name" {
  description = "EC2 key pair name for Bastion SSH access"
  type        = string
  default     = null

  validation {
    condition     = !var.enable_ec2_bastion || var.ec2_bastion_key_name != null
    error_message = "ec2_bastion_key_name must be set when enable_ec2_bastion=true."
  }
}

variable "ec2_bastion_ami_id" {
  description = "Optional AMI id override for Bastion EC2"
  type        = string
  default     = null
}

variable "ec2_bastion_ingress_cidrs" {
  description = "CIDRs allowed to SSH into Bastion EC2"
  type        = list(string)
  default     = []

  validation {
    condition     = !var.enable_ec2_bastion || length(var.ec2_bastion_ingress_cidrs) > 0
    error_message = "ec2_bastion_ingress_cidrs must contain at least one CIDR when enable_ec2_bastion=true."
  }
}

variable "enable_ec2_elasticsearch" {
  description = "Whether to create single-node Elasticsearch on EC2"
  type        = bool
  default     = false
}

variable "ec2_elasticsearch_instance_type" {
  description = "EC2 instance type for single-node Elasticsearch"
  type        = string
  default     = "t3.small"
}

variable "ec2_elasticsearch_ebs_volume_size" {
  description = "Root EBS volume size for Elasticsearch EC2 in GiB"
  type        = number
  default     = 30
}

variable "ec2_elasticsearch_heap_size" {
  description = "Elasticsearch JVM heap size, for example 512m or 1g"
  type        = string
  default     = "512m"
}

variable "ec2_elasticsearch_subnet_id" {
  description = "Optional subnet id override for Elasticsearch EC2 (defaults to first private subnet)"
  type        = string
  default     = null
}

variable "ec2_elasticsearch_key_name" {
  description = "Optional EC2 key pair name for Elasticsearch instance"
  type        = string
  default     = null
}

variable "ec2_elasticsearch_ami_id" {
  description = "Optional AMI id override for Elasticsearch EC2"
  type        = string
  default     = null
}

variable "enable_msk" {
  description = "Whether to create Amazon MSK cluster"
  type        = bool
  default     = false
}

variable "msk_kafka_version" {
  description = "MSK Kafka version"
  type        = string
  default     = "3.6.0"
}

variable "msk_broker_node_count" {
  description = "MSK broker node count"
  type        = number
  default     = 2
}

variable "msk_instance_type" {
  description = "MSK broker instance type"
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_ebs_volume_size" {
  description = "MSK broker EBS volume size in GiB"
  type        = number
  default     = 100
}

variable "msk_client_authentication_unauthenticated" {
  description = "Enable unauthenticated client access (dev only)"
  type        = bool
  default     = true
}

variable "msk_enhanced_monitoring" {
  description = "MSK enhanced monitoring level"
  type        = string
  default     = "DEFAULT"
}

variable "enable_opensearch" {
  description = "Whether to create OpenSearch domain"
  type        = bool
  default     = false
}

variable "opensearch_domain_name" {
  description = "Optional OpenSearch domain name override"
  type        = string
  default     = null
}

variable "opensearch_engine_version" {
  description = "OpenSearch engine version"
  type        = string
  default     = "OpenSearch_2.11"
}

variable "opensearch_instance_type" {
  description = "OpenSearch instance type"
  type        = string
  default     = "t3.small.search"
}

variable "opensearch_instance_count" {
  description = "OpenSearch instance count"
  type        = number
  default     = 1
}

variable "opensearch_ebs_volume_size" {
  description = "OpenSearch EBS volume size in GiB"
  type        = number
  default     = 20
}
