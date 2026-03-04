resource "random_password" "aurora_master" {
  count = var.enable_aurora && var.aurora_master_password == null ? 1 : 0

  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_db_subnet_group" "aurora" {
  count = var.enable_aurora ? 1 : 0

  name       = "${var.name_prefix}-${var.environment}-aurora-subnet-group"
  subnet_ids = [for s in values(aws_subnet.private) : s.id]

  tags = local.common_tags
}

resource "aws_rds_cluster" "aurora" {
  count = var.enable_aurora ? 1 : 0

  cluster_identifier              = "${var.name_prefix}-${var.environment}-aurora"
  engine                          = "aurora-postgresql"
  engine_version                  = var.aurora_engine_version
  database_name                   = var.aurora_database_name
  master_username                 = var.aurora_master_username
  master_password                 = local.aurora_master_password
  db_subnet_group_name            = aws_db_subnet_group.aurora[0].name
  vpc_security_group_ids          = [aws_security_group.db.id]
  storage_encrypted               = true
  backup_retention_period         = var.aurora_backup_retention_days
  preferred_backup_window         = var.aurora_preferred_backup_window
  preferred_maintenance_window    = var.aurora_preferred_maintenance_window
  deletion_protection             = var.aurora_deletion_protection
  apply_immediately               = var.aurora_apply_immediately
  skip_final_snapshot             = var.aurora_skip_final_snapshot
  copy_tags_to_snapshot           = true
  enabled_cloudwatch_logs_exports = ["postgresql"]

  tags = local.common_tags
}

resource "aws_rds_cluster_instance" "writer" {
  count = var.enable_aurora ? 1 : 0

  identifier                 = "${var.name_prefix}-${var.environment}-aurora-writer"
  cluster_identifier         = aws_rds_cluster.aurora[0].id
  instance_class             = var.aurora_instance_class
  engine                     = aws_rds_cluster.aurora[0].engine
  engine_version             = aws_rds_cluster.aurora[0].engine_version
  publicly_accessible        = false
  auto_minor_version_upgrade = true
  apply_immediately          = var.aurora_apply_immediately

  tags = local.common_tags
}

resource "aws_rds_cluster_instance" "reader" {
  count = var.enable_aurora ? var.aurora_reader_instance_count : 0

  identifier                 = "${var.name_prefix}-${var.environment}-aurora-reader-${count.index + 1}"
  cluster_identifier         = aws_rds_cluster.aurora[0].id
  instance_class             = var.aurora_instance_class
  engine                     = aws_rds_cluster.aurora[0].engine
  engine_version             = aws_rds_cluster.aurora[0].engine_version
  promotion_tier             = 15
  publicly_accessible        = false
  auto_minor_version_upgrade = true
  apply_immediately          = var.aurora_apply_immediately

  tags = local.common_tags
}

resource "aws_secretsmanager_secret" "aurora_master" {
  count = var.enable_aurora && var.create_aurora_master_secret ? 1 : 0

  name                    = coalesce(var.aurora_master_secret_name, "${var.name_prefix}/${var.environment}/aurora/master")
  recovery_window_in_days = var.secret_recovery_window_in_days

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "aurora_master" {
  count = var.enable_aurora && var.create_aurora_master_secret ? 1 : 0

  secret_id = aws_secretsmanager_secret.aurora_master[0].id
  secret_string = jsonencode({
    username = var.aurora_master_username,
    password = local.aurora_master_password,
    engine   = "postgresql",
    host     = aws_rds_cluster.aurora[0].endpoint,
    port     = aws_rds_cluster.aurora[0].port,
    dbname   = var.aurora_database_name
  })
}
