resource "aws_ecr_repository" "this" {
  for_each = local.ecr_repository_names

  name                 = "${var.name_prefix}-${var.environment}-${each.value}"
  image_tag_mutability = var.ecr_image_tag_mutability

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = local.common_tags
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1,
        description  = "Retain last ${var.ecr_lifecycle_max_image_count} images",
        selection = {
          tagStatus   = "any",
          countType   = "imageCountMoreThan",
          countNumber = var.ecr_lifecycle_max_image_count
        },
        action = {
          type = "expire"
        }
      }
    ]
  })
}
