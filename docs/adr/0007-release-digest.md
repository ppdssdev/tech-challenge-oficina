# ADR 0007 — Release da API por digest

**Status:** Renderer implementado; digest observado no cluster; 2026-11-03. Registro consolidado a partir das fontes; não inventa data de
aprovação original nem transforma propostas em implantação.

## Contexto

Evitar que a mesma referência de imagem resolva para artefatos diferentes e tornar rollback rastreável.

## Decisão

Renderizar runtime com repo@sha256 revisado e manter ECR imutável. Job Flyway separado usa artefato validado. Release local com preflight e SSM é caminho manual, enquanto CD/OIDC permanece pendente.

## Consequências e reavaliação

Digest identifica bytes, não comprova aprovação, assinatura ou ausência de vulnerabilidades. Registrar vínculo SHA→build→digest→rollout e conservar digest anterior. Rollback exige compatibilidade de schema e restauração de configuração; nunca desfazer migrations automaticamente.

## Evidências

[renderer](../../scripts/staging/render.sh), [release](../staging/release-local.md), [CI/CD](../fase3/cicd-strategy.md).
