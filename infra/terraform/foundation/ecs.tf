resource "aws_ecs_cluster" "this" {
  name = "${var.name_prefix}-${var.environment}"

  setting {
    name  = "containerInsights"
    value = var.ecs_cluster_container_insights ? "enabled" : "disabled"
  }

  tags = local.common_tags
}

data "aws_iam_policy_document" "ecs_task_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name = coalesce(
    var.ecs_task_execution_role_name,
    "${var.name_prefix}-${var.environment}-ecs-task-execution-role"
  )

  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "task_execution_extra" {
  name = "${var.name_prefix}-${var.environment}-ecs-task-execution-extra"
  role = aws_iam_role.task_execution.id

  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "secretsmanager:GetSecretValue",
          "kms:Decrypt"
        ],
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role" "task" {
  name = coalesce(
    var.ecs_task_role_name,
    "${var.name_prefix}-${var.environment}-ecs-task-role"
  )

  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json
  tags               = local.common_tags
}
