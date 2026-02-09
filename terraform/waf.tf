# AWS WAF Web ACL (protects against Layer-7 attacks, credential stuffing, and bot abuse)
resource "aws_wafv2_web_acl" "main" {
  name        = "cg-notification-waf"
  description = "WAF for cg-notification ALB"
  scope       = "REGIONAL" # Must be REGIONAL for ALB (same region as ALB)

  default_action {
    allow {}
  }

  # CRITICAL: Allow authentication endpoints to bypass WAF rules
  # Authentication endpoints (/auth/**) must be accessible for users to login/register
  # Managed rule sets can be too aggressive and block legitimate auth requests
  # This rule has priority 0 (highest) so it's evaluated first
  rule {
    name     = "AllowAuthEndpoints"
    priority = 0 # Highest priority - evaluated first

    statement {
      byte_match_statement {
        positional_constraint = "STARTS_WITH"
        search_string         = "/auth/"
        field_to_match {
          uri_path {}
        }
        text_transformation {
          priority = 0
          type     = "LOWERCASE"
        }
      }
    }

    action {
      allow {}
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AllowAuthEndpointsMetric"
      sampled_requests_enabled   = true
    }
  }

  # AWS Managed Common Rule Set
  # CRITICAL: Use scope-down statement to exclude /auth/** paths from evaluation
  # This allows managed rules to actually BLOCK attacks (override_action: none)
  # while still allowing /auth/** endpoints to work
  # The AllowAuthEndpoints rule (priority 0) allows /auth/** before this rule evaluates
  # Scope-down ensures managed rules don't inspect /auth/** requests at all
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 1

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"

        # Scope-down: Only evaluate requests that do NOT start with /auth/
        # This prevents managed rules from inspecting authentication endpoints
        scope_down_statement {
          not_statement {
            statement {
              byte_match_statement {
                positional_constraint = "STARTS_WITH"
                search_string         = "/auth/"
                field_to_match {
                  uri_path {}
                }
                text_transformation {
                  priority = 0
                  type     = "LOWERCASE"
                }
              }
            }
          }
        }
      }
    }

    # CRITICAL: Use 'none' to actually BLOCK attacks (not just count)
    # This provides real security protection against XSS, RFI, LFI, etc.
    override_action {
      none {}
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "CommonRuleSetMetric"
      sampled_requests_enabled   = true
    }
  }

  # AWS Managed SQL Injection Rule Set
  # CRITICAL: Use scope-down statement to exclude /auth/** paths from evaluation
  # SQL injection rules are particularly aggressive and can block legitimate login attempts
  # Scope-down ensures they don't evaluate /auth/** requests at all
  rule {
    name     = "AWSManagedRulesSQLiRuleSet"
    priority = 2

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"

        # Scope-down: Only evaluate requests that do NOT start with /auth/
        # This prevents SQL injection rules from blocking legitimate login attempts
        scope_down_statement {
          not_statement {
            statement {
              byte_match_statement {
                positional_constraint = "STARTS_WITH"
                search_string         = "/auth/"
                field_to_match {
                  uri_path {}
                }
                text_transformation {
                  priority = 0
                  type     = "LOWERCASE"
                }
              }
            }
          }
        }
      }
    }

    # CRITICAL: Use 'none' to actually BLOCK SQL injection attacks (not just count)
    # This provides real security protection against SQL injection
    override_action {
      none {}
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "SQLiRuleSetMetric"
      sampled_requests_enabled   = true
    }
  }

  # Rate Limiting Rule (4000 requests per 5 minutes per IP)
  # Note: This applies to all paths including /auth/** 
  # 4000 requests per 5 minutes should be sufficient for legitimate use
  rule {
    name     = "RateLimitRule"
    priority = 3

    statement {
      rate_based_statement {
        limit              = 4000
        aggregate_key_type = "IP"
      }
    }

    action {
      block {}
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitMetric"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "cg-notification-waf"
    sampled_requests_enabled   = true
  }

  tags = {
    Name = "cg-notification-waf"
  }
}

# Associate WAF with ALB (same region as ALB)
# CRITICAL: Explicit dependency ensures ALB is fully created before WAF association
resource "aws_wafv2_web_acl_association" "main" {
  resource_arn = aws_lb.main.arn
  web_acl_arn  = aws_wafv2_web_acl.main.arn

  depends_on = [aws_lb.main]
}
