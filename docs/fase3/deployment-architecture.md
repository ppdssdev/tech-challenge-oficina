# Arquitetura implantada e limites de comprovação

Baseline e classificação: [índice](README.md). Observações AWS/K3s em
2026-11-03: [registro sanitizado](operational-evidence.md). Linha contínua representa
recurso/relação observada ou declarada no código; **não afirma que toda chamada foi
executada**. Linha pontilhada identifica fluxo fechado, local ou planejado.

## Componentes

```mermaid
flowchart TB
  Operator["Operador autorizado"]
  GH["GitHub Actions: CI; OIDC/CD pendentes"]
  subgraph AWS["AWS us-east-2 — staging observado"]
    IAM["IAM: role do nó e role Lambda"]
    SSM["Systems Manager"]
    ECR["ECR: imagem imutável por digest"]
    SM["Secrets Manager: valores fora do Git"]
    GW["HTTP API Gateway: endpoint desabilitado; AWS_IAM"]
    CW["CloudWatch Logs: declarado em auth; entrega não verificada"]
    subgraph VPC["VPC staging"]
      subgraph PUB["Subnet pública — AZ a"]
        EC2["EC2 m7i-flex.large — um nó K3s"]
        API["Java 21 / Spring Boot — 3 pods Ready"]
        MS["Metrics Server — operacional"]
        HPA["HPA 1–3 pods"]
        EC2 --> API
        MS --> HPA --> API
      end
      subgraph PRIV["Duas subnets privadas — AZ a e AZ b"]
        L["Lambda Python 3.13 — concorrência zero"]
        VPCE["Endpoint Interface Secrets Manager — 2 subnets"]
        RDS[("RDS PostgreSQL 16.15 — Single-AZ")]
      end
      API -->|"JDBC TLS; SG app"| RDS
      L -.->|"SELECT view V2; SG auth; emissão fechada"| RDS
      L -.->|"443 privado; janela fechada"| VPCE
      EC2 -->|"443 privado; configuração externa"| VPCE
    end
    VPCE --> SM
    GW -.->|"POST /auth/token fechado"| L
    L -.-> CW
    IAM --> EC2
    IAM --> L
    SSM -->|"comandos administrativos"| EC2
    EC2 -->|"pull HTTPS"| ECR
  end
  Operator -->|"IAM / canal TLS"| SSM
  GH -.->|"OIDC bloqueado; sem CD implantado"| IAM
  subgraph LOCAL["Somente ambiente local versionado"]
    PROM["Prometheus"] --> GRAF["Grafana"]
    LOCALAPI["API local /actuator/prometheus"] --> PROM
  end
```

Lambda é um serviço regional com interfaces de rede nas subnets privadas; o desenho
não implica servidores Lambda dedicados em cada AZ. RDS tem subnet group em duas
AZs, mas **uma instância Single-AZ**, sem réplica standby. Secrets Manager, ECR,
SSM e API Gateway são serviços AWS fora das subnets da aplicação. Tracing não foi
incluído como componente implantado: não há coletor/exportador comprovado.

## Implantação e caminhos de rede

```mermaid
flowchart LR
  OP["Estação do operador"] -->|"1. HTTPS autenticado IAM"| SSM["SSM regional"]
  subgraph AWS["AWS / staging"]
    subgraph VPC["VPC"]
      IGW["Internet Gateway"]
      subgraph PUB["Subnet pública AZ a"]
        NODE["EC2 + K3s; SG app sem ingressos"]
        SVC["Service ClusterIP:8080"] --> POD["Deployment API; digest; probes"]
        JOB["Job Flyway separado; não presente no inventário"]
        MET["Metrics Server"] --> HPA["HPA CPU/memória"] --> POD
      end
      subgraph PRIVATE["Subnets privadas isoladas AZ a/b"]
        ENI["ENIs Lambda; SG auth"]
        DB[("RDS privado:5432; SG PostgreSQL")]
        EP["Endpoint Interface:443; private DNS"]
      end
      NODE -->|"saída 443"| IGW
      POD -->|"2. privado: JDBC verify-full"| DB
      JOB -.->|"2. privado: DDL migrator"| DB
      ENI -.->|"2. privado: SELECT V2"| DB
      NODE -->|"2. privado:443"| EP
      ENI -.->|"2. privado:443"| EP
    end
    SM["Secrets Manager"]
    ECR["ECR"]
    EP --> SM
    IGW -->|"HTTPS pull"| ECR
    IGW -->|"canal iniciado pelo agente"| SSM
    SSM -->|"1. Run Command / sessão; sem SSH"| NODE
    NODE -->|"administração local kubectl"| SVC
  end
  PROM["3. Prometheus/Grafana: local; staging pendente"] -.-> POD
```

1. **Administração:** SSM usa conexão de saída do agente; a seta de comando não é
   ingresso TCP público na EC2. Consultas kubectl foram executadas por Run Command.
   Túnel Session Manager/port-forward é procedimento operacional, não endpoint público.
2. **Aplicação:** API/RDS e Lambda/RDS usam caminho privado, porta 5432 e SGs por
   origem. TLS `verify-full` é exigido na configuração; a auditoria não abriu sessão
   SQL remota para comprovar TLS efetivo. Lambda lê segredo por endpoint Interface;
   não há NAT Gateway na topologia versionada. Não há Ingress/ALB no inventário.
3. **Observabilidade:** Metrics Server alimenta HPA; não armazena séries históricas
   como Prometheus. Prometheus/Grafana locais não comprovam observabilidade cloud.
   `/actuator/prometheus` é `permitAll()` no código: ClusterIP/SG fechado limita
   exposição externa, mas não demonstra isolamento entre workloads no cluster.

## Fontes e divergências

- [network.tf](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/10fa81e88e8e5cd9ab06fe8661cf1a735be26675/network.tf), [compute.tf](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/10fa81e88e8e5cd9ab06fe8661cf1a735be26675/compute.tf), [security.tf](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/10fa81e88e8e5cd9ab06fe8661cf1a735be26675/security.tf):
  uma subnet pública, duas privadas, sem NAT; EC2 padrão `t3.medium`, observado
  `m7i-flex.large` (tipo permitido pela variável, não prova de drift).
- [secretsmanager-endpoint.tf](https://github.com/ppdssdev/tech-challenge-oficina-infra-k3s/blob/10fa81e88e8e5cd9ab06fe8661cf1a735be26675/secretsmanager-endpoint.tf): endpoint opcional, padrão desligado;
  observado habilitado. Valores efetivos de tfvars/state não foram consultados.
- [rds.tf](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/blob/51bb0557156a42e0f6f71c05bc304a6e9af362bf/rds.tf), [network.tf](https://github.com/ppdssdev/tech-challenge-oficina-infra-rds/blob/51bb0557156a42e0f6f71c05bc304a6e9af362bf/network.tf): privado, criptografado,
  Single-AZ, duas origens SG; configuração observada compatível.
- [infra/terraform/main.tf](https://github.com/ppdssdev/tech-challenge-oficina-auth-lambda/blob/1ff247717e35054967495ba7684742c67aaeb9da/infra/terraform/main.tf): HTTP API endpoint desabilitado,
  rota AWS_IAM, concorrência padrão zero, janela sintética opcional de 2.
- [Manifest API](../../k8s/staging/api.yaml), [HPA](../../k8s/staging/hpa.yaml),
  [Job](../../k8s/staging/migration-job.yaml), [Compose](../../docker-compose.yml).

Sem state ou plan, esta comparação não certifica ausência de drift. Não há evidência
de sincronizador automático Secrets Manager → Kubernetes; o provisionamento externo
de Secrets/ConfigMaps é descrito no [runbook existente](../staging/deployment.md).
