resource "aws_cloudwatch_log_group" "service" {
  for_each = var.services

  name              = "/ecs/${var.name_prefix}/${var.environment}/${each.key}"
  retention_in_days = each.value.log_retention_days
  tags              = local.common_tags
}

resource "aws_service_discovery_service" "service" {
  for_each = var.service_discovery_namespace_id == null ? {} : var.services

  name = try(each.value.service_discovery_name, each.key)

  dns_config {
    namespace_id = var.service_discovery_namespace_id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  tags = local.common_tags
}

resource "aws_ecs_task_definition" "service" {
  for_each = var.services

  family                   = "${var.name_prefix}-${var.environment}-${each.key}"
  requires_compatibilities = [upper(each.value.launch_type)]
  network_mode             = "awsvpc"
  cpu                      = tostring(each.value.cpu)
  memory                   = tostring(each.value.memory)
  execution_role_arn       = var.task_execution_role_arn
  task_role_arn            = var.task_role_arn

  dynamic "runtime_platform" {
    for_each = upper(each.value.launch_type) == "FARGATE" ? [1] : []

    content {
      cpu_architecture        = upper(each.value.cpu_architecture)
      operating_system_family = upper(each.value.operating_system_family)
    }
  }

  container_definitions = jsonencode([
    merge(
      {
        name      = each.key
        image     = each.value.image
        essential = true

        portMappings = [
          {
            containerPort = each.value.container_port
            hostPort      = each.value.container_port
            protocol      = "tcp"
          }
        ]

        environment = [
          for key in sort(keys(each.value.environment)) : {
            name  = key
            value = each.value.environment[key]
          }
        ]

        secrets = [
          for key in sort(keys(each.value.secrets)) : {
            name      = key
            valueFrom = each.value.secrets[key]
          }
        ]

        logConfiguration = {
          logDriver = "awslogs"
          options = {
            awslogs-group         = aws_cloudwatch_log_group.service[each.key].name
            awslogs-region        = var.aws_region
            awslogs-stream-prefix = each.key
          }
        }
      },
      length(each.value.command) > 0 ? { command = each.value.command } : {},
      length(each.value.entrypoint) > 0 ? { entryPoint = each.value.entrypoint } : {}
    )
  ])

  tags = local.common_tags
}

resource "aws_ecs_service" "service" {
  for_each = var.services

  name                   = "${var.name_prefix}-${var.environment}-${each.key}"
  cluster                = var.cluster_arn
  task_definition        = aws_ecs_task_definition.service[each.key].arn
  desired_count          = each.value.desired_count
  launch_type            = upper(each.value.launch_type)
  platform_version       = upper(each.value.launch_type) == "FARGATE" ? each.value.platform_version : null
  enable_execute_command = each.value.enable_execute_command

  deployment_minimum_healthy_percent = each.value.deployment_minimum_healthy_percent
  deployment_maximum_percent         = each.value.deployment_maximum_percent
  health_check_grace_period_seconds  = try(each.value.target_group_arn, null) != null ? each.value.health_check_grace_period_seconds : null
  force_new_deployment               = true
  wait_for_steady_state              = false
  propagate_tags                     = "SERVICE"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = each.value.assign_public_ip
  }

  dynamic "load_balancer" {
    for_each = try(each.value.target_group_arn, null) == null ? [] : [each.value.target_group_arn]

    content {
      target_group_arn = load_balancer.value
      container_name   = each.key
      container_port   = each.value.container_port
    }
  }

  dynamic "service_registries" {
    for_each = var.service_discovery_namespace_id == null ? [] : [1]

    content {
      registry_arn = aws_service_discovery_service.service[each.key].arn
    }
  }

  tags = local.common_tags
}
