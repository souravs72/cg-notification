# SNS topics and SQS queues (replacing MSK/Kafka)

resource "aws_sns_topic" "email" {
  name = "${var.app_name}-notifications-email"
  tags = { Name = "${var.app_name}-notifications-email" }
}

resource "aws_sns_topic" "whatsapp" {
  name = "${var.app_name}-notifications-whatsapp"
  tags = { Name = "${var.app_name}-notifications-whatsapp" }
}

resource "aws_sqs_queue" "email_dlq" {
  name                      = "${var.app_name}-notifications-email-dlq"
  message_retention_seconds = 1209600 # 14 days
  tags                      = { Name = "${var.app_name}-notifications-email-dlq" }
}

resource "aws_sqs_queue" "whatsapp_dlq" {
  name                      = "${var.app_name}-notifications-whatsapp-dlq"
  message_retention_seconds = 1209600
  tags                      = { Name = "${var.app_name}-notifications-whatsapp-dlq" }
}

resource "aws_sqs_queue" "email" {
  name                       = "${var.app_name}-notifications-email-queue"
  visibility_timeout_seconds = 120
  message_retention_seconds  = 345600 # 4 days
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.email_dlq.arn
    maxReceiveCount     = 3
  })
  tags = { Name = "${var.app_name}-notifications-email-queue" }
}

resource "aws_sqs_queue" "whatsapp" {
  name                       = "${var.app_name}-notifications-whatsapp-queue"
  visibility_timeout_seconds = 120
  message_retention_seconds  = 345600
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.whatsapp_dlq.arn
    maxReceiveCount     = 3
  })
  tags = { Name = "${var.app_name}-notifications-whatsapp-queue" }
}

resource "aws_sqs_queue_redrive_allow_policy" "email_dlq" {
  queue_url = aws_sqs_queue.email_dlq.id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.email.arn]
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "whatsapp_dlq" {
  queue_url = aws_sqs_queue.whatsapp_dlq.id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.whatsapp.arn]
  })
}

resource "aws_sns_topic_subscription" "email_queue" {
  topic_arn                       = aws_sns_topic.email.arn
  protocol                        = "sqs"
  endpoint                        = aws_sqs_queue.email.arn
  raw_message_delivery            = true # SQS receives raw JSON payload, not SNS envelope
}

resource "aws_sns_topic_subscription" "whatsapp_queue" {
  topic_arn                       = aws_sns_topic.whatsapp.arn
  protocol                        = "sqs"
  endpoint                        = aws_sqs_queue.whatsapp.arn
  raw_message_delivery            = true
}

resource "aws_sqs_queue_policy" "email" {
  queue_url = aws_sqs_queue.email.id
  policy    = data.aws_iam_policy_document.sns_to_sqs_email.json
}

data "aws_iam_policy_document" "sns_to_sqs_email" {
  statement {
    sid    = "AllowSNS"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.email.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.email.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "whatsapp" {
  queue_url = aws_sqs_queue.whatsapp.id
  policy    = data.aws_iam_policy_document.sns_to_sqs_whatsapp.json
}

data "aws_iam_policy_document" "sns_to_sqs_whatsapp" {
  statement {
    sid    = "AllowSNS"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.whatsapp.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.whatsapp.arn]
    }
  }
}
