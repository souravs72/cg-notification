# ECR Repository for notification-api
resource "aws_ecr_repository" "api" {
  name                 = "cg-notification/api"
  image_tag_mutability = "MUTABLE"
  force_delete         = true # Allow deletion even with images (for fresh deployments)

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "cg-notification-api"
  }
}

# ECR Lifecycle Policy for notification-api (prevents unbounded storage cost growth)
resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = {
        type = "expire"
      }
    }]
  })
}

# ECR Repository for email-worker
resource "aws_ecr_repository" "email_worker" {
  name                 = "cg-notification/email-worker"
  image_tag_mutability = "MUTABLE"
  force_delete         = true # Allow deletion even with images (for fresh deployments)

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "cg-notification-email-worker"
  }
}

# ECR Lifecycle Policy for email-worker
resource "aws_ecr_lifecycle_policy" "email_worker" {
  repository = aws_ecr_repository.email_worker.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = {
        type = "expire"
      }
    }]
  })
}

# ECR Repository for whatsapp-worker
resource "aws_ecr_repository" "whatsapp_worker" {
  name                 = "cg-notification/whatsapp-worker"
  image_tag_mutability = "MUTABLE"
  force_delete         = true # Allow deletion even with images (for fresh deployments)

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "cg-notification-whatsapp-worker"
  }
}

# ECR Lifecycle Policy for whatsapp-worker
resource "aws_ecr_lifecycle_policy" "whatsapp_worker" {
  repository = aws_ecr_repository.whatsapp_worker.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = {
        type = "expire"
      }
    }]
  })
}

# ECR Repository for migration image
resource "aws_ecr_repository" "migration" {
  name                 = "cg-notification/migration"
  image_tag_mutability = "MUTABLE"
  force_delete         = true # Allow deletion even with images (for fresh deployments)

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "cg-notification-migration"
  }
}

# ECR Lifecycle Policy for migration
resource "aws_ecr_lifecycle_policy" "migration" {
  repository = aws_ecr_repository.migration.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = {
        type = "expire"
      }
    }]
  })
}

# ECR Repository for kafka-admin (MSK topic creation)
resource "aws_ecr_repository" "kafka_admin" {
  name                 = "cg-notification/kafka-admin"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "cg-notification-kafka-admin"
  }
}

resource "aws_ecr_lifecycle_policy" "kafka_admin" {
  repository = aws_ecr_repository.kafka_admin.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = {
        type = "expire"
      }
    }]
  })
}

