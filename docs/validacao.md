# Plano de validação, riscos e evidências

Status: T01 e T23 aprovados para autenticação implementada na CI 38090975052 com SQL Server real/HTTPS/Chromium. T02/T18/T20 possuem evidências parciais de acesso, schema de contas e viewports; jornada completa e acessibilidade continuam pendentes. Demais cenários de negócio não foram aceitos. Evidências: [validacao-integrada.md](validacao-integrada.md).

## Estratégia de testes

Antes de implementar/encerrar cada etapa, aplicar o [protocolo de revisão recorrente](checklist-revisoes.md), incluindo segunda análise de cada achado para verificar falsos positivos, evidências e severidade. Esse protocolo define análises e testes a selecionar; não é evidência de execução automática.

Plano de IHC, inspeção inicial e roteiro de participantes: [experiencia-usuario.md](experiencia-usuario.md). T20 tem evidência de viewport, mas não aprovação de teclado, leitor de tela ou teste com usuários. Histórias HU01–HU18 mapeiam os cenários abaixo sem afirmar implementação das histórias futuras.

- Domínio: relógio fixo, sequências, cálculo e regras de fuso sem dependência da hora atual.
- API/segurança: sessão real de teste, DTOs, status HTTP, CSRF e isolamento entre usuários.
- Integração: SQL Server real para migrations, tipo temporal, constraints e concorrência; não declarar H2 equivalente.
- Interface: fluxo completo, responsividade, teclado, estados vazios, erros e timeout.
- Execução: clone limpo e banco vazio, usando exclusivamente os passos publicados.

Evolução na PR #1: modelo online unitário compilado/testado e regressão de autenticação repetida com sucesso, conforme [modelo-jornada.md](modelo-jornada.md). Commit de código/ajuste 2d15a39, Foundation checks 38199549465 e SQL Server/browser 38199549481. Isso não aprova os cenários completos de jornada abaixo: persistência/API/interface de jornada ainda ausentes.

## Casos de teste rastreáveis

| ID | Requisito | Cenário | Resultado esperado |
|---|---|---|---|
| T01 | RF01/RF02 | Login válido, inválido e logout | Sessão correta; erro genérico; sessão antiga rejeitada após logout |
| T02 | RNF03/RNF04 | Sem sessão; sem CSRF; token inválido | 401 sem sessão; mutações autenticadas sem CSRF válido rejeitadas |
| T03 | RF03–RF06 | 09h entrada, 12h pausa, 13h retorno, 18h saída | Quatro eventos e duração de 8h |
| T04 | RF12 | Duas pausas | Ambas descontadas |
| T05 | RF03–RF06 | Retorno sem pausa, duas entradas ou nova saída após encerramento com outra chave | 409, sem evento ou alteração parcial |
| T06 | RF12/RNF07 | Entrada 22h e saída 06h seguinte | 8h sem pausas; data de referência do início |
| T07 | RF11/RNF07 | Mesmos instantes consultados em Brasil e Amsterdam | Horários diferentes; duração e data de referência idênticas |
| T08 | RNF07 | Europe/Amsterdam, 29/03/2026 00:30Z até 02:30Z | 2h reais, apesar do salto no horário local |
| T09 | RNF07 | Europe/Amsterdam, 25/10/2026 00:30Z até 02:30Z | 2h reais, apesar da repetição de horários locais |
| T10 | RF10 | Jornada entre último e primeiro dia de meses distintos | Total atribuído ao mês da data de início |
| T11 | RF13 | Jornada sem saída | Pendência separada do consolidado; nova entrada rejeitada |
| T12 | RF14 | Repetição após resposta perdida | Mesma chave devolve mesmo resultado; um único evento |
| T13 | RF14 | Chave reutilizada com outro tipo | 409 e preservação do resultado original |
| T14 | RNF06 | Duas entradas simultâneas com chaves diferentes | Só uma jornada aberta; perdedora recebe conflito |
| T15 | RNF06 | Duas transições incompatíveis simultâneas | Uma confirmada; outra reavaliada/rejeitada; estado e eventos coerentes |
| T16 | RNF03 | Usuário A solicita detalhes de B ou injeta EmployeeId | Não acessa/gera registros de B |
| T17 | RNF02/RNF05 | Falha transacional e reinício | Sem gravação parcial; registros confirmados continuam persistidos |
| T18 | RNF09 | SQL Server vazio | Flyway cria schema; Hibernate validate passa; seed não duplica |
| T19 | RNF07 | Round-trip JDBC de instante e offset | Instante normalizado e precisão preservada conforme decisão |
| T20 | RNF01/RNF12 | Celular 320/360 CSS px, tablet, desktop, teclado e zoom | Fluxos utilizáveis, sem cortes; foco visível, alvos de toque adequados e mensagens acessíveis |
| T21 | RNF08/RNF13 | Tipo desconhecido, data inválida e paginação excessiva | 400 sem stack trace, com erro explicativo |
| T22 | RF07/RF14 | Timeout após commit e nova tentativa | Interface reconcilia estado com mesma chave; não duplica |
| T23 | RNF04 | Login/logout e emissão do CSRF | Fluxo continua válido após renovação; sessão/token obsoletos não autorizam mutação |
| T24 | RNF07 | Relógio retorna instante anterior ao último evento | Erro controlado sem duração negativa |
| T25 | RF15/RF16 P1 | Gestor consulta equipe externa; RH consulta autorizado | Escopo de acesso correto, se implementado |
| T26 | RF17/RF18 recorte P0 | Saída esquecida aprovada, rejeitada e autoaprovação; acesso fora de escopo | Original preservado; revisão efetiva correta; rejeição com motivo; autoaprovação e escopo externo rejeitados |
| T27 | RF06/RF14; RN04/RN05 | Entrada 09h, pausa 12h e saída 12h30; repetir saída com a mesma chave | Jornada encerrada, 3h trabalhadas e 30min de pausa; apenas 3 eventos, sem retorno artificial; repetição devolve resultado original |
| T28 | RF13/RF17/RF18 P0 | Jornada aberta, pedido pendente e aprovação de saída esquecida | Nova entrada bloqueada até aprovação; decisão/revisão/encerramento/auditoria atômicos; histórico distingue manual/online; total efetivo correto e nova entrada liberada |
| T29 | RF17/RNF07 | Saída anterior ao último evento, futura, local inexistente ou ambígua; madrugada/pausa final | Propostas inválidas rejeitadas; ambiguidade exige offset válido; proposta válida mantém data de referência e aplica RN05 sem retorno artificial |
| T30 | RF17/RF18; RNF05/RNF06 | Pedidos/decisões repetidos ou simultâneos; cancelamento; saída online; falha transacional | Um pedido pendente por jornada; revisão aplicada no máximo uma vez; decisão obsoleta rejeitada; saída online cancela pedido superado; sem gravação parcial; rejeição/cancelamento permitem novo pedido |
| T31 | RF14/HU14 | Resposta perdida após commit; reload e recuperação do diário | Consulta autenticada confirma mesmo evento/instante; nenhum POST automático nem duplicação; atualizar estado antes de liberar ações |
| T32 | RF14/RN01 | Falha antes do commit; recibo ausente enquanto primeira requisição está em andamento; repetição explícita | Mesma chave/payload; no máximo um resultado aplicado; horário do processamento aceito, nunca do clique; ausência do recibo não é sucesso/falha definitiva |
| T33 | RF14/RNF03/RNF11 | Armazenamento falha/é alterado, troca de conta, logout e novo login | Falha do diário bloqueia novo envio; entradas inválidas não autorizam mutação; conta diferente não consulta/reexecuta tentativa; mesma conta recupera; nenhum segredo no diário |
| T34 | RF14/RNF06 | Duas abas/dispositivos; requisição antiga aponta jornada anterior; conflito definitivo repetido | Constraints/versão mantêm integridade; comando antigo não afeta nova jornada; recibo rejeitado não vira sucesso ao repetir; coordenação local não é tratada como proteção do banco |

T27 é planejado, não executado. Complementar com múltiplas pausas, saída em pausa atravessando meia-noite/mudança de offset e concorrência entre retorno/saída (T15): uma transição vence, a outra é reavaliada/rejeitada conforme o estado confirmado, sem gravação parcial ou duração negativa.

T26/T28–T30 são planejados, não executados. RF17/RF18 P0 cobrem apenas saída esquecida, sem aceitação de outras correções P1. Verificar também que a autoridade de aprovador não permite consulta geral RF16 e que o seed de avaliação provisiona escopo/identidades distintos sem segredos.

T31–T34 são planejados, não executados. Testar falhas reais/controladas antes e depois do commit, não somente simular erro de componente. Conferir recibos/eventos no SQL Server, formato mínimo do diário e ausência de credenciais; documentar limitação de recuperação após limpeza do navegador ou em outro dispositivo.

Datas de horário de verão são exemplos determinísticos a validar com ZoneId e a base de fusos da versão de Java utilizada; não dependem de esperar a data ocorrer.

Para concorrência, usar barreira de sincronização ou disparo coordenado em múltiplas transações. Testar o estado final do banco, não apenas status HTTP. Registrar IDs e contagens esperadas sem dados pessoais reais.

## Roteiro do avaliador

1. Executar passos do README em ambiente suportado e SQL Server vazio.
2. Entrar com colaborador A fictício; conferir fuso e estado inicial.
3. Registrar entrada, pausa, retorno e saída; consultar detalhe e cálculo.
4. Consultar período e mudar fuso de visualização, conferindo invariantes.
5. Entrar com colaborador B; verificar que não vê registros de A.
6. Executar suíte documentada, incluindo migrations, CSRF, idempotência e concorrência.
7. Reiniciar serviços preservando volume; conferir dados e autenticar novamente se necessário.
8. Ler limitações e conferir recursos P1 somente quando declarados implementados.

## Registro de evidências

| Evidência | Conteúdo necessário | Estado atual |
|---|---|---|
| Execução limpa | Commit, ambiente, versões, comandos exatos, logs sanitizados e resultado | Checkout limpo/CI de autenticação aprovado; execução local/produto completo pendente |
| Testes automatizados | Comando, commit, testes/falhas e relatório gerado | PR #1: 24 backend (12 base + 12 domínio) + 14 frontend + 2 SQL Server + 2 percursos E2E de autenticação aprovados no commit 2d15a39; jornada completa pendente |
| Testes manuais | T20/T22 e demais fluxos, ambiente e resultado observado | Pendente |
| Banco | Histórico Flyway, constraints e contagens relevantes | V1, persistência/constraints de contas e seed repetido aprovados; jornada pendente |
| Segurança | Resultado de acesso cruzado, CSRF, logout e inspeção de segredos | Identidades isoladas, CSRF/logout/cookies/expiração aprovados; autorização de jornada pendente |
| Interface | Capturas fictícias e descrição do fluxo | Quatro capturas de autenticação revisadas/preservadas; demais telas pendentes |

Não publicar cookies, tokens, strings de conexão com senha ou dumps reais. Evidência de uma execução local não comprova prontidão de produção.

## Registro de riscos

Classificação qualitativa proposta, sem estimativas estatísticas.

| ID | Risco | Probabilidade/impacto | Mitigação | Gatilho e resposta |
|---|---|---|---|---|
| R01 | Ambiente ARM ou recursos insuficientes para SQL Server | Desconhecida/alto | Verificar máquina antes da implementação; conexão externa configurável | Banco não inicia: usar host suportado e documentar caminho |
| R02 | Prazo curto e escopo excessivo | Desconhecida/alto | Núcleo P0 primeiro; P1 condicionado | Núcleo incompleto: suspender complementos |
| R03 | Configuração incorreta de Spring Security/Angular | Média/alto | Testar sessão, CSRF e logout cedo | Login aparente sem sessão: corrigir fluxo antes de implementar telas restantes |
| R04 | Inconsistência temporal ou de precisão JDBC | Média/alto | Testes UTC, DST e round-trip SQL Server | Divergência: ajustar mapeamento antes de publicar totais |
| R05 | Duas marcações concorrentes passam | Média/alto | @Version, transação e constraints reais | Estado final inválido: bloquear aceite do núcleo |
| R06 | Backend Java menos alinhado à empresa | Certa/moderado | ADR transparente e execução clara | Questionamento técnico: explicar trade-off, sem alegar superioridade universal |
| R07 | Saída esquecida bloqueia novas jornadas enquanto aguarda aprovador | Média/moderado | Regularização mínima P0 auditada e estado/motivo explícitos | Sem aprovação: manter pendência e declarar espera; não inventar saída nem prazo de atendimento |
| R08 | Credenciais fictícias ativadas fora da avaliação | Baixa/alto | Perfil exclusivo e verificação de inicialização | Seed ativo em produção: impedir inicialização insegura |
| R09 | Dependências incompatíveis ou migration falha | Média/alto | Versões fixadas e teste em banco vazio | Falha de criação: corrigir migration ainda não publicada e repetir validação |
| R10 | Documentação diverge do código | Média/moderado | Conferência por pacote e execução do README | Comando/fluxo diverge: atualizar antes da entrega |

## Checklist de encerramento

### Dispositivos e contextos — aceite antes da entrega

Checklist obrigatório do projeto para demonstrar a exigência do enunciado de uso em diferentes dispositivos e contextos. Todos os itens abaixo permanecem abertos para o produto completo. A autenticação já validada em Chromium desktop/360 px é evidência parcial, não aceite de jornada ou de dispositivos reais. Não marcar como aprovado apenas porque o cenário está documentado ou automatizado sem execução.

| Item | Requisitos/testes | Critério de aceite e evidência necessária |
|---|---|---|
| [ ] Celular, tablet e desktop | RNF01/RNF12; T20 | Executar login, marcações e consultas em 320/360 CSS px, tablet e desktop; registrar dimensões, orientação e capturas. Sem cortes/rolagem horizontal geral, textos legíveis e controles utilizáveis por toque |
| [ ] Navegadores e dispositivo físico | RNF01/RNF09; T20 | Executar fluxos essenciais em Chromium e Safari/WebKit, incluindo celular físico quando disponível; registrar navegador, versão, sistema e dispositivo. Emulação/WebKit não comprova Safari no iPhone. Se houver bloqueio de acesso ao ambiente, registrar como não testado, nunca aprovado |
| [ ] Teclado e acessibilidade | RNF12; T20 | Completar tarefas por teclado, conferir foco após login/logout/erros, labels e mensagens com leitor de tela; verificar contraste, texto a 200% e reflow a 320 CSS px. Registrar ferramenta, passos e resultados; não declarar conformidade integral sem avaliação |
| [ ] Presencial e remoto | RF01–RF09; RNF03/RNF04 | Usar o mesmo fluxo autenticado pelo navegador, sem depender de rede do escritório, localização ou identificador fornecido pelo cliente; ambiente de avaliação acessível com HTTPS válido e acesso aos registros próprios |
| [ ] Viagem e fusos distintos | RF11/RNF07; T07–T09/T19 | Alterar fuso de dispositivo/visualização com jornadas existentes; manter instantes, duração, data de referência e fuso histórico. Exibir claramente data e fuso, distinguindo horários locais repetidos |
| [ ] Madrugada e fechamento de período | RF10/RF12/RF13; T04/T06/T10/T11 | Conferir múltiplas pausas, meia-noite, mudança de mês e pendências; cálculo correto, datas explícitas e totais provisórios separados dos consolidados |
| [ ] Rede lenta, indisponível e resposta perdida | RF07/RF14; RNF05/RNF06; T12/T13/T22 | Mostrar envio em andamento, não declarar sucesso sem confirmação e não duplicar evento. Testar falha antes/depois do commit, repetição e reload com reconciliação da mesma tentativa; conferir contagens no banco. Informar que não há marcação offline |
| [ ] Sessão expirada durante uso | RF01/RF02; RNF04; T01/T23 | Expirar sessão durante marcação/consulta; orientar novo login, não enviar automaticamente uma nova marcação nem mostrar sucesso falso. Reconciliar resultado desconhecido antes de nova tentativa; conferir sessão, CSRF e estado persistido |

Para cada item, registrar commit, ambiente, passos, resultado esperado/observado e evidência sanitizada. Falhas devem ser corrigidas e retestadas antes do aceite. Ambientes indisponíveis devem permanecer explicitamente pendentes na revisão final; não presumir compatibilidade universal. Usabilidade com participantes segue o plano de [experiencia-usuario.md](experiencia-usuario.md), sem inventar participantes ou resultados.

Todos os requisitos explícitos funcionais; testes críticos aprovados; regras assumidas documentadas; API e interface coerentes; README executado desde zero; licença de desenvolvimento e arquitetura do banco esclarecidas; sem segredos; repositório público preparado. Enviar o link ao endereço do enunciado somente após concluir a entrega e por ação autorizada naquela etapa.

## Consultas próprias — 14/10/2026

Implementação e evidências de T07/T09/T10/T11/T16/T21 no escopo de histórico/detalhe/resumo em [consultas-jornada.md](consultas-jornada.md). T20 recebe evidência parcial de teclado/reflow; teste não certifica toda acessibilidade. Filtros por data de referência, propriedade, conta ativa, limite de período/página e soma com precisão possuem testes próprios. Demais dispositivos/contextos e regularização permanecem pendentes.
