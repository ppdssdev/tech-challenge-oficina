output "cluster_name" {
  description = "Nome do cluster Kind provisionado."
  value       = kind_cluster.oficina.name
}

output "kubeconfig_path" {
  description = "Kubeconfig isolado gerado para acessar o cluster provisionado."
  value       = local.kubeconfig_path
}

output "api_health_url" {
  description = "URL local do health após iniciar o port-forward."
  value       = "http://localhost:${var.api_port}/actuator/health"
}

output "swagger_url" {
  description = "URL local do Swagger após iniciar o port-forward."
  value       = "http://localhost:${var.api_port}/swagger-ui/index.html"
}

output "mailpit_url" {
  description = "URL local da interface do Mailpit após iniciar o port-forward."
  value       = "http://localhost:${var.mailpit_ui_port}"
}

output "api_port_forward_command" {
  description = "Comando para expor temporariamente a API local."
  value       = "kubectl -n ${var.namespace} port-forward svc/oficina-api ${var.api_port}:8080"
}

output "mailpit_port_forward_command" {
  description = "Comando para expor temporariamente o Mailpit local."
  value       = "kubectl -n ${var.namespace} port-forward svc/oficina-mailpit ${var.mailpit_ui_port}:8025"
}

output "manual_validation_command" {
  description = "Comando de validação manual do Actuator."
  value       = "curl http://localhost:${var.api_port}/actuator/health"
}
