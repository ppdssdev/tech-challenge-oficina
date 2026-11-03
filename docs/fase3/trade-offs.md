# Limitações, alternativas e custos

| Escolha/limite | Benefício | Consequência e comprovação pendente |
|---|---|---|
| K3s single-node em EC2 | Menos componentes/control plane gerenciado no escopo acadêmico | Falha do nó para API e controle; 3 pods no mesmo nó não dão HA; testar recuperação/reboot |
| RDS Single-AZ privado | Banco gerenciado e acesso por SG, sem exposição pública | Sem failover entre AZs; subnet group em duas AZs não cria standby; restore/RTO/RPO não medidos |
| HPA 1–3 | Ajusta pods conforme CPU/memória | Limitado ao nó; não cria EC2; amostra de 3 réplicas não demonstra escala sob carga; revisar memória JVM/capacidade |
| HTTP API com endpoint desligado | Mantém demonstração fechada | Não há URL pública funcional nem fluxo browser completo; não chamar HTTP API de PRIVATE API |
| CPF como entrada de autenticação | Demonstra consulta read-only e emissão RS256 | Não prova posse/identidade; somente UUIDs sintéticos explicitamente habilitados; produção requer fator verificável |
| JWT 900s e duas cadeias CUSTOMER | Validação local e integração com legado | Revogação não imediata; PEM e JWKS precisam mesma confiança; rotação/negações operacionais pendentes |
| CI separada de CD | Checks reproduzíveis sem credenciais AWS em PRs | CD ausente em main; SCP bloqueia auditoria OIDC e subjects da proposta divergem; release manual exige rastreabilidade |
| Observabilidade local | Métricas/dashboards reproduzíveis | Sem Prometheus/Grafana no cluster auditado; tracing ausente; faltam alertas e retenção cloud |
| Ausência de produção comprovada | Evita prometer ambiente não entregue | Não demonstrados SLO, disponibilidade ou segurança de produção; exemplos/configuração production não são deploy |

## Custos operacionais

Não há orçamento atualizado nem fatura auditada nesta entrega. Não prometer
“gratuito” com base em Free Plan/créditos antigos. Drivers: horas EC2 do tipo
**efetivamente implantado** m7i-flex.large, EBS gp3/snapshots, IPv4 público de saída,
RDS db.t4g.micro/20 GiB e backups, endpoint Interface Secrets Manager em duas AZs,
segredos/requests, ECR armazenamento/scan, Lambda, API Gateway, CloudWatch e tráfego.
EC2/RDS/endpoint continuam gerando custo sem requisições; desligar concorrência da
Lambda não elimina esses custos. Snapshots retidos após desmontagem também custam.

Somar custo fixo por hora × horas planejadas, armazenamento e consumo, separar
créditos temporários do custo bruto e validar preços/região e elegibilidade da conta.
Os números antigos em `aws-infrastructure-dependencies.md` são estimativas históricas
para outra configuração, não orçamento atual. Custos de manutenção de K3s, patches,
backup, incidentes e revisão de segredos são trabalho operacional, mesmo sem fatura
específica. EKS/Multi-AZ podem reduzir algumas responsabilidades, com custos e
complexidade próprios; nenhuma migração foi realizada nesta tarefa.

Governança observada: zero aprovações humanas obrigatórias nos quatro repos; banca
adicionada somente na API. [Consulta e limites](governance-audit.md).
