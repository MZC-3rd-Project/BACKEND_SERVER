output "service_names" {
  value = {
    for name, svc in aws_ecs_service.service : name => svc.name
  }
}

output "service_arns" {
  value = {
    for name, svc in aws_ecs_service.service : name => svc.id
  }
}

output "task_definition_arns" {
  value = {
    for name, td in aws_ecs_task_definition.service : name => td.arn
  }
}

output "log_group_names" {
  value = {
    for name, lg in aws_cloudwatch_log_group.service : name => lg.name
  }
}
