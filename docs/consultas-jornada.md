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
