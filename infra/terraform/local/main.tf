locals {
  project_root       = abspath("${path.module}/../../..")
  kind_config_path   = abspath("${path.module}/${var.kind_config_path}")
  manifest_path      = abspath("${path.module}/${var.k8s_manifest_path}")
  kubeconfig_path    = abspath("${path.module}/.terraform/${var.cluster_name}-kubeconfig")
  kind_configuration = yamldecode(file(local.kind_config_path))

  application_files = concat(
    ["Dockerfile", "pom.xml"],
    sort(tolist(fileset(local.project_root, "src/**")))
  )
  application_hash = sha256(join("", [
    for filename in local.application_files : filesha256("${local.project_root}/${filename}")
  ]))

  manifest_files = sort(tolist(fileset(local.manifest_path, "**")))
  manifest_hash = sha256(join("", [
    for filename in local.manifest_files : filesha256("${local.manifest_path}/${filename}")
  ]))
}

resource "kind_cluster" "oficina" {
  name            = var.cluster_name
  node_image      = var.kind_node_image
  wait_for_ready  = true
  kubeconfig_path = local.kubeconfig_path

  kind_config {
    kind        = local.kind_configuration.kind
    api_version = local.kind_configuration["apiVersion"]

    dynamic "node" {
      for_each = local.kind_configuration.nodes

      content {
        role = node.value.role
      }
    }
  }
}

resource "null_resource" "docker_image" {
  triggers = {
    application_hash = local.application_hash
    image_name       = var.image_name
  }

  depends_on = [kind_cluster.oficina]

  provisioner "local-exec" {
    working_dir = local.project_root
    interpreter = ["/bin/bash", "-c"]
    command     = "docker build -t \"$IMAGE_NAME\" . && kind load docker-image \"$IMAGE_NAME\" --name \"$CLUSTER_NAME\""

    environment = {
      CLUSTER_NAME = var.cluster_name
      IMAGE_NAME   = var.image_name
    }
  }
}

resource "null_resource" "k8s_apply" {
  triggers = {
    cluster_name    = kind_cluster.oficina.name
    image_build_id  = null_resource.docker_image.id
    kubeconfig_path = local.kubeconfig_path
    manifest_hash   = local.manifest_hash
    manifest_path   = local.manifest_path
    namespace       = var.namespace
    project_root    = local.project_root
  }

  depends_on = [null_resource.docker_image]

  provisioner "local-exec" {
    working_dir = local.project_root
    interpreter = ["/bin/bash", "-c"]
    command     = <<-EOT
      set -euo pipefail
      kubectl --kubeconfig "$KUBECONFIG_FILE" apply -k "$MANIFEST_PATH"
      kubectl --kubeconfig "$KUBECONFIG_FILE" -n "$NAMESPACE" rollout status deployment/oficina-postgres --timeout=180s
      kubectl --kubeconfig "$KUBECONFIG_FILE" -n "$NAMESPACE" rollout status deployment/oficina-mailpit --timeout=180s
      kubectl --kubeconfig "$KUBECONFIG_FILE" -n "$NAMESPACE" rollout status deployment/oficina-api --timeout=300s
    EOT

    environment = {
      KUBECONFIG_FILE = local.kubeconfig_path
      MANIFEST_PATH   = local.manifest_path
      NAMESPACE       = var.namespace
    }
  }

  provisioner "local-exec" {
    when        = destroy
    working_dir = self.triggers.project_root
    interpreter = ["/bin/bash", "-c"]
    command     = "kubectl --kubeconfig \"$KUBECONFIG_FILE\" delete -k \"$MANIFEST_PATH\" --ignore-not-found=true || true"

    environment = {
      KUBECONFIG_FILE = self.triggers.kubeconfig_path
      MANIFEST_PATH   = self.triggers.manifest_path
    }
  }
}
