# S3 bucket for ALB access logs
resource "aws_s3_bucket" "alb_logs" {
  bucket = "cg-notification-alb-logs-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "cg-notification-alb-logs"
  }
}

# CRITICAL: ALB access logs still rely on object ACL = bucket-owner-full-control in practice.
# Using BucketOwnerPreferred keeps the bucket owner as the object owner while still allowing
# the required ACL header.
resource "aws_s3_bucket_ownership_controls" "alb_logs" {
  bucket = aws_s3_bucket.alb_logs.id

  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

# CRITICAL: Block all public access explicitly (required for security scanners & audits)
resource "aws_s3_bucket_public_access_block" "alb_logs" {
  bucket = aws_s3_bucket.alb_logs.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# Lifecycle policy for ALB logs (ALB logs grow fast - prevents unbounded cost growth)
resource "aws_s3_bucket_lifecycle_configuration" "alb_logs" {
  bucket = aws_s3_bucket.alb_logs.id

  rule {
    id     = "DeleteOldLogs"
    status = "Enabled"

    filter {
      prefix = ""
    }

    expiration {
      days = 90 # Retain logs for 90 days (good balance between cost and forensics)
    }
  }
}

# Encryption for ALB logs bucket (required for audits)
# ALB does not support SSE-KMS for log delivery — AES256 is the correct choice
resource "aws_s3_bucket_server_side_encryption_configuration" "alb_logs" {
  bucket = aws_s3_bucket.alb_logs.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Bucket policy for ALB logging
# CRITICAL:
# - Require the ACL header that ALB uses: bucket-owner-full-control
# - ALB log delivery differs across regions/accounts; allow both modern and legacy principals.
resource "aws_s3_bucket_policy" "alb_logs" {
  bucket = aws_s3_bucket.alb_logs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "logdelivery.elasticloadbalancing.amazonaws.com"
        }
        Action = "s3:PutObject"
        # Must match ALB access_logs.prefix = "alb-access-logs"
        Resource = "${aws_s3_bucket.alb_logs.arn}/alb-access-logs/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
        Condition = {
          StringEquals = {
            "s3:x-amz-acl" = "bucket-owner-full-control"
          }
        }
      },
      {
        Effect = "Allow"
        Principal = {
          Service = "logdelivery.elasticloadbalancing.amazonaws.com"
        }
        Action   = "s3:GetBucketAcl"
        Resource = aws_s3_bucket.alb_logs.arn
      },
      # Legacy log delivery principal used in some AWS-managed delivery flows.
      {
        Effect = "Allow"
        Principal = {
          Service = "delivery.logs.amazonaws.com"
        }
        Action   = "s3:PutObject"
        Resource = "${aws_s3_bucket.alb_logs.arn}/alb-access-logs/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
        Condition = {
          StringEquals = {
            "s3:x-amz-acl" = "bucket-owner-full-control"
          }
        }
      },
      {
        Effect = "Allow"
        Principal = {
          Service = "delivery.logs.amazonaws.com"
        }
        Action   = "s3:GetBucketAcl"
        Resource = aws_s3_bucket.alb_logs.arn
      },
      # Regional ELB account (ap-south-1 = 127311923021) can be required for ALB access logs.
      # Keep it without ACL requirements (BucketOwnerEnforced).
      {
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::127311923021:root"
        }
        Action   = "s3:PutObject"
        Resource = "${aws_s3_bucket.alb_logs.arn}/alb-access-logs/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
        Condition = {
          StringEquals = {
            "s3:x-amz-acl" = "bucket-owner-full-control"
          }
        }
      },
      {
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::127311923021:root"
        }
        Action   = "s3:GetBucketAcl"
        Resource = aws_s3_bucket.alb_logs.arn
      }
    ]
  })
}

# Application Load Balancer
resource "aws_lb" "main" {
  name               = var.alb_name
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection       = var.alb_deletion_protection
  idle_timeout                     = 120
  enable_cross_zone_load_balancing = true # Explicitly enable cross-AZ load balancing (ALB default, but explicit helps future readers)

  # Enable access logging on ALB (required for security forensics, WAF tuning, incident response)
  # CRITICAL: Must wait for bucket policy to be applied before enabling access logs
  # Using depends_on ensures bucket policy is ready before ALB tries to write logs
  depends_on = [
    aws_s3_bucket_policy.alb_logs,
    aws_s3_bucket_ownership_controls.alb_logs,
  ]

  access_logs {
    bucket  = aws_s3_bucket.alb_logs.id
    prefix  = "alb-access-logs"
    enabled = true
  }

  tags = {
    Name = var.alb_name
  }
}

# Target Group for notification-api
resource "aws_lb_target_group" "api" {
  name        = "cg-notification-api-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 5 # Increased for Spring Boot apps that may take time to become ready
    timeout             = 5
    interval            = 30
    path                = "/actuator/health/liveness" # Use liveness endpoint - checks if process is alive, not if all dependencies are ready
    protocol            = "HTTP"
    matcher             = "200"
  }

  # Configure deregistration delay (smooths rolling deployments, prevents in-flight request drops)
  deregistration_delay = 30

  tags = {
    Name = "cg-notification-api-tg"
  }
}


# HTTP Listener - redirects to HTTPS if certificate provided, otherwise forwards
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = "80"
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = (var.acm_certificate_arn != "" || (var.domain_name != "" && var.route53_zone_id != "")) ? [1] : []
    content {
      type = "redirect"
      redirect {
        protocol    = "HTTPS"
        port        = "443"
        status_code = "HTTP_301"
      }
    }
  }

  dynamic "default_action" {
    # When no valid HTTPS cert is wired up, keep HTTP forwarding directly to the target group
    for_each = (var.acm_certificate_arn == "" && (var.domain_name == "" || var.route53_zone_id == "")) ? [1] : []
    content {
      type             = "forward"
      target_group_arn = aws_lb_target_group.api.arn
    }
  }
}

# HTTPS Listener
# Only create when we have a usable certificate at apply-time:
# - user supplied an existing ACM cert ARN, OR
# - we can auto-validate via Route53 in the same apply (domain_name + route53_zone_id)
# If domain_name is provided without route53_zone_id, ACM will be PENDING_VALIDATION and
# AWS rejects the listener with UnsupportedCertificate. In that case we keep HTTP only
# and output the DNS validation records for manual setup.
resource "aws_lb_listener" "https" {
  count             = (var.acm_certificate_arn != "" || (var.domain_name != "" && var.route53_zone_id != "")) ? 1 : 0
  load_balancer_arn = aws_lb.main.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = (var.domain_name != "" && var.route53_zone_id != "") ? aws_acm_certificate_validation.main[0].certificate_arn : var.acm_certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

# ECS api service waits for HTTP listener (and HTTPS when ACM cert set via same apply).
# depends_on requires a static list; HTTPS listener is conditional so we only reference HTTP.
resource "null_resource" "alb_listeners" {
  depends_on = [aws_lb_listener.http]
}

