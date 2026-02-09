# MSK Serverless Cluster with IAM authentication
resource "aws_msk_serverless_cluster" "main" {
  cluster_name = var.msk_cluster_name

  vpc_config {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.msk.id]
  }

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }

  lifecycle {
    prevent_destroy = true
    # Avoid forced replacement when subnet IDs drift (e.g. after import from different VPC)
    ignore_changes = [vpc_config]
  }
}




