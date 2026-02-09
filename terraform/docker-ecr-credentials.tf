# Optional: configure Docker to use ECR credential helper instead of storing
# tokens in ~/.docker/config.json. Removes "credentials stored unencrypted" warning.
# Enable with: terraform apply -var="configure_docker_ecr_credentials=true"

resource "null_resource" "docker_ecr_credential_helper" {
  count = var.configure_docker_ecr_credentials ? 1 : 0

  triggers = {
    region = var.aws_region
  }

  provisioner "local-exec" {
    command     = "AWS_REGION=${var.aws_region} bash \"${path.module}/../scripts/setup-docker-ecr-credential-helper.sh\""
    working_dir = path.module
  }
}
