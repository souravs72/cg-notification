# ACM Certificate for custom domain
resource "aws_acm_certificate" "main" {
  count            = var.domain_name != "" ? 1 : 0
  domain_name      = var.domain_name
  validation_method = "DNS"

  # Request certificate for both www and non-www
  subject_alternative_names = var.domain_name != "" ? ["www.${var.domain_name}"] : []

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "cg-notification-${var.domain_name}"
  }
}

# Route53 hosted zone data source (only if zone_id is provided)
data "aws_route53_zone" "main" {
  count   = var.domain_name != "" && var.route53_zone_id != "" ? 1 : 0
  zone_id = var.route53_zone_id
}

# Route53 records for certificate validation (only if zone_id is provided)
resource "aws_route53_record" "cert_validation" {
  for_each = var.domain_name != "" && var.route53_zone_id != "" ? {
    for dvo in aws_acm_certificate.main[0].domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  } : {}

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.main[0].zone_id
}

# DNS validation (only if Route53 zone_id is provided)
resource "aws_acm_certificate_validation" "main" {
  count           = var.domain_name != "" && var.route53_zone_id != "" ? 1 : 0
  certificate_arn = aws_acm_certificate.main[0].arn
  validation_record_fqdns = [
    for record in aws_route53_record.cert_validation : record.fqdn
  ]

  timeouts {
    create = "5m"
  }
}

# Route53 A record pointing to ALB (only if zone_id is provided)
resource "aws_route53_record" "alb" {
  count   = var.domain_name != "" && var.route53_zone_id != "" ? 1 : 0
  zone_id = data.aws_route53_zone.main[0].zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}

# Route53 A record for www subdomain (only if zone_id is provided)
resource "aws_route53_record" "alb_www" {
  count   = var.domain_name != "" && var.route53_zone_id != "" ? 1 : 0
  zone_id = data.aws_route53_zone.main[0].zone_id
  name    = "www.${var.domain_name}"
  type    = "A"

  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}
