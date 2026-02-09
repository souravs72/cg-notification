# MSK to SNS/SQS Migration Guide

**Production-ready guide to replace Apache Kafka (MSK) with AWS SNS + SQS in the cg-notification system.**

---

## Table of Contents

1. [Target Architecture](#1-target-architecture)
2. [Kafka to SNS/SQS Mapping](#2-kafka-to-snssqs-mapping)
3. [Spring Boot Application Changes](#3-spring-boot-application-changes)
4. [Throughput, Scaling, and Backpressure](#4-throughput-scaling-and-backpressure)
5. [Error Handling and Retries](#5-error-handling-and-retries)
6. [Infrastructure Setup](#6-infrastructure-setup)
7. [CloudWatch Cost Optimization](#7-cloudwatch-cost-optimization)
8. [Migration Strategy](#8-migration-strategy)
9. [Cost Comparison](#9-cost-comparison)

---

## 1. Target Architecture

### 1.1 High-Level AWS Architecture

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                     AWS Region                          │
                    │                                                          │
  Clients ──► API   │   ┌──────────────┐     ┌─────────────────────────────┐  │
  (REST)     (ECS)  │   │ SNS Topic    │     │ SQS Queue                  │  │
                    │   │ notifications-│────►│ notifications-email-queue  │──┼──► email-worker (ECS)
                    │   │ email        │     │ (Standard)                  │  │
                    │   └──────────────┘     └─────────────────────────────┘  │
                    │          │                          │                    │
                    │          │              ┌───────────▼──────────┐         │
                    │          │              │ notifications-email-dlq        │
                    │          │              └─────────────────────┘         │
                    │   ┌──────────────┐     ┌─────────────────────────────┐  │
                    │   │ SNS Topic    │     │ SQS Queue                  │  │
                    │   │ notifications-│────►│ notifications-whatsapp-    │──┼──► whatsapp-worker (ECS)
                    │   │ whatsapp     │     │ queue (Standard or FIFO)   │  │
                    │   └──────────────┘     └─────────────────────────────┘  │
                    │                                  │                       │
                    │                      ┌───────────▼──────────┐            │
                    │                      │ notifications-whatsapp-dlq       │
                    │                      └─────────────────────┘            │
                    └─────────────────────────────────────────────────────────┘
```

**Flow:**

1. **Notification API** (after DB commit): publishes message to the appropriate **SNS topic** (email or whatsapp).
2. **SNS** fans out to one or more subscribers; each channel has one **SQS queue** subscribed to its topic.
3. **Workers** (email-worker, whatsapp-worker) poll **SQS** via `@SqsListener`, process the message, update `message_logs`.
4. Failed messages (after SQS redrive) go to **DLQ**; optional Lambda or API job can reconcile with `message_logs` or alert.

### 1.2 Why SNS + SQS Fits This Traffic Profile

| Requirement | Your profile | SNS/SQS fit |
|-------------|--------------|-------------|
| Throughput | 100–1,000 msg/sec peak, ~10K/day | SNS/SQS Standard supports **unlimited** throughput (request-based); no partition limits. |
| Ordering | Per-entity acceptable (message group) | Use **SQS Standard** for max throughput; use **SQS FIFO** only if you need strict per-session ordering (e.g. one WhatsApp session). |
| Replay / streaming | Not required | SQS is queue-based; no replay needed. |
| Cost | Top priority | SNS/SQS pay-per-request; no cluster or broker costs. |
| At-least-once, retries, DLQ | Required | Native via visibility timeout, redrive policy, and DLQ. |

### 1.3 SQS Standard vs FIFO

| Aspect | SQS Standard | SQS FIFO |
|--------|--------------|----------|
| Throughput | Unlimited (request-based) | 3,000 msg/sec (without batching), 30,000 with batching |
| Ordering | Best-effort, no guarantee | Exactly-once processing, ordering by MessageGroupId |
| Duplicates | At-least-once (rare duplicates) | Exactly-once (deduplication window 5 min) |
| Cost | Lower | Slightly higher |
| **Recommendation for cg-notification** | **Use Standard** for both email and WhatsApp unless you need strict per-session ordering. Your current design uses **KafkaRetryService** for retries and **per-message** semantics; FIFO adds complexity (MessageGroupId per session) and throughput limits. Use **Standard** for simplicity and cost. |

**When to use FIFO:** Only if you must guarantee strict ordering for a single WhatsApp session (e.g. one `MessageGroupId` per session). For 1,000 msg/sec across many sessions, Standard is simpler and scales without throughput caps.

---

## 2. Kafka to SNS/SQS Mapping

### 2.1 Topic and Queue Mapping

| Kafka | AWS equivalent |
|-------|-----------------|
| **Topic** `notifications-email` | **SNS topic** `notifications-email` (or `cg-notification-email`) |
| **Topic** `notifications-whatsapp` | **SNS topic** `notifications-whatsapp` |
| **Consumer group** (email-worker) | **SQS queue** `notifications-email-queue` subscribed to SNS `notifications-email` |
| **Consumer group** (whatsapp-worker) | **SQS queue** `notifications-whatsapp-queue` subscribed to SNS `notifications-whatsapp` |
| **DLQ topic** `notifications-email-dlq` | **SQS queue** `notifications-email-dlq` (no SNS; API publishes directly to this queue for DLQ) |
| **DLQ topic** `notifications-whatsapp-dlq` | **SQS queue** `notifications-whatsapp-dlq` |

### 2.2 Fan-Out with SNS

- **One SNS topic per channel:** Each SNS topic has one subscription: the SQS queue for that worker.
- **Adding more consumers later:** To fan out to multiple queues (e.g. analytics, audit), add another SQS queue as an SNS subscriber; SNS will deliver to all subscribers.
- **Current design:** 1:1 topic → queue per channel, so behavior matches your current Kafka single-consumer-group setup.

### 2.3 Message Key (Kafka) → SQS MessageAttributes

- **Kafka:** Key = `messageId`, Value = JSON payload.
- **SNS/SQS:** Publish payload as body; set **MessageAttribute** `messageId` (or use `MessageDeduplicationId` in FIFO). Workers can read `messageId` from the body (current payload already has it) or from attributes for routing/logging.

---

## 3. Spring Boot Application Changes

### 3.1 Dependencies to Remove

**notification-api/pom.xml, email-worker/pom.xml, whatsapp-worker/pom.xml:**

- `spring-kafka`
- `aws-msk-iam-auth`

**common-proto/pom.xml:**

- `kafka-clients` (only if no other module needs it after migration).

**notification-api (test):** Remove or replace `spring-kafka-test` and `testcontainers/kafka` with SQS/localstack if you want integration tests against SQS.

### 3.2 Dependencies to Add

**Parent pom.xml (dependencyManagement):**

```xml
<!-- Spring Cloud AWS BOM (use version compatible with Spring Boot 3.3.x) -->
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-dependencies</artifactId>
    <version>3.1.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**notification-api/pom.xml:**

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-sns</artifactId>
</dependency>
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-sqs</artifactId>
</dependency>
```

**email-worker/pom.xml and whatsapp-worker/pom.xml:**

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-sqs</artifactId>
</dependency>
```

(Workers only consume from SQS; they do not publish to SNS. API publishes to SNS and to DLQ SQS queues.)

### 3.3 Configuration (application.yml)

**notification-api:**

```yaml
spring:
  cloud:
    aws:
      region:
        static: ${AWS_REGION:us-east-1}
      credentials:
        # Use default provider chain (IAM role in ECS, env vars locally)
        use-default-aws-credentials-chain: true
      sns:
        endpoint: # omit in prod; set for localstack
      sqs:
        endpoint: # omit in prod; set for localstack
      sqs.listener:
        max-concurrent-messages: 10
        max-messages-per-poll: 10

# Replace spring.kafka.topics with SNS/SQS
messaging:
  sns:
    topics:
      email: notifications-email
      whatsapp: notifications-whatsapp
  sqs:
    queues:
      email-dlq: notifications-email-dlq
      whatsapp-dlq: notifications-whatsapp-dlq
```

**email-worker / whatsapp-worker:**

```yaml
spring:
  cloud:
    aws:
      region:
        static: ${AWS_REGION:us-east-1}
      credentials:
        use-default-aws-credentials-chain: true
      sqs:
        endpoint:
      sqs.listener:
        max-concurrent-messages: 5
        max-messages-per-poll: 10

messaging:
  sqs:
    queues:
      email: notifications-email-queue    # email-worker
      # whatsapp: notifications-whatsapp-queue  # whatsapp-worker
```

### 3.4 Publishing to SNS (Notification API)

Replace `KafkaTemplate` with `SnsTemplate` (or `SnsAsyncClient`) and publish after DB commit.

**Example: SnsNotificationSender (new service)**

```java
package com.clapgrow.notification.api.service;

import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnsNotificationSender {

    private final SnsTemplate snsTemplate;

    @Value("${messaging.sns.topics.email:notifications-email}")
    private String emailTopicArnOrName;
    @Value("${messaging.sns.topics.whatsapp:notifications-whatsapp}")
    private String whatsappTopicArnOrName;

    /**
     * Publish message to the channel-specific SNS topic.
     * Message body is JSON payload; messageId can be set as attribute for tracing.
     */
    public void publish(String topicName, String messageId, String jsonPayload) {
        String topicArn = resolveTopicArn(topicName);
        snsTemplate.sendNotification(topicArn, jsonPayload, messageId);
        log.debug("Published message {} to SNS topic {}", messageId, topicName);
    }

    private String resolveTopicArn(String topicName) {
        // If topicName is already an ARN, use it; else build ARN from name (or use SnsTemplate with topic name if supported)
        if (topicName.startsWith("arn:aws:sns:")) {
            return topicName;
        }
        // SnsTemplate.sendNotification accepts topic ARN; resolve name to ARN via env or config
        return topicName;
    }

    public String getEmailTopic() { return emailTopicArnOrName; }
    public String getWhatsappTopic() { return whatsappTopicArnOrName; }
}
```

**Publishing to SQS DLQ (from API):** Use `SqsTemplate` or `SqsAsyncClient` to send to the DLQ queue URL/name when sending to DLQ (replace `kafkaTemplate.send(dlqTopic, ...)`).

**Idempotency:** Keep generating a single `messageId` per request and storing it in `message_logs`. Workers should treat `messageId` as idempotency key: if they have already processed this `messageId` (e.g. status already DELIVERED/FAILED with same id), they can skip or ack without re-sending.

### 3.5 Consuming from SQS (Workers)

Replace `@KafkaListener` with `@SqsListener`.

**Email worker – example:**

```java
@SqsListener(value = "${messaging.sqs.queues.email}", deletionPolicy = ON_SUCCESS)
public void processEmailNotification(String payload, @Header("MessageId") String sqsMessageId) {
    // Optional: get messageId from payload or SQS message attributes
    NotificationPayload notification = objectMapper.readValue(payload, NotificationPayload.class);
    String messageId = notification.getMessageId();
    if (messageId == null) {
        messageId = sqsMessageId; // fallback to SQS MessageId
    }
    // ... same business logic: tenant check, send email, update status ...
    // On success: message is deleted by deletionPolicy = ON_SUCCESS
    // On exception: message returns to queue after visibility timeout, then retries → DLQ
}
```

**Important:** Use `deletionPolicy = ON_SUCCESS` so the message is only deleted when the method completes without throwing. On throw, SQS will make the message visible again after the visibility timeout.

### 3.6 Message Format and Idempotency

- **Payload:** Keep existing JSON (messageId, siteId, channel, recipient, subject, body, etc.). No API keys in payload.
- **Idempotency:** Include `messageId` in every payload. Workers should check `message_logs` by `messageId`; if status is already DELIVERED (or FAILED with no retry intent), process is idempotent (e.g. skip send, then delete from SQS).

---

## 4. Throughput, Scaling, and Backpressure

### 4.1 Scaling to 1,000 msg/sec

- **SNS:** Automatically scales; no partition limit.
- **SQS Standard:** Request-based; 1,000 msg/sec is well within limits. Use **batch send** (SendMessageBatch, max 10 messages) to reduce API calls and cost.
- **Workers:** Scale ECS tasks (email-worker, whatsapp-worker) horizontally. Each task runs multiple `@SqsListener` threads (e.g. `max-concurrent-messages: 5` per listener). More tasks = more consumers.

### 4.2 Batching

- **Producer (API):** For bulk notifications, use `SnsTemplate` (or SNS PublishBatch) to send in batches if you have multiple messages ready. For single-message flow, single publish is fine.
- **Consumer:** Spring Cloud AWS SQS listener can poll up to 10 messages per poll. Tune `max-messages-per-poll` and `max-concurrent-messages` so that visibility timeout > (max processing time per message × concurrency).

### 4.3 Backpressure

- **SQS:** Natural backpressure: if workers are slow, messages accumulate in the queue; no broker push. No need for Kafka-style fetch throttling.
- **Visibility timeout:** Set to 2–5× expected processing time so that if a worker crashes, messages reappear for another worker. Avoid setting too high or failed messages wait too long before retry.

---

## 5. Error Handling and Retries

### 5.1 Visibility Timeout

- Set **visibility timeout** on the main queue (e.g. 60–120 seconds for email/WhatsApp sends that may call external APIs).
- If the worker does not delete the message within this time (e.g. crash or exception), the message becomes visible again and another worker can process it (at-least-once).

### 5.2 Retries and Redrive Policy

- **Max receives:** e.g. 3. After 3 failed processing attempts (message received but not deleted), SQS moves the message to the **DLQ**.
- **Redrive allow policy:** On the main queue, set `redriveAllowPolicy` so the DLQ can receive messages; on the DLQ, set `redrivePolicy` to the main queue ARN (or leave DLQ without redrive).

### 5.3 DLQ Configuration

- **notifications-email-dlq**, **notifications-whatsapp-dlq:** Standard SQS queues. No SNS; the API’s “retry/DLQ” logic can either:
  - **Option A:** Rely on SQS redrive: after max receives, SQS moves to DLQ; no need for API to publish to DLQ. Then KafkaRetryService is replaced by “no republish to main queue” (DLQ is terminal).
  - **Option B:** Keep KafkaRetryService-like logic in API: API still has a job that finds FAILED rows and “republishes” by sending to SNS again (or to main SQS via SNS). After max retries, API sends to DLQ queue via SqsTemplate. This mirrors current behavior (retry from DB, then DLQ).

Recommendation: **Option A** is simpler—use SQS redrive to DLQ and remove producer-side retry republish; only keep a job that retries **consumer failures** by re-sending to SNS (so message reappears in SQS). That way, “retry” = republish to SNS once per retry; “DLQ” = SQS redrive after max receives.

### 5.4 Duplicate Messages

- SQS Standard can deliver a message more than once. Workers must be **idempotent**: check `message_logs` by `messageId`; if already DELIVERED (or terminal state), skip send and return success so the message is deleted.

---

## 6. Infrastructure Setup

### 6.1 Required AWS Resources

- **SNS topics:** `notifications-email`, `notifications-whatsapp`
- **SQS queues (main):** `notifications-email-queue`, `notifications-whatsapp-queue`
- **SQS queues (DLQ):** `notifications-email-dlq`, `notifications-whatsapp-dlq`
- **Subscriptions:** SNS topic → SQS queue (subscription per channel)
- **IAM:** API task role: SNS Publish, SQS SendMessage (for DLQ if used). Worker task roles: SQS ReceiveMessage, DeleteMessage, GetQueueAttributes, ChangeMessageVisibility.

### 6.2 Terraform Snippets

**variables.tf (add/keep):**

```hcl
variable "app_name" {
  description = "Application prefix for resource names (SNS, SQS)"
  type        = string
  default     = "cg-notification"
}

variable "cloudwatch_log_retention_days" {
  description = "CloudWatch log retention in days for ECS service logs"
  type        = number
  default     = 14
}

# Remove or deprecate msk_cluster_name when MSK is decommissioned
```

**sns-sqs.tf (new file):**

```hcl
# -----------------------------------------------------------------------------
# SNS Topics (replacing Kafka topics)
# -----------------------------------------------------------------------------
resource "aws_sns_topic" "email" {
  name = "${var.app_name}-notifications-email"
  tags = { Name = "${var.app_name}-notifications-email" }
}

resource "aws_sns_topic" "whatsapp" {
  name = "${var.app_name}-notifications-whatsapp"
  tags = { Name = "${var.app_name}-notifications-whatsapp" }
}

# -----------------------------------------------------------------------------
# SQS DLQs (replacing Kafka DLQ topics)
# -----------------------------------------------------------------------------
resource "aws_sqs_queue" "email_dlq" {
  name                      = "${var.app_name}-notifications-email-dlq"
  message_retention_seconds  = 1209600  # 14 days
  tags = { Name = "${var.app_name}-notifications-email-dlq" }
}

resource "aws_sqs_queue" "whatsapp_dlq" {
  name                      = "${var.app_name}-notifications-whatsapp-dlq"
  message_retention_seconds  = 1209600
  tags = { Name = "${var.app_name}-notifications-whatsapp-dlq" }
}

# -----------------------------------------------------------------------------
# SQS Main Queues (replacing Kafka consumer groups)
# -----------------------------------------------------------------------------
resource "aws_sqs_queue" "email" {
  name                       = "${var.app_name}-notifications-email-queue"
  visibility_timeout_seconds = 120
  message_retention_seconds  = 345600   # 4 days
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

# DLQ must allow redrive from main queue
resource "aws_sqs_queue_redrive_allow_policy" "email_dlq" {
  queue_url = aws_sqs_queue.email_dlq.id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byPolicy"
    sourceQueueArns   = [aws_sqs_queue.email.arn]
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "whatsapp_dlq" {
  queue_url = aws_sqs_queue.whatsapp_dlq.id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byPolicy"
    sourceQueueArns   = [aws_sqs_queue.whatsapp.arn]
  })
}

# -----------------------------------------------------------------------------
# SNS → SQS subscriptions
# -----------------------------------------------------------------------------
resource "aws_sns_topic_subscription" "email_queue" {
  topic_arn = aws_sns_topic.email.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.email.arn
}

resource "aws_sns_topic_subscription" "whatsapp_queue" {
  topic_arn = aws_sns_topic.whatsapp.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.whatsapp.arn
}

# SQS policy: allow SNS to send messages to the queue
resource "aws_sqs_queue_policy" "email" {
  queue_url = aws_sqs_queue.email.id
  policy    = data.aws_iam_policy_document.sns_to_sqs_email.json
}

data "aws_iam_policy_document" "sns_to_sqs_email" {
  statement {
    sid    = "AllowSNS"
    effect = "Allow"
    principals { type = "Service", identifiers = ["sns.amazonaws.com"] }
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
    principals { type = "Service", identifiers = ["sns.amazonaws.com"] }
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.whatsapp.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.whatsapp.arn]
    }
  }
}
```

**IAM (add to api task role, remove MSK after cutover):**

```hcl
# API: publish to SNS and send to DLQ SQS if you keep API-side DLQ publish
resource "aws_iam_role_policy" "api_task_sns_sqs" {
  name = "SNS-SQS-Access"
  role = aws_iam_role.api_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["sns:Publish", "sns:PublishBatch"]
        Resource = [aws_sns_topic.email.arn, aws_sns_topic.whatsapp.arn]
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = [aws_sqs_queue.email_dlq.arn, aws_sqs_queue.whatsapp_dlq.arn]
      }
    ]
  })
}

# Email worker: consume from email queue
resource "aws_iam_role_policy" "email_worker_task_sqs" {
  name = "SQS-Access"
  role = aws_iam_role.email_worker_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:ChangeMessageVisibility"]
        Resource = [aws_sqs_queue.email.arn]
      }
    ]
  })
}

# WhatsApp worker: consume from whatsapp queue
resource "aws_iam_role_policy" "whatsapp_worker_task_sqs" {
  name = "SQS-Access"
  role = aws_iam_role.whatsapp_worker_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:ChangeMessageVisibility"]
        Resource = [aws_sqs_queue.whatsapp.arn]
      }
    ]
  })
}
```

**Note:** If your Terraform does not define `var.app_name`, add it (e.g. `default = "cg-notification"`) or replace `${var.app_name}` with a literal prefix like `cg-notification` so queue/topic names align with your project.

---

## 7. CloudWatch Cost Optimization

### 7.1 Log Levels

- **Production:** `INFO` for application logs. Avoid `DEBUG` and trace logging in steady state.
- **Libraries:** Set `org.springframework.cloud.aws: WARN`, `software.amazon: WARN` to reduce SDK chatter.

### 7.2 Log Retention

- **Current:** `cloudwatch_log_retention_days = 14` in Terraform is good; reduce to **7** if acceptable for troubleshooting.
- **Effect:** Shorter retention reduces storage and (for log insights) scan cost.

### 7.3 Metrics to Keep vs Remove

- **Keep:** ECS CPU/memory, ALB request count/latency, SQS queue depth (NumberOfMessagesReceived, ApproximateNumberOfMessagesVisible), SNS NumberOfMessagesPublished. These are low-cost and useful.
- **Reduce/remove:** High-cardinality custom metrics (e.g. per-messageId or per-recipient). Keep aggregate counters (e.g. messages sent per channel, DLQ count).
- **MSK:** After migration, remove any MSK-specific dashboards and alarms.

### 7.4 Archive Logs to S3

- Use **CloudWatch Logs subscription filters** to stream log groups to **Kinesis Data Firehose** → **S3** for long-term retention at lower cost. Query with Athena when needed. This reduces CloudWatch Logs storage and extends retention cheaply.

---

## 8. Migration Strategy

### 8.1 Step-by-Step Plan

1. **Add SNS/SQS and new code paths (no traffic)**  
   - Deploy Terraform: SNS topics, SQS queues, DLQs, IAM.  
   - Deploy application with **feature flag**: e.g. `messaging.backend=kafka` (default) or `messaging.backend=sns`.  
   - When `sns`: API publishes to SNS (and optional DLQ SQS); workers do not consume from SQS yet.

2. **Dual-publish (optional, for validation)**  
   - Set API to **dual-publish**: after DB commit, publish to both Kafka and SNS.  
   - Workers still consume only from Kafka.  
   - Compare: ensure same messageId appears in both Kafka and SQS (e.g. by a small script or log sampling).  
   - Run for 24–48 hours; monitor SQS depth and SNS publish count.

3. **Consumer cutover**  
   - Switch workers to consume from SQS only (e.g. `messaging.backend=sns`, workers read from SQS, Kafka listeners disabled).  
   - Deploy workers; keep API dual-publishing or switch API to SNS-only.  
   - Monitor: message_logs statuses, DLQ depth, latency, errors.

4. **API to SNS-only**  
   - Stop publishing to Kafka; API publishes only to SNS (and DLQ SQS if used).  
   - Run for several days under load.

5. **Decommission MSK**  
   - Remove Kafka config and code from API and workers.  
   - Remove MSK Terraform, IAM MSK policies, kafka-admin ECR/task.  
   - Delete MSK cluster (after final backup/export if required).

### 8.2 Rollback

- **Before consumer cutover:** Rollback = do nothing; Kafka remains source of truth.  
- **After consumer cutover:** Rollback = set API back to dual-publish or Kafka-only; switch workers back to Kafka listeners; fix any DB rows stuck in PENDING (e.g. manual script to republish from DB to Kafka). Keep SNS/SQS Terraform so you can re-cutover without recreating resources.

---

## 9. Cost Comparison

### 9.1 Rough Monthly Comparison (us-east-1, indicative)

**Assumptions:** ~10K messages/day ≈ 300K/month; peak 1,000 msg/sec for short bursts; 3 ECS services (API, email-worker, whatsapp-worker); current CloudWatch 14-day retention.

| Component | MSK + current | SNS/SQS + optimized CW |
|-----------|----------------|-------------------------|
| **MSK Serverless** | ~$50–150+ (usage-based) | $0 |
| **SNS** | — | ~$0.50 (300K publishes) |
| **SQS** | — | ~$1.25 (300K receive + 300K send from SNS) |
| **CloudWatch Logs** | High (if verbose) | Lower (7–14 day retention, INFO only) |
| **CloudWatch Metrics** | Custom + MSK metrics | ECS/ALB/SQS only; no MSK |

**Summary:** Messaging leg (MSK → SNS/SQS) typically **60–80% cheaper**; total savings depend on how much you reduce CloudWatch (retention, level, archiving). Expect **significant** overall cost reduction with SNS/SQS and optimized logging.

---

## Validation Checklist (before removing MSK)

- [ ] Messages published to SNS are consumed from SQS.
- [ ] Message counts match DB inserts.
- [ ] DLQs receive failed messages after retries.
- [ ] No Kafka dependencies remain in code or config.
- [ ] CloudWatch cost drops (verify in Billing).

## Summary Checklist

- [x] Add Spring Cloud AWS (SNS/SQS) dependencies; remove Spring Kafka and aws-msk-iam-auth.
- [ ] Implement SNS publish and (if needed) SQS DLQ send in notification-api; replace KafkaTemplate usage in NotificationService, KafkaRetryService, ScheduledMessageService.
- [ ] Replace @KafkaListener with @SqsListener in email-worker and whatsapp-worker; keep payload and idempotency by messageId.
- [ ] Add Terraform: SNS topics, SQS queues, DLQs, subscriptions, queue policies, IAM for API and workers.
- [ ] Tune visibility timeout and max receive count; ensure DLQ redrive allow policy.
- [ ] Apply CloudWatch optimizations (retention, log level, metrics).
- [ ] Run dual-publish → consumer cutover → SNS-only → decommission MSK with rollback plan.

This guide is intended for a mid-level engineer to execute the migration end-to-end with minimal external dependencies.
