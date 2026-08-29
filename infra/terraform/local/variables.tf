variable "cluster_name" {
  description = "Nome do cluster Kind local."
  type        = string
  default     = "oficina-local"
}

variable "namespace" {
  description = "Namespace Kubernetes usado pelos manifests locais."
  type        = string
  default     = "oficina"
}

variable "image_name" {
  description = "Nome e tag da imagem Docker local da API."
  type        = string
  default     = "tech-challenge-oficina:local"
}

variable "kind_node_image" {
  description = "Imagem do nó Kind usada para tornar o cluster reproduzível."
  type        = string
  default     = "kindest/node:v1.34.0"
}

variable "kind_config_path" {
  description = "Caminho, relativo a este módulo, para a configuração Kind existente."
  type        = string
  default     = "../../../k8s/kind/oficina-kind.yaml"
}

variable "k8s_manifest_path" {
  description = "Caminho, relativo a este módulo, para a base Kustomize local."
  type        = string
  default     = "../../../k8s/local"
}

variable "api_port" {
  description = "Porta local usada no port-forward da API."
  type        = number
  default     = 8080
}

variable "mailpit_ui_port" {
  description = "Porta local usada no port-forward da interface do Mailpit."
  type        = number
  default     = 8025
}

variable "prometheus_port" {
  description = "Porta local usada no port-forward do Prometheus."
  type        = number
  default     = 9090
}

variable "grafana_port" {
  description = "Porta local usada no port-forward do Grafana."
  type        = number
  default     = 3000
}
