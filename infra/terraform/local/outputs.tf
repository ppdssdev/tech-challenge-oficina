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

output "prometheus_url" {
  description = "URL local do Prometheus após iniciar o port-forward."
  value       = "http://localhost:${var.prometheus_port}"
}

output "grafana_url" {
  description = "URL local do Grafana após iniciar o port-forward."
  value       = "http://localhost:${var.grafana_port}"
}

output "api_port_forward_command" {
  description = "Comando para expor temporariamente a API local."
  value       = "kubectl -n ${var.namespace} port-forward svc/oficina-api ${var.api_port}:8080"
}

output "mailpit_port_forward_command" {
  description = "Comando para expor temporariamente o Mailpit local."
  value       = "kubectl -n ${var.namespace} port-forward svc/oficina-mailpit ${var.mailpit_ui_port}:8025"
}

output "prometheus_port_forward_command" {
  description = "Comando para expor temporariamente o Prometheus local."
  value       = "kubectl -n ${var.namespace} port-forward svc/oficina-prometheus ${var.prometheus_port}:9090"
}

output "grafana_port_forward_command" {
  description = "Comando para expor temporariamente o Grafana local."
  value       = "kubectl -n ${var.namespace} port-forward svc/oficina-grafana ${var.grafana_port}:3000"
}

output "manual_validation_command" {
  description = "Comando de validação manual do Actuator."
  value       = "curl http://localhost:${var.api_port}/actuator/health"
}

output "hpa_status_command" {
  description = "Comando para consultar o estado do HPA da API."
  value       = "kubectl -n ${var.namespace} get hpa"
}

output "hpa_describe_command" {
  description = "Comando para detalhar o HPA da API."
  value       = "kubectl -n ${var.namespace} describe hpa oficina-api-hpa"
}

output "top_pods_command" {
  description = "Comando para consultar métricas de recursos dos pods."
  value       = "kubectl -n ${var.namespace} top pods"
}
