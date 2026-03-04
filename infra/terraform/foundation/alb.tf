resource "aws_lb" "this" {
  count = var.create_alb ? 1 : 0

  name               = substr(replace("${var.name_prefix}-${var.environment}-alb", "_", "-"), 0, 32)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [for s in values(aws_subnet.public) : s.id]

  idle_timeout = 60

  tags = local.common_tags
}

resource "aws_lb_target_group" "gateway" {
  count = var.create_alb ? 1 : 0

  name        = substr(replace("${var.name_prefix}-${var.environment}-gw-tg", "_", "-"), 0, 32)
  port        = var.gateway_container_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.this.id

  health_check {
    enabled             = true
    path                = var.gateway_health_check_path
    protocol            = "HTTP"
    matcher             = "200-399"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 15
  }

  tags = local.common_tags
}

resource "aws_lb_listener" "http" {
  count = var.create_alb ? 1 : 0

  load_balancer_arn = aws_lb.this[0].arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway[0].arn
  }

  tags = local.common_tags
}
