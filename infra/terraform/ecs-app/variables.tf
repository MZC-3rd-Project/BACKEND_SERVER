variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
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

variable "cluster_arn" {
  description = "Existing ECS cluster ARN"
  type        = string
}

variable "task_execution_role_arn" {
  description = "ECS task execution role ARN"
  type        = string
}

variable "task_role_arn" {
  description = "ECS task role ARN"
  type        = string
}

variable "subnet_ids" {
  description = "Subnets used by ECS services"
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups attached to ECS services"
  type        = list(string)
}

variable "service_discovery_namespace_id" {
  description = "Cloud Map private DNS namespace id for ECS service discovery"
  type        = string
  default     = null
}

variable "default_tags" {
  description = "Additional tags"
  type        = map(string)
  default     = {}
}

variable "services" {
  description = "ECS service definitions"
  type = map(object({
    image                              = string
    cpu                                = number
    memory                             = number
    container_port                     = number
    desired_count                      = optional(number, 1)
    launch_type                        = optional(string, "FARGATE")
    assign_public_ip                   = optional(bool, false)
    enable_execute_command             = optional(bool, true)
    platform_version                   = optional(string, "1.4.0")
    deployment_minimum_healthy_percent = optional(number, 50)
    deployment_maximum_percent         = optional(number, 200)
    health_check_grace_period_seconds  = optional(number, 30)
    target_group_arn                   = optional(string)
    command                            = optional(list(string), [])
    entrypoint                         = optional(list(string), [])
    environment                        = optional(map(string), {})
    secrets                            = optional(map(string), {})
    service_discovery_name             = optional(string)
    log_retention_days                 = optional(number, 14)
  }))
  default = {}

  validation {
    condition = alltrue([
      for svc in values(var.services) : contains(["FARGATE", "EC2"], upper(svc.launch_type))
    ])
    error_message = "service.launch_type must be FARGATE or EC2."
  }
}
