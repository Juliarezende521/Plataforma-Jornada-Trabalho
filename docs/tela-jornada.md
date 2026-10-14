# Tela de jornada, IHC e recuperação

Etapa iniciada na PR #3 e consolidada na PR #4. Escopo: RF03–RF07 e recuperação de RF14/HU14, mantendo o backend como autoridade. Implementação Angular integrada à API; histórico/detalhe/resumo e regularização P0 ainda pendentes. O prazo inicialmente informado para 14/10 foi flexibilizado pela desenvolvedora: qualidade e validação antes de uma entrega apressada.

## Decisões de IHC e conteúdo estudado

| Princípio | Aplicação na tela | Verificação |
|---|---|---|
| Consistência e reconhecimento | Registrar entrada, Iniciar pausa, Retomar trabalho e Encerrar jornada; ações dependem do estado confirmado | Estados vazio, WORKING e PAUSED; nenhum botão exige memorizar código de evento |
| Feedback e tempo de resposta | Consultando, conferindo, registrando e resultado confirmado/recusado; `aria-busy` e região de status | Bloqueio enquanto requisitando; só recibo do servidor confirma resultado |
| Prevenção de erros | Confirma identidade e estado antes do comando; bloqueia nova ação com tentativa incerta | Double-click, estado alterado, conta diferente, diário indisponível |
| Controle do usuário | Atualizar jornada e consultar resultados; repetição exige ação explícita, sem POST automático | Falha/reload/404 não dispara reenvio; informa novo horário se tentativa não foi gravada |
| Fechamento da tarefa | Confirmação textual e instante servidor, inclusive após encerrar jornada | GET seguinte pode falhar sem apagar o resultado já conhecido |
| Navegação e teclado | Elementos nativos, headings/labels, ordem DOM, foco visível; foco no título após ações que removem o botão | Login/logout e marcação por teclado; atualização passiva de outra aba não rouba foco |
| Cores e acessibilidade | Estado e resultado por texto; contraste do sistema existente; botões de pelo menos 44 px e quebra responsiva | Reflow 320/360/768/1280 e texto ampliado; não equivale a certificação WCAG |
| Contexto internacional | UTC convertido no fuso IANA da jornada, offset visível e data de referência explícita | Viagem não modifica jornada antiga; duração provisória não é total fechado/folha |

Sem modal de confirmação em toda marcação: o fluxo frequente usa estado claro, revalidação e recibo. Estado não muda de forma otimista. O contador não inventa segundos locais: apresenta duração provisória até a última consulta, com instante e botão de atualização. Sem polling contínuo que prolongue a sessão ou faça consultas redundantes. Português é a escolha atual; idiomas europeus e teste com usuários reais ainda precisam de avaliação.

## Recuperação e segurança

Diário `work-journey.online.v1.<UUID>` em localStorage: somente version, requestKey, userId, employeeId, type e expectedWorkSessionId. Nenhuma senha, cookie, CSRF, nome, email ou timestamp. Cada intenção tem sua própria chave, sem sobrescrever outra aba. Escrita e releitura devem passar antes do POST. Conteúdo é validado como não confiável; chave não autoriza leitura/marcação. Sem conexão não há registro offline nem fila de envio posterior.

Antes de novas ações: confirmar identidade no servidor, reconciliar todos os recibos próprios visíveis e consultar jornada. Conta diferente não recupera nem reexecuta diário alheio. Falha mantém tentativa; reload usa GET e só limpa após recibo terminal validado. 404 não prova falha nem encerra uma transação em andamento. Repetição consulta primeiro e mantém chave/payload; backend mantém lock/constraints/versão. KEY_REUSED não permite descartar a tentativa como recibo terminal. 401 solicita nova autenticação; 403 não dispara reenvio automático. Logout não apaga intenções incertas, permitindo recuperação com a mesma conta.

StorageEvent bloqueia ações e reconcilia alterações de outras abas. Isso é coordenação auxiliar, sem promessa de mutex entre abas/dispositivos; SQL Server continua responsável pela integridade. LocalStorage pode ser apagado ou alterado; conteúdo inválido bloqueia envio e não é removido automaticamente. Limpeza do navegador/novo dispositivo pode impedir recuperar a chave; histórico e fluxo de suporte ainda precisam ser concluídos. Uma resposta perdida pós-commit é validada por recibo original e ausência de POST no reload, não por assumir sucesso de um timeout.

A camada HTTP Angular envia URLs relativas e CSRF somente em memória. DTO não envia identidade ou horário. Sem innerHTML, upload, caminhos de arquivo ou SQL recebido pelo frontend. Proteção real contra autorização/SQL Injection permanece na API parametrizada, não em botões ou UUIDs. Segredos de avaliação permanecem externos ao Git. `/api/status` anuncia JOURNEY_ONLINE fora de bootstrap; bootstrap continua FOUNDATION/false. Esse endpoint informa modo do recurso e liveness, não prova disponibilidade do SQL nem aprovação completa do desafio.

## Qualidade, desempenho e segunda análise

| ID / localização / critério | Causa/evidência, impacto inicial → final | Segunda análise e validação adicional |
|---|---|---|
| U01 — JourneyComponent/connect, desempenho/manutenção | Nova instância da mesma identidade poderia repetir inicialização/GET; mecanismo visível na versão de trabalho. Médio potencial → resolvido: inicializa uma vez por identidade e efeitos não rastreiam leituras incidentais do store | Não houve benchmark ou gargalo observado em produção. Teste de input com mesmos IDs impede nova consulta; manter métricas futuras antes de cache/otimização |
| U02 — botões condicionais/foco, IHC | Botão acionado desaparece na mudança de estado, podendo perder destino de teclado. Médio → resolvido no fluxo: título recebe foco após ação manual; efeitos passivos não roubam foco | Remoção DOM é confirmada; dificuldade de usuários reais não medida. Testes de navegador precisam comprovar foco/Enter; não alegar toda navegação por teclado ou leitura assistiva validada |
| U03 — recibo KEY_REUSED, segurança/integridade do fluxo | Resultado de chave incompatível não é o recibo persistido original. Médio → resolvido antes da publicação: não remove diário nem trata conflito como registro | Colisão UUID não foi observada; adulteração local é cenário relevante e não autoriza recurso. Teste simula conflito e verifica preservação. Recuperação automática de diário adulterado não é prometida |
| U04 — storage e rede, recuperação | Escrita/consulta podem falhar e resposta pode ser perdida antes/depois do commit. Alto potencial → controlado: persistência mínima antes do envio, GET próprio, sem POST automático, retry explícito | Não é vulnerabilidade confirmada da API nem suporte offline. Testes locais controlam storage/falhas; navegador/SQL real devem comprovar perda pós-commit, recibo original e novo envio com a mesma chave. Apagamento externo continua limitação |
| U05 — erros de teste, legibilidade/evidência | Asserção ampla encontrava “horário confirmado pelo servidor” nas instruções, sem ser confirmação da tentativa. Baixo → resolvido: asserção específica na região de feedback | Falso positivo do teste, não sucesso falso da interface. Teste atualizado preserva exigência de não confirmar antes da resposta. Teste antigo de login foi ampliado para aguardar consultas de jornada, mantendo logout/limpeza de senha |

| U06 — cartão da conta/hierarquia, IHC | Capturas reais mostravam cartão de conta alto com espaço de feedback vazio, deslocando a tarefa principal. Baixo → resolvido: conta compacta e ações junto do estado/fuso | Não bloqueava operação nem comprovava dificuldade de participantes. Inspeção visual confirma excesso de espaço; reflow/teclado são retestados após o refinamento. Pesquisa de usabilidade continua necessária |

Responsabilidades: AttemptDiary valida/persiste dados mínimos; JourneyApi cuida de HTTP/CSRF/timeout/recibo; JourneyState coordena preflight, reconciliação e ações; JourneyComponent apresenta estado/foco/formatos. Não duplica regras de transição/duração do domínio. Consultas extras de identidade/estado antes de POST são deliberadas para segurança e stale state; não remover por estilo. Reconciliar percorre O(n) intenções dessa identidade, sequencialmente e sem marcações automáticas; gargalo não medido. Sem otimizar hash de senha, locks, CSRF ou receipts. Nenhum problema alto atual confirmado nesta revisão; riscos/limitações não são omitidos.

## Evidência e pendências

Local: build Angular e 38 testes aprovados (18 anteriores + 20 adicionais), diff e listagem dos 4 testes Playwright. Sem navegador Chromium instalado localmente: teste de layout/fluxo real será feito na CI autorizada, com SQL Server real/HTTPS e capturas. Java depende da CI, como na etapa anterior. Testes escritos não são teste executado.

Roteiro real: entrada com resposta perdida depois de commit → reload/recibo sem POST → pausa/retorno → saída direta em pausa → tentativa abortada antes de commit/404 → reload sem replay → repetição explícita da mesma chave → encerramento/logout. Testa foco/Enter, larguras 320/768/1280, viewport 360 e texto raiz a 200%; ampliação de fonte não é teste completo de zoom real do navegador. Capturas não substituem teste de usabilidade com participantes, contraste medido de todos os estados ou leitura assistiva.

Para fechar entrega: histórico/detalhe/resumo, regularização mínima, IHC/dispositivos restantes (Safari/celular físico/tecnologia assistiva quando possível), execução reproduzível e README/roteiro de avaliação. Não modificar escopo P0 silenciosamente; se alguma parte não for entregue, registrar claramente. Aprovação do técnico da empresa ainda não ocorreu.

### Validação progressiva

Primeiro código `e8d2a80`: Foundation 38586278468 e SQL/browser 38586279321 aprovados; logs 115929022191/115929021499/115929024405 conferidos: 55 backend, 38 Angular, 34 SQL distintos (1 migration + 33 agrupados), 4 percursos Chromium HTTPS. Repetições com runtime restrito não contam como novos testes. Capturas desse primeiro layout foram inspecionadas e motivaram U06.

Refinamento de hierarquia `29cc25a`: build e 38 testes locais aprovados; Foundation 38587696350 aprovado, logs backend 115933977719/frontend 115933978194 (55/38), checkout aba1ca9. SQL/browser 38587695553 aprovado, log 115933976793 conferido (34 SQL distintos + 4 percursos Chromium). Capturas refinadas em evidencias/jornada, inspecionadas em 320/1280 px. O roteiro de navegador recebe também cenário com duas abas reais, foco passivo preservado, logout com intenção pendente, conta europeia sem consulta/replay de tentativa brasileira e recuperação na conta original. Registrar resultado dessa ampliação antes de considerá-la validada.

Contrastes calculados por luminância relativa sRGB dos pares opacos declarados: #172033/branco 16,27:1; #475569/branco 7,58:1; #30458a/branco 8,96:1; #30458a/#eef2ff 8,01:1; #172033/#fff7ed 15,32:1; #9a3412/#fff7ed 6,88:1; foco #b45309/branco 5,02:1. Não cobre todos os estados/transparências nem certifica WCAG. Capturas somente com dados fictícios e sem credenciais.


Ampliação no commit cadd548: Foundation 38589408692 aprovado (55/38); SQL/browser 38589408760 teve 34 testes SQL e backend aprovados, mas não concluiu os 4 percursos. O teste desktop recarregava a página imediatamente após clicar Sair, antes de concluir CSRF/logout; a sessão brasileira permanecia ativa e o formulário esperado não aparecia. Achado U07: baixo de sequenciamento de teste; não demonstra acesso da conta europeia à tentativa brasileira, pois esse passo não foi alcançado. Correção: aguardar tela Entrar e comprovar GET /api/me=401 antes da navegação/login seguinte, sem mudar regras, remover asserções ou aumentar limites. A causa é sustentada pela sequência do teste e snapshot da sessão ainda ativa; a nova execução precisa confirmar a conclusão do logout e toda a troca de conta. Resultado anterior não é apresentado como sucesso global; um percurso ficou sem execução pelo limite global após o timeout.
