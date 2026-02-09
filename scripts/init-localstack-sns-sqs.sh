#!/usr/bin/env bash
# Create SNS topics and SQS queues in LocalStack for local Docker development.
# Usage: run once after LocalStack is up, or use as init container.
# Requires: aws CLI, endpoint URL (e.g. http://localstack:4566)

set -e
ENDPOINT="${AWS_ENDPOINT_URL:-http://localhost:4566}"
# Names match application.yml defaults (notifications-email, notifications-email-queue, etc.)
PREFIX="${MESSAGING_PREFIX:-}"

echo "Creating SNS/SQS resources in LocalStack at $ENDPOINT..."

aws --endpoint-url="$ENDPOINT" sns create-topic --name "notifications-email" 2>/dev/null || true
aws --endpoint-url="$ENDPOINT" sns create-topic --name "notifications-whatsapp" 2>/dev/null || true

aws --endpoint-url="$ENDPOINT" sqs create-queue --queue-name "notifications-email-dlq" 2>/dev/null || true
aws --endpoint-url="$ENDPOINT" sqs create-queue --queue-name "notifications-whatsapp-dlq" 2>/dev/null || true
aws --endpoint-url="$ENDPOINT" sqs create-queue --queue-name "notifications-email-queue" 2>/dev/null || true
aws --endpoint-url="$ENDPOINT" sqs create-queue --queue-name "notifications-whatsapp-queue" 2>/dev/null || true

# Subscribe SNS to SQS (LocalStack account id is 000000000000, region us-east-1 by default)
TOPIC_EMAIL_ARN="arn:aws:sns:us-east-1:000000000000:notifications-email"
TOPIC_WHATSAPP_ARN="arn:aws:sns:us-east-1:000000000000:notifications-whatsapp"
QUEUE_EMAIL_ARN="arn:aws:sqs:us-east-1:000000000000:notifications-email-queue"
QUEUE_WHATSAPP_ARN="arn:aws:sqs:us-east-1:000000000000:notifications-whatsapp-queue"

aws --endpoint-url="$ENDPOINT" sns subscribe --topic-arn "$TOPIC_EMAIL_ARN" --protocol sqs --notification-endpoint "$QUEUE_EMAIL_ARN" --attributes '{"RawMessageDelivery":"true"}' 2>/dev/null || true
aws --endpoint-url="$ENDPOINT" sns subscribe --topic-arn "$TOPIC_WHATSAPP_ARN" --protocol sqs --notification-endpoint "$QUEUE_WHATSAPP_ARN" --attributes '{"RawMessageDelivery":"true"}' 2>/dev/null || true

# SQS policy to allow SNS to send (LocalStack may apply automatically; set if needed)
echo "Done. SNS topics, SQS queues, and subscriptions created (or already exist)."
