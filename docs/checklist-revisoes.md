# Protocolo de revisão para todas as próximas etapas

Solicitado pela desenvolvedora em 12/10/2026. Este documento preserva os critérios enviados na conversa e deve ser consultado antes de implementar uma etapa e novamente antes de considerar sua entrega validada. São instruções de análise e critérios para definir testes; não são testes automatizados já executados nem garantia de ausência de falhas.

## 1. Aderência ao desafio e contexto

- Conferir enunciado, RF/RNF, regras assumidas, histórias e critérios de aceite da etapa.
- Revisar os oito critérios: interpretação do cenário, organização/estrutura, qualidade de código, modelagem, decisões técnicas, comunicação técnica, experiência de uso e boas práticas.
- Considerar Brasil/Europa, trabalho remoto, viagens, UTC/IANA, madrugada, pausas, pendências e integridade de registros quando aplicáveis.
- Seguir o checklist de dispositivos/contextos em [validacao.md](validacao.md), o plano de [IHC](experiencia-usuario.md) e as decisões canônicas de [requisitos.md](requisitos.md).
- Não inventar regras trabalhistas, requisitos da empresa, resultados de pesquisa ou evidências de execução.

## 2. Qualidade do código

Avaliar em cada etapa:

- [ ] Coesão.
- [ ] Duplicação.
- [ ] Acoplamento.
- [ ] Complexidade.
- [ ] Oportunidades de refatoração.
- [ ] Responsabilidade das funções e classes.

Classificar cada problema como baixo, médio ou alto impacto, explicando evidência e efeito no contexto real. Quando refatoração estiver no escopo autorizado, apresentar a versão refatorada e explicar as principais alterações; preservar contratos e acrescentar/reexecutar os testes pertinentes. Uma solicitação apenas de análise não autoriza correções/publicação automaticamente.

## 3. Segurança — análise separada

Analisar exclusivamente segurança nesta seção. Procurar vulnerabilidades relacionadas a:

- [ ] XSS.
- [ ] Autorização.
- [ ] Autenticação.
- [ ] SQL Injection.
- [ ] Validação de entrada.
- [ ] Manipulação de arquivos.
- [ ] Utilização insegura de APIs.
- [ ] Exposição de informações sensíveis.

Diferenciar vulnerabilidades confirmadas de possíveis riscos que precisam de investigação. Explicar causa, pré-condições, impacto e alcance de cada problema. Separar código de produção, perfis locais/de avaliação e fixtures de CI. Não chamar uma configuração restrita de teste de vulnerabilidade de produção sem demonstrar o caminho de exposição. Não presumir autorização por recurso por existir login ou UUID; verificar no servidor quando houver recurso implementado. Ausência de achado não é prova de segurança.

## 4. Desempenho — evidência, não preferência de estilo

Identificar:

- [ ] Loops ineficientes.
- [ ] Possíveis gargalos.
- [ ] Consultas repetidas.
- [ ] Operações desnecessárias.
- [ ] Processamento redundante.
- [ ] Problemas de complexidade.

Não propor otimizações apenas por preferência de estilo. Para cada sugestão, explicar qual problema de desempenho resolve, onde está a evidência e como medir benefício/correção. Distinguir custo visível no código de gargalo comprovado com carga. Não reduzir proteção de senha/CSRF, integridade transacional ou imutabilidade por conveniência de desempenho. Não tratar duração de testes como latência da aplicação.

## 5. Legibilidade — manutenção

Analisar:

- [ ] Duplicação.
- [ ] Comentários.
- [ ] Organização.
- [ ] Clareza da lógica.
- [ ] Nomes de funções.
- [ ] Nomes de variáveis.
- [ ] Tamanho das funções.

Priorizar melhorias que realmente facilitem manutenção, revisão de regras e diagnóstico. Não dividir funções/classes apenas por contagem de linhas, nem criar abstrações sem consumidores e necessidade concretos. Distinguir problema funcional, risco de manutenção e preferência de formatação.

## 6. Segunda análise obrigatória da própria revisão

Para **cada achado** da revisão da etapa e cada achado anterior relevante que esteja sendo reavaliado:

1. Identificar possíveis falsos positivos e condições que invalidariam a conclusão.
2. Reclassificar severidade se necessário, mostrando classificação anterior/final e justificativa.
3. Verificar se existe evidência suficiente para afirmar que o problema realmente existe; distinguir mecanismo confirmado de impacto/exploração não demonstrados.
4. Explicar quais informações ou testes adicionais seriam necessários para confirmar o problema.

Não repetir como problema atual algo já corrigido. Marcar resolvido, não aplicável ou inconclusivo explicitamente. Referenciar arquivo/função, commit e teste/medição quando disponíveis. Não aumentar a lista artificialmente para dar aparência de revisão mais completa.

## 7. Testes, evidências e fechamento da etapa

- [ ] Definir testes essenciais da mudança, regressão do que já funciona e itens que dependem de funcionalidades futuras.
- [ ] Reutilizar testes existentes quando adequados; não duplicar casos apenas para aumentar contagem.
- [ ] Executar build e testes aplicáveis; SQL Server real para constraints/migrations/concorrência. Não declarar H2 equivalente.
- [ ] Conferir resultados/logs, commit, ambiente, falhas e testes ignorados; repetição em outro workflow não cria testes distintos.
- [ ] Registrar bloqueios de ambiente separados de falhas de código, sem apresentar teste escrito como teste aprovado.
- [ ] Conferir documentação, links, diff e ausência de segredos nos arquivos destinados à entrega.
- [ ] Preservar a autorização para commits, publicação, PR e merge; não relaxar checks/proteções para obter aprovação.
- [ ] Para merge condicionado à CI, conferir head final da PR e sucesso das verificações pertinentes; parar se houver proteção/permissão bloqueando.

Formato mínimo do resultado por achado: ID, critério, localização, causa, evidência, impacto inicial/final, confirmado/risco/inconclusivo/resolvido, possível falso positivo, validação adicional e ação proposta/autorizada. Escopo da análise deve ser proporcional à etapa, sem afirmar aceite global do desafio antes do produto completo.

## Ponto de retomada registrado em 12/10/2026

PRs #1/#2 integradas; fonte atual no momento deste registro: main 2a5aa34. A segunda análise específica de segurança/desempenho/legibilidade foi iniciada sem alterar o código e ainda precisa ter seu relatório final apresentado. npm audit --omit=dev não reportou vulnerabilidades conhecidas nas dependências npm de produção naquele momento; não cobre Java, dependências de desenvolvimento ou toda a segurança. Retomar esse relatório antes da etapa de persistência/migrations. Este parágrafo é um checkpoint histórico, não status automático do projeto.
