# Instruções para trabalho neste repositório

Antes de implementar, revisar ou encerrar cada etapa, ler e aplicar integralmente `docs/checklist-revisoes.md`, conforme solicitação da desenvolvedora. O protocolo inclui aderência ao desafio/contexto, qualidade, segurança, desempenho, legibilidade e uma segunda análise dos próprios achados para identificar falsos positivos e reclassificar severidades.

Usar `docs/requisitos.md` como baseline canônica e consultar regras/arquitetura, histórias, validação e IHC relevantes à etapa. Manter rastreabilidade e distinção entre planejado, implementado e validado. O domínio de jornada não comprova autorização, idempotência ou concorrência no banco por si só.

Em cada resultado, registrar evidências e limitações, não prometer ausência de erros. Não propor otimizações sem um problema demonstrável; não enfraquecer segurança ou integridade para passar testes. Não publicar segredos ou dados reais de colaboradores.

Uma solicitação de análise não autoriza implementação de correções ou alterações externas automaticamente. Respeitar a autorização vigente para commits/PRs/merge, preservar mudanças existentes e não contornar permissões, proteções de branch ou checks. Quando houver merge condicionado à CI, verificar o head final e todas as verificações aplicáveis antes de integrar.
