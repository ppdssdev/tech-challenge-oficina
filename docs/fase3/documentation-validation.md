# Validação da consolidação documental

Data: 2026-11-03. Escopo: arquivos Markdown alterados/novos nas quatro PRs.
Cópias de trabalho isoladas em `/tmp/oficina-docs-final`; checkout original e seus
arquivos não rastreados preservados. Código, migrations, manifests, Terraform,
pipelines, lockfiles e estados não foram modificados.

| Verificação | Resultado e alcance |
|---|---|
| Mermaid | 13 blocos analisados com parser Mermaid 11.12.0, sem erro; inclui componentes, implantação, duas sequências e MER; parsing não substitui revisão visual |
| Links locais | Destinos relativos e âncoras conferidos nos quatro snapshots; sem destinos ausentes na revisão final |
| Links entre repos | Paths conferidos contra snapshots locais; novos links main dependem dos merges das PRs correspondentes; não foi feito merge |
| Fontes remotas | Metadados GitHub e runs CI consultados; não foi feito crawler de todos os sites externos/URLs localhost |
| Modelo SQL | V1–V11 executadas numericamente com ON_ERROR_STOP num PostgreSQL 16.15 descartável, sem rede; catálogo gerou 12 tabelas, 2 views e 116 colunas; container removido |
| Proveniência SQL | SHA-256 de cada migration registrado no modelo; nenhum arquivo SQL alterado; sem consulta ao Flyway history do RDS |
| Consistência | Comparação de JWT/view V2, rotas/ownership, use cases, flags Gateway/Lambda, HPA/digest, backends e estado operacional; fontes antigas identificadas como históricas |
| Segredos/privacidade | Scan por padrões e revisão dos documentos alterados: sem CPF literal, JWT, chave privada, access key ou IP privado desnecessário; exemplo histórico de CPF removido e CIDRs do desenho K3s omitidos |
| Higiene/escopo | git diff --check e allowlist de arquivos .md; nenhuma mudança funcional/infra/pipeline/state |

O scanner procura PEM privado, access keys AWS, JWT compacto, CPF literal/formatado,
IPs privados e credenciais demonstrativas literais. IDs de runs GitHub e hashes
foram diferenciados de dados pessoais. Não é prova matemática de ausência de
segredos, auditoria de todo o histórico Git ou scan de CVEs. Fixtures/configurações
locais preexistentes em código não foram alteradas nesta tarefa documental.

A validação de links trata paths e âncoras; URLs HTTP externas não foram todas
checadas. Referências imutáveis de código usam SHAs auditados. Links localhost são
contratos de execução local. Documentos novos de outros repositórios têm aviso
explícito de disponibilidade em main somente depois dos merges independentes.

Os scripts auxiliares de geração/parser/scanner e saídas de catálogo ficaram fora
dos repositórios, em diretório temporário de revisão. A reprodução independente do
modelo está em [data-model.md](data-model.md); diagramas estão embutidos em Markdown
com linguagem mermaid e podem ser analisados com Mermaid 11.12.0. Os comandos
operacionais e limites da coleta estão em [operational-evidence.md](operational-evidence.md).

Não foram reexecutadas suítes funcionais completas para alterações exclusivamente
documentais. Runs CI das baselines foram consultados e estão na
[matriz](evidence-matrix.md); o status dos checks das PRs novas deve ser conferido
no HEAD documental antes de merge. CI verde não encerra pendências cloud.
