# EAP — Plataforma de Jornada de Trabalho

Status: sem estimativa de prazo aprovada. Base 1.2.1 implementada; schema de contas/seed de 1.2.2/1.2.3 e autenticação 1.3.1 aprovados em SQL Server real/Chromium na CI 38090975052. Schema completo de jornada, execução local do avaliador e acessibilidade/dispositivos físicos continuam pendentes. 1.3.2 não está concluído sem os recursos de jornada. Evidências atualizadas: [validacao-integrada.md](validacao-integrada.md).

## Estrutura de entregas

A EAP decompõe o produto e a entrega em pacotes verificáveis. Não é um cronograma: datas e esforço só serão estimados após conhecer prazo e ambiente.

| Código | Pacote pai | Entrega | Resultado esperado |
|---|---|---|---|
| 1 | — | Plataforma de Jornada de Trabalho | Solução e repositório avaliáveis |
| 1.1 | 1 | Especificação | Escopo, requisitos, regras e decisões |
| 1.1.1 | 1.1 | Requisitos e critérios | Levantamento rastreável ao enunciado |
| 1.1.2 | 1.1 | Modelo e decisões | Arquitetura, entidades e regras temporais |
| 1.1.3 | 1.1 | Contexto e histórias | Stakeholders, hipóteses e critérios de aceite rastreáveis |
| 1.1.4 | 1.1 | Planejamento de interação | Fluxos, acessibilidade e plano de avaliação de IHC |
| 1.2 | 1 | Base executável | Backend, frontend e banco configurados |
| 1.2.1 | 1.2 | Estrutura de código | Projetos, wrappers e configurações por ambiente |
| 1.2.2 | 1.2 | Schema versionado | Flyway e constraints em SQL Server |
| 1.2.3 | 1.2 | Ambiente de avaliação | Conexão, seed fictício e instruções |
| 1.3 | 1 | Acesso seguro | Login, sessão e isolamento por usuário |
| 1.3.1 | 1.3 | Autenticação | Login/logout/CSRF funcionais |
| 1.3.2 | 1.3 | Autorização | Consultas e operações próprias protegidas |
| 1.4 | 1 | Núcleo de jornada | Registro e cálculo consistentes |
| 1.4.1 | 1.4 | Eventos e estados | Entrada, pausa, retorno e saída |
| 1.4.2 | 1.4 | Integridade | Transação, concorrência e idempotência |
| 1.4.3 | 1.4 | Datas e duração | UTC, fusos, madrugada e múltiplas pausas |
| 1.5 | 1 | Consulta e interface | Experiência web completa |
| 1.5.1 | 1.5 | API de consulta | Atual, histórico, detalhe e resumo |
| 1.5.2 | 1.5 | Interface de jornada | Marcação responsiva com feedback |
| 1.5.3 | 1.5 | Interface de histórico | Filtros, fusos, detalhes e pendências |
| 1.6 | 1 | Qualidade e entrega | Evidências e execução reproduzível |
| 1.6.1 | 1.6 | Testes automatizados | Domínio, segurança, banco e concorrência |
| 1.6.2 | 1.6 | Verificação manual | Celular, teclado, erros e roteiro |
| 1.6.3 | 1.6 | Repositório final | README, documentos, evidências e revisão de segredos |
| 1.7 | 1 | Complementos condicionais P1 | Somente após estabilizar o núcleo |
| 1.7.1 | 1.7 | Consulta de gestor/RH | Escopo de acesso validado |
| 1.7.2 | 1.7 | Correções auditadas | Solicitação, decisão e revisão efetiva |

## Dicionário dos pacotes

| Pacote | Requisitos principais | Dependências | Aceite |
|---|---|---|---|
| 1.1 | Todos | Enunciado | Regras inferidas e exclusões explícitas; oito critérios de avaliação mapeados |
| 1.2 | RNF02/RNF09 | 1.1 | Schema criado por Flyway; Hibernate validate; seed só em avaliação; conexão demonstrada |
| 1.3 | RF01/RF02; RNF03/RNF04 | 1.2 | Sessão/CSRF/logout e dois usuários testados |
| 1.4 | RF03–RF06/RF12–RF14; RNF05–RNF07 | 1.2/1.3 | Sequências, duração, repetição e concorrência comprovadas |
| 1.5 | RF07–RF11/RF13; RNF01/RNF12/RNF13 | 1.3/1.4 | Consulta isolada e paginada; interface com loading/erro/vazio/sucesso |
| 1.6 | RNF08–RNF14 | Entregas incrementais de 1.2–1.5 | Testes aprovados e README reproduzido desde banco vazio |
| 1.7.1 | RF15/RF16 | Núcleo estável | Permissões de equipe e RH testadas |
| 1.7.2 | RF17/RF18 | Núcleo estável e perfil RH | Registro original preservado; sem autoaprovação; cálculo usa revisão válida |

Qualidade ocorre em cada pacote, embora as evidências consolidadas pertençam a 1.6. Não deixar segurança ou testes somente para o final.

1.1.3/1.1.4 possuem documentos iniciais em [stakeholders.md](stakeholders.md), [historias-usuario.md](historias-usuario.md) e [experiencia-usuario.md](experiencia-usuario.md). Isso não conclui 1.6.2: faltam testes de usabilidade com participantes e verificação de acessibilidade/dispositivos. Rever UX05/UX06 e recuperação HU14 antes dos pacotes de jornada dependentes.

## Sequência sugerida de trabalho

1. Especificação e verificação do ambiente SQL Server.
2. Base executável, migrations e autenticação com consulta própria simples.
3. Fluxo vertical de entrada até saída, com cálculo e testes.
4. Pausas, fusos, idempotência, concorrência e histórico.
5. Interface responsiva, recuperação de erros e revisão de acesso.
6. Execução limpa, documentação final e evidências.
7. Complementos P1 conforme prazo, sem comprometer o núcleo.

Não há duração, responsável externo ou caminho crítico calculado neste momento. Desenvolvedora responsável: Julia. Revisão técnica de planejamento e implementação: assistida nesta conversa. A empresa não participou da aprovação destes pacotes.
