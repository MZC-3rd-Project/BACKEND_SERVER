locals {
  search_ai_enrichment_queue_name  = coalesce(var.search_ai_enrichment_queue_name, "${var.name_prefix}-${var.environment}-search-ai-enrichment")
  search_ai_enrichment_dlq_name    = coalesce(var.search_ai_enrichment_dlq_name, "${var.name_prefix}-${var.environment}-search-ai-enrichment-dlq")
  search_ai_enrichment_lambda_name = coalesce(var.search_ai_enrichment_lambda_name, "${var.name_prefix}-${var.environment}-search-ai-enricher")
  search_ai_enrichment_secret_name = coalesce(var.search_ai_enrichment_secret_name, "${var.name_prefix}/${var.environment}/search-ai-enrichment")
  search_ai_enrichment_lambda_zip  = "${path.module}/.terraform/search-ai-enricher.zip"
  search_ai_enrichment_es_url = coalesce(
    var.search_ai_enrichment_elasticsearch_url,
    try("http://${aws_instance.ec2_elasticsearch[0].private_ip}:9200", null)
  )
}

data "archive_file" "search_ai_enricher" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  type        = "zip"
  source_dir  = "${path.module}/../../../lambda/search-ai-enricher"
  output_path = local.search_ai_enrichment_lambda_zip
}

data "aws_iam_policy_document" "search_ai_enricher_assume_role" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_security_group" "search_ai_enricher_lambda" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  name        = "${var.name_prefix}-${var.environment}-search-ai-enricher-lambda-sg"
  description = "Lambda security group for search AI enrichment"
  vpc_id      = aws_vpc.this.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.name_prefix}-${var.environment}-search-ai-enricher-lambda-sg"
  })
}

resource "aws_security_group_rule" "ec2_elasticsearch_from_search_ai_lambda" {
  count = var.enable_search_ai_enrichment && var.enable_ec2_elasticsearch ? 1 : 0

  type                     = "ingress"
  from_port                = 9200
  to_port                  = 9200
  protocol                 = "tcp"
  security_group_id        = aws_security_group.ec2_elasticsearch[0].id
  source_security_group_id = aws_security_group.search_ai_enricher_lambda[0].id
  description              = "Elasticsearch HTTP from search AI enrichment Lambda"
}

resource "aws_sqs_queue" "search_ai_enrichment_dlq" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  name                      = local.search_ai_enrichment_dlq_name
  message_retention_seconds = var.search_ai_enrichment_queue_message_retention_seconds

  tags = merge(local.common_tags, {
    Name = local.search_ai_enrichment_dlq_name
  })
}

resource "aws_sqs_queue" "search_ai_enrichment" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  name                       = local.search_ai_enrichment_queue_name
  visibility_timeout_seconds = var.search_ai_enrichment_queue_visibility_timeout
  message_retention_seconds  = var.search_ai_enrichment_queue_message_retention_seconds
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.search_ai_enrichment_dlq[0].arn
    maxReceiveCount     = var.search_ai_enrichment_queue_max_receive_count
  })

  tags = merge(local.common_tags, {
    Name = local.search_ai_enrichment_queue_name
  })
}

resource "aws_secretsmanager_secret" "search_ai_enrichment" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  name = local.search_ai_enrichment_secret_name

  tags = merge(local.common_tags, {
    Name = local.search_ai_enrichment_secret_name
  })
}

resource "aws_secretsmanager_secret_version" "search_ai_enrichment" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  secret_id = aws_secretsmanager_secret.search_ai_enrichment[0].id
  secret_string = jsonencode({
    queue_url  = aws_sqs_queue.search_ai_enrichment[0].url
    queue_arn  = aws_sqs_queue.search_ai_enrichment[0].arn
    index_name = var.search_ai_enrichment_index_name
    model_id   = var.search_ai_enrichment_bedrock_model_id
    aws_region = var.search_ai_enrichment_bedrock_region
  })
}

resource "aws_iam_role" "search_ai_enricher_lambda" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  name               = local.search_ai_enrichment_lambda_name
  assume_role_policy = data.aws_iam_policy_document.search_ai_enricher_assume_role[0].json

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "search_ai_enricher_lambda_basic" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  role       = aws_iam_role.search_ai_enricher_lambda[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "search_ai_enricher_lambda_vpc" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  role       = aws_iam_role.search_ai_enricher_lambda[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "search_ai_enricher_lambda" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  statement {
    sid = "ConsumeQueue"

    actions = [
      "sqs:ChangeMessageVisibility",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:ReceiveMessage"
    ]

    resources = [aws_sqs_queue.search_ai_enrichment[0].arn]
  }

  statement {
    sid = "InvokeBedrockModel"

    actions = [
      "bedrock:InvokeModel"
    ]

    resources = [
      "arn:aws:bedrock:${var.search_ai_enrichment_bedrock_region}::foundation-model/${var.search_ai_enrichment_bedrock_model_id}"
    ]
  }
}

resource "aws_iam_policy" "search_ai_enricher_lambda" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  name        = "${local.search_ai_enrichment_lambda_name}-policy"
  description = "Permissions for the search AI enrichment Lambda"
  policy      = data.aws_iam_policy_document.search_ai_enricher_lambda[0].json

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "search_ai_enricher_lambda" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  role       = aws_iam_role.search_ai_enricher_lambda[0].name
  policy_arn = aws_iam_policy.search_ai_enricher_lambda[0].arn
}

resource "aws_cloudwatch_log_group" "search_ai_enricher_lambda" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  name              = "/aws/lambda/${local.search_ai_enrichment_lambda_name}"
  retention_in_days = var.search_ai_enrichment_log_retention_days

  tags = local.common_tags
}

resource "aws_lambda_function" "search_ai_enricher" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  function_name    = local.search_ai_enrichment_lambda_name
  role             = aws_iam_role.search_ai_enricher_lambda[0].arn
  filename         = data.archive_file.search_ai_enricher[0].output_path
  source_code_hash = data.archive_file.search_ai_enricher[0].output_base64sha256
  handler          = "lambda_function.handler"
  runtime          = var.search_ai_enrichment_lambda_runtime
  timeout          = var.search_ai_enrichment_lambda_timeout
  memory_size      = var.search_ai_enrichment_lambda_memory_size

  vpc_config {
    subnet_ids         = [for subnet in values(aws_subnet.private) : subnet.id]
    security_group_ids = [aws_security_group.search_ai_enricher_lambda[0].id]
  }

  environment {
    variables = {
      SEARCH_ENRICHMENT_TARGET = "elasticsearch"
      SEARCH_ELASTICSEARCH_URL = coalesce(local.search_ai_enrichment_es_url, "")
      SEARCH_INDEX_NAME        = var.search_ai_enrichment_index_name
      BEDROCK_REGION           = var.search_ai_enrichment_bedrock_region
      BEDROCK_MODEL_ID         = var.search_ai_enrichment_bedrock_model_id
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.search_ai_enricher_lambda,
    aws_iam_role_policy_attachment.search_ai_enricher_lambda_basic,
    aws_iam_role_policy_attachment.search_ai_enricher_lambda_vpc,
    aws_iam_role_policy_attachment.search_ai_enricher_lambda
  ]

  lifecycle {
    precondition {
      condition     = local.search_ai_enrichment_es_url != null
      error_message = "search_ai_enrichment requires search_ai_enrichment_elasticsearch_url or enable_ec2_elasticsearch=true."
    }
  }

  tags = local.common_tags
}

resource "aws_lambda_event_source_mapping" "search_ai_enricher" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  event_source_arn                   = aws_sqs_queue.search_ai_enrichment[0].arn
  function_name                      = aws_lambda_function.search_ai_enricher[0].arn
  batch_size                         = 10
  function_response_types            = ["ReportBatchItemFailures"]
  maximum_batching_window_in_seconds = 5
}
