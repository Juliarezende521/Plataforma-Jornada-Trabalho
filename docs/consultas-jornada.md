# Histórico, detalhe e resumo próprio

Recorte RF08–RF11/RF13, RN08/RN09/RN11 e RNF13. A tela de jornada anterior foi fechada no head 61ad61c; esta etapa acrescenta consultas sem modificar eventos ou aceitar identidade do navegador. Regularização manual continua pendente: as respostas atuais descrevem somente eventos online originais.

## Contrato

- GET `/api/me/work-sessions?from=2026-10-01&to=2026-10-31&page=0&size=20`: datas ISO inclusivas pela `work_date` congelada, até 366 dias, page >= 0 e size 1–100. Defaults page=0/size=20. Parâmetros duplicados/desconhecidos e datas inválidas retornam 400. Identidade deriva da sessão; conta ativa/vínculo são revalidados. Ordenação work_date, abertura e ID decrescentes. Retorna página limitada, totalElements, observedAtUtc e resumo de todo o período, independente da página.
- GET `/api/me/work-sessions/{id}`: detalhe próprio, eventos originais pela sequência e intervalos de trabalho/pausa. ID alheio ou inexistente retorna o mesmo 404; ID malformado retorna 400. Sessão ausente 401; vínculo/conta desativada 403; falha temporária 503 ou erro interno 500 com correlação e sem SQL/identidade públicos.
- Durations ISO 8601 conservam precisão de 100 ns; soma encerrada exclui todas as jornadas abertas. Abertas têm duração provisória até Clock do servidor e contador de pendências separado. Jornada que cruza mês permanece integralmente no período da data de início. Não é folha, fechamento legal, horas extras ou banco de horas.

## Consistência e custo

A consulta adquire o mesmo lock de colaborador usado pela marcação. Isso mantém página, resumo e eventos consistentes enquanto uma marcação simultânea espera a transação. Todos os acessos do serviço revalidam autoria/conta no banco. Não depende do UUID ou dos botões.

Página: uma consulta de jornadas e uma consulta de eventos IN IDs, sem N+1 por jornada. Resumo: percorre lotes de 100 jornadas e soma pelo domínio, com precisão original e sem materializar o período inteiro. Entidades lidas são detached após cada lote para evitar crescimento do contexto JPA. Mantém o cálculo canônico e não cria total derivado persistido que precisaria ser revisado com regularizações.

Custo O(jornadas + eventos) no período, com duas consultas por lote; não há gargalo medido. Lock pode fazer uma marcação aguardar durante um resumo grande. Período/página são limitados, mas quantidade de eventos por jornada e jornadas no período não têm teto de negócio. Medir latência/contenção com massa representativa antes de definir escala ou SLA; snapshot isolation/projeções de totais podem ser avaliados se a medição justificar. O MVP não promete consulta ilimitada com latência fixa.

## IHC

Navegação por âncoras nativas permite chegar à jornada ou histórico sem destruir a recuperação. Filtros têm labels, orientação de intervalo inclusivo, erro associado aos campos e loading/feedback. Resultados identificam o período efetivamente consultado, mesmo se o formulário for alterado antes de paginar. Paginação reutiliza filtros aplicados. Detalhe e retorno preservam a página e direcionam foco. Não há polling ou requisição de histórico automática em cada marcação.

Fuso de visualização (referência, São Paulo, Amsterdã ou UTC) converte somente instantes via Intl, exibindo data e offset. Data de referência e duração continuam autoritativas e imutáveis. Detalhe explica pausas excluídas, intervalos incluídos e duração provisória. Estados de vazio/erro não fingem confirmação nem exibem um resumo antigo como resposta nova. Mudança de identidade cancela requisição e limpa dados; resposta 401 retorna ao login. Sem cache persistente, senha, token ou histórico em localStorage.

## Revisão e segunda análise

| ID / critério / local | Evidência, impacto inicial/final, ação | Possível falso positivo e confirmação adicional |
|---|---|---|
| H01 segurança/autorização — JourneyQueries | Filtros explícitos por colaborador e revalidação da conta/vínculo; IDs invisíveis recebem 404. Risco médio controlado por implementação e teste cruzado escrito | UUID/login isoladamente não provariam isolamento. Testar HTTP/SQL real com dois usuários e conta desativada antes de declarar validado |
| H02 desempenho — resumo e lock | Varredura por lotes/lock confirmados no código. Médio potencial → baixo no escopo atual, condicionado ao volume; sem gargalo medido. Evita N+1 e memória de período completo | Não tratar custo linear como falha comprovada. Falta carga representativa e medição de espera durante consultas grandes; sem SLA declarado |
| H03 qualidade/legibilidade — formatação | Extrai formatação de data/duração/instante compartilhada entre jornada e histórico; baixo → resolvido no código | Duplicação pequena não era falha funcional. Regressão Angular e navegador sustentam preservação do formato |
| H04 IHC/evidência — teste de login | Asserção genérica de ausência de qualquer form passou a encontrar o formulário de filtros. Baixo → corrigido usando #login-form; mesmas verificações de login/logout mantidas | Falha do seletor não demonstra login quebrado. Confirmar regressão completa, sem retirar asserções |
| H05 segurança/consistência visual — HistoryComponent | Cancelamento de subscription e limpeza por identidade, erro sem resumo antigo e filtros aplicados separados; médio potencial → controlado | Sem vazamento real demonstrado; UI não substitui autorização. Testes locais de troca durante requisição e erro, mais isolamento SQL, são necessários |

| H06 erros públicos — JourneyQueryController | Catch inicial abrangia chamada do serviço, podendo classificar NPE interno como filtro inválido. Baixo → resolvido: catch restrito ao parsing e teste que exige 500 genérico para falha interna | Mecanismo confirmado no código, sem NPE real observado. Não demonstra exploração; validar novo teste na CI |

Análise separada: sem innerHTML, HTML dinâmico confiado, SQL concatenado, upload ou novos caminhos de arquivo. Query parameters não escolhem autor/ordenação SQL. Nenhuma credencial adicional. DTOs não serializam JPA nem identidade/autor. Logs seguem erros genéricos/correlação. Essas observações não comprovam ausência global de XSS/injection nem auditoria de histórico Git/dependências.

Qualidade: domínio mantém transições/duração; store fornece lotes; serviço coordena escopo e snapshot; controller valida query; componente consulta/apresenta sem repetir cálculo de trabalho. Complexidade do resumo é justificada por necessidade concreta, sem cache ou novas abstrações genéricas. Comentários explicam lock/detach e limites.

## Validação da etapa

Testes escritos: detalhe em horário de verão e precisão, pausa final, relógio regressivo, limites inclusivos; HTTP/SQL com dois usuários, página de 1 item/resumo global, período vazio, filtros e conta desativada; conta de runtime restrita repete consulta própria. Angular: validação/loading, fuso, resumo global, filtros/paginação, detalhe/retorno, erro sem dados antigos, troca de identidade. Percurso de navegador ampliado em desktop/360: histórico, detalhe, fuso/offset, foco e reflow 320/1280, junto da regressão de marcações/recuperação.

Java local bloqueado antes de compilar por parent Maven indisponível. Build Angular local aprovado e 45 testes Angular aprovados (38 anteriores + 6 de consultas + 1 de apresentação temporal); listagem dos 4 percursos Playwright e diff/script checks aprovados. CI ainda deve receber evidência do head final. Testes escritos não são aprovados. Não cobre Safari/celular físico/tecnologia assistiva, participantes de usabilidade, carga ou revisões manuais futuras.

### Primeiro ciclo integrado

2ec2d3b: Foundation 38593652440 aprovado (58 backend/44 Angular). SQL/browser 38593652448: SQL aprovado (36 distintos), runtime restrito aprovado, 2 testes de autenticação aprovados; jornada desktop falhou no reflow de texto 200%/320 px antes de chegar ao novo histórico. O percurso mobile encontrou a jornada ainda aberta deixada pelo primeiro; não demonstra falha independente de marcação. Logs 115954688725 e captura conferidos.

H07 — IHC/reflow: overflow horizontal após ampliação do texto, impacto médio, confirmado pelo teste real. A origem no espaço dos controles nativos de datas é hipótese inicial; diagnóstico dos elementos foi acrescentado para confirmar o mecanismo. Reduzir padding responsivo da shell/cartão de histórico e inputs, mantendo fonte ampliada e testes; a correção ainda exige nova execução. Screenshot ampliado e diagnóstico dos bounding boxes adicionados, sem ocultar overflow nem relaxar o critério. Falso positivo de marcação mobile: teste anterior deixou estado aberto; cleanup agora encerra a jornada via API real nas contas fictícias, sem apagar originais, para projetos independentes. Revalidar todo o percurso.

## Correspondência com os oito critérios do desafio

| Critério | Evidência concreta desta etapa |
|---|---|
| Interpretação | Datas de referência congeladas, viagens/fusos, madrugada, virada de mês e pendências sem horas inventadas |
| Organização | Consulta separada de marcações, controller/serviço/store/DTO e componente/API/formatos |
| Qualidade | Testes de propriedade, filtros, precisão e erros; cálculo canônico sem cópia no Angular |
| Modelagem | Jornadas e eventos existentes preservados; não altera schema nem representa regularização ainda ausente |
| Decisões técnicas | Lock para consistência, lotes sem N+1, precisão original e limites com custo/risco explicitados |
| Comunicação | Contrato HTTP, revisão com autocrítica e rastreabilidade, status implementado versus validado |
| Experiência | Filtros claros, loading/vazio/erro, pendências separadas, retorno com filtros/foco e fusos explícitos |
| Boas práticas | Sessão/escopo no servidor, runtime restrito, DTOs, SQL parametrizado, CI e nenhum segredo em cache local |

Essa relação demonstra decisões e evidências; não garante nota ou aceite do avaliador. Regularização mínima ainda é parte P0 pendente.

Reteste f4cff75: Foundation 38594935660 aprovado (59 backend/45 Angular); SQL/browser 38594935564 com SQL e permissões aprovados, 3/4 percursos aprovados. Mobile concluiu histórico/detalhe/fuso e recuperação; desktop confirmou overflow apenas nos DIVs/labels/inputs history-from/history-to (right=340 em viewport=320). H07 permanece médio, confirmado; hipótese de espaço insuficiente refinada para tamanho transversal intrínseco dos campos de data em flex coluna. Restringir explicitamente width/max-width dos contêineres e max-width dos inputs; fonte ampliada e exigência sem overflow preservadas. Nova execução exigida.

## Fechamento do código validado

Head **48096ab**: Foundation **38596015082** e SQL/browser **38596014919**, ambos aprovados. Logs 115962815811/115962815835/115962815694: **59 backend, 45 Angular, 37 SQL distintos (1 migration + 36 agrupados), 4 percursos Chromium HTTPS**. Repetições com runtime restrito (incluindo histórico/consulta própria) não aumentam a contagem. Sem falhas/erros/skips nas suítes aprovadas. Testes de seed/reinício e permissões preservados sem novas concessões.

H01/H05: controle de acesso e identidade com evidência HTTP/SQL e Angular. H03/H04/H06: resolvidos e regressão aprovada. H02: risco condicionado ao volume, sem gargalo medido. **H07: médio → resolvido no contexto testado**, mantendo fonte ampliada e sem ocultar overflow; datas/contêineres agora têm limites explícitos. Bounding boxes e scrollWidth aprovados em desktop e mobile no roteiro. Não extrapolar para navegadores/dispositivos não testados.

Capturas finais de detalhe 320/1280 e texto 200%/320 inspecionadas e preservadas em [evidências](evidencias/consultas/README.md). Native date format depende do locale do navegador; datas de resultado usam pt-BR e instantes carregam offset. Seletor nativo mantém opções acessíveis, mas seu texto visível pode abreviar em texto ampliado. Pesquisa com participantes permanece pendente.

Este registro corresponde ao código 48096ab. O commit documental posterior apenas preserva status/capturas; conferir seus checks na PR antes de integrar. PR #4 continua em rascunho, sem merge. Próximo recorte: regularização mínima de saída esquecida, com solicitação/decisão auditada e autorização; aceite global e matriz restante de dispositivos ainda pendentes.
