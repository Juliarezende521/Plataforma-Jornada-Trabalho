# Levantamento de requisitos — Plataforma de Jornada de Trabalho

Status: baseline de escopo. RF01/RF02 aprovados no ambiente de CI com SQL Server real/HTTPS/Chromium, conforme [validacao-integrada.md](validacao-integrada.md). A jornada online e recuperação têm evidência progressiva em [tela-jornada.md](tela-jornada.md); consultas/regularização e aceite global permanecem pendentes; RNFs têm evidências parciais, não aprovação integral.

## Objetivo e fontes

Documentos derivados: [stakeholders](stakeholders.md), [histórias de usuário](historias-usuario.md) e [experiência/IHC](experiencia-usuario.md). Eles não ampliam o escopo nem substituem esta baseline. UX05 (RN05), UX06 (regularização mínima) e recuperação HU14 receberam decisões documentadas em 11/10/2026; RN05 tem cobertura de domínio/serviço; sua interface e recuperação Angular foram implementadas com evidência progressiva em tela-jornada.md; regularização mínima ainda precisa de implementação e testes. A fronteira HTTP atual está documentada em [API de jornada](api-jornada.md); não encerra o aceite global dos requisitos de jornada.

Centralizar as marcações e consultas de jornada da Dungeons & Dragons Group, com colaboradores no Brasil e Europa, inclusive remotos e em viagem. Fonte principal: enunciado fornecido na entrevista. Fonte complementar: escolha de Java pela desenvolvedora e informação recebida de que a empresa utiliza SQL Server; não houve entrevista adicional com RH ou usuários.

Origem E = explícita no enunciado; C = derivada do contexto; D = decisão de projeto. Prioridade P0 = núcleo de entrega; P1 = complemento após o núcleo. A prioridade é nossa proposta, não uma classificação informada pela empresa. “Núcleo” inclui decisões necessárias à confiabilidade; não significa que todas foram exigidas textualmente.

## Atores e limites de acesso

| Ator | Necessidade | Acesso proposto |
|---|---|---|
| Colaborador | Marcar jornada e consultar seus registros | Apenas registros próprios |
| Gestor (P1) | Acompanhar rotina da equipe | Consulta dos colaboradores vinculados; sem marcar por eles |
| Aprovador de regularização (P0, função atribuída ao RH na proposta) | Decidir solicitações de saída esquecida | Apenas solicitações no escopo provisionado e contexto necessário à decisão; não aprova a própria |
| RH (consulta geral P1) | Consultar registros da operação | Consulta geral somente com autorização específica, distinta da aprovação mínima |
| Técnico avaliador | Executar e verificar a solução | Contas fictícias e roteiro de avaliação; nenhum acesso especial em produção |

Provisionamento inicial por seed exclusivo de avaliação. Cadastro público, painel administrativo de contas e integração corporativa de identidade não fazem parte do núcleo.

## Requisitos funcionais

| ID | Origem | Prioridade | Requisito | Critério de aceite |
|---|---|---|---|---|
| RF01 | E/D | P0 | Autenticar contas provisionadas | Credenciais válidas estabelecem sessão; inválidas retornam erro genérico |
| RF02 | D | P0 | Encerrar a sessão | Logout invalida a sessão; nova consulta protegida retorna 401 |
| RF03 | E/D | P0 | Registrar entrada | Cria uma jornada e um evento para o usuário autenticado, se não houver jornada aberta |
| RF04 | E/D | P0 | Registrar início de pausa | Só é aceito enquanto trabalhando; muda o estado e persiste evento |
| RF05 | E/D | P0 | Registrar retorno | Só é aceito durante pausa; volta ao estado trabalhando |
| RF06 | E/D | P0 | Registrar saída | Aceito trabalhando ou em pausa; encerra a jornada. Em pausa, delimita a pausa final sem retorno artificial e sem contar esse intervalo como trabalho |
| RF07 | C/D | P0 | Exibir jornada atual | Mostra estado confirmado, última marcação e ações permitidas |
| RF08 | E/D | P0 | Consultar histórico próprio | Filtra por data de referência e apresenta resultados paginados, sem registros alheios |
| RF09 | E/D | P0 | Consultar detalhe | Lista eventos ordenados e explica os intervalos que compõem a duração |
| RF10 | C/D | P0 | Apresentar resumo do período | Soma jornadas encerradas e apresenta quantidade de pendências separadamente |
| RF11 | C/D | P0 | Consultar em fusos distintos | Mudar fuso de visualização altera horários exibidos, sem mudar instantes, duração ou data de referência |
| RF12 | C/D | P0 | Suportar madrugada e múltiplas pausas | Jornada atravessa meia-noite e desconta todos os intervalos de pausa corretamente |
| RF13 | C/D | P0 | Sinalizar jornada incompleta | Sem saída, aparece pendência e eventual total provisório; não entra no total fechado |
| RF14 | C/D | P0 | Proteger repetição e recuperar marcação | Mesma chave e payload retornam resultado original; payload diferente gera 409; tentativa preservada antes do envio permite consulta autenticada após timeout/reload |
| RF15 | C/D | P1 | Consultar equipe por gestor | Usuário gestor só visualiza colaboradores vinculados |
| RF16 | C/D | P1 | Consultar registros por RH | Perfil RH consulta colaboradores com autorização no servidor |
| RF17 | C/D | P0 mínimo / P1 amplo | Solicitar regularização com motivo | P0: propor saída esquecida da própria jornada aberta, sem alterar originais; outras correções ficam P1 |
| RF18 | C/D | P0 mínimo / P1 amplo | Aprovar/rejeitar regularização | P0: aprovador autorizado diferente do solicitante decide saída esquecida; original preservado e atualização efetiva atômica. Outras correções ficam P1 |

Consulta geral por gestor/RH permanece P1. Em 11/10/2026, a desenvolvedora aprovou antecipar somente a regularização de saída esquecida de RF17/RF18 para P0, como decisão do projeto, não requisito textual da empresa. Esse recorte inclui interface mínima de solicitação/decisão, autorização e auditoria, não painel completo de RH nem edição de qualquer marcação.

### Regularização mínima de saída esquecida — decisão P0

- Colaborador solicita somente para a própria jornada aberta: data/hora de saída proposta, fuso/offset explícito e justificativa obrigatória. Não oferecer Saída agora como reparo retroativo. Não apagar nem reescrever eventos originais.
- Converter a proposta para instante UTC usando fuso validado; rejeitar horário local inexistente e exigir offset válido quando ambíguo. A saída proposta não pode ser anterior ao último evento nem posterior ao instante do pedido, determinado pelo servidor. Aplicar RN05 se a jornada terminar em pausa. A data de referência continua a do início.
- Registrar pedido PENDING, proponente, instante da proposta, fuso/offset informado, instante de registro, justificativa e versão da jornada. Permitir no máximo um pedido pendente por jornada; rejeição permite novo pedido com nova chave, preservando o anterior. Validação e limites de campos serão fixados no contrato antes de codificar.
- Aprovador tem autoridade específica e escopo de colaboradores provisionado no servidor. Consulta apenas pedidos e contexto necessário das jornadas nesse escopo; essa permissão não concede RF16. Autoaprovação e escopo externo são rejeitados. Não haverá cadastro público/admin de permissões nesta etapa; avaliação terá aprovador fictício provisionado por migration/seed apropriados.
- Aprovação revalida jornada ainda aberta, versão e limites temporais. Se houve marcação após o pedido, retornar conflito sem aplicar correção e exigir reavaliação/cancelamento do pedido; permitir cancelar o próprio pedido ainda PENDING, com registro auditado. Não bloquear uma saída online legítima por existir pedido: ela encerra a jornada e torna o pedido não aprovável, com cancelamento por superação auditado na mesma transação.
- APPROVED, REJECTED e CANCELLED são estados terminais do pedido. Aprovação, revisão efetiva de saída, encerramento/versão da jornada e auditoria são atômicos. A revisão guarda saída original ausente, saída efetiva informada, solicitante, aprovador, justificativas e instante da decisão do servidor. Não inserir uma saída como se fosse marcação online original. Histórico distingue correção manual de registro online e cálculo usa a saída efetiva.
- Solicitação, cancelamento e decisão exigem sessão, CSRF, idempotência e concorrência; repetir a mesma decisão confirmada não reaplica a revisão. Decisões incompatíveis simultâneas não podem ambas vencer. Motivo de rejeição é obrigatório e visível ao solicitante; a aprovação também fica identificada.
- Enquanto pendente/rejeitada/cancelada e sem saída efetiva, a jornada segue incompleta, não entra no consolidado e bloqueia nova entrada. Após aprovação, nova entrada é liberada. Não prometer disponibilidade imediata do aprovador nem inventar prazo de atendimento: o tempo de espera é uma limitação operacional a declarar e testar.

Status: somente planejamento autorizado; permissões, migrations, endpoints, interface e testes desse recorte ainda não implementados. Não tratar o seed ou a autoridade EMPLOYEE existentes como suporte a aprovador.

### Recuperação de marcação online — decisão P0

Protocolo de RF14/HU14 definido em 11/10/2026, implementado no recorte online descrito em tela-jornada.md. Não é fila offline nem registro retroativo; consultar evidências e limitações da etapa.

1. Antes de enviar, confirmar identidade/estado no servidor e gerar UUID aleatório para Idempotency-Key. Preservar em localStorage, sob namespace/versionamento próprios, apenas chave, userId/employeeId da identidade confirmada, tipo e expectedWorkSessionId (null para entrada; ID da jornada alvo para outras transições). Nenhuma senha, cookie, token CSRF, email, nome, justificativa ou horário de trabalho é armazenado. Falha ao gravar/reler a tentativa bloqueia o envio com orientação explícita, em vez de prometer recuperação após reload sem suporte.
2. Enviar via HTTPS com sessão e CSRF atualizado; backend deriva autor da sessão, não do armazenamento. O payload inclui tipo e jornada esperada, sem timestamp do dispositivo. Validar que a jornada alvo pertence ao colaborador e coincide com a aberta; um comando antigo não pode atingir uma jornada nova. Hash canônico inclui operação/tipo/jornada alvo; chave não é credencial nem concede acesso.
3. No servidor, persistir evento, estado/versão e resultado confirmado atomicamente, com unicidade colaborador/operação/chave. A consulta autenticada do resultado retorna apenas o recibo próprio: tipo, jornada/evento, instante confirmado e resultado HTTP original. Resultado aceito tem replay da resposta original; conflitos definitivos de negócio também devem ter resultado terminal idempotente, gravado sem alteração parcial. Falhas de autenticação/CSRF e indisponibilidade não são recibos de marcação. Definir tradução transacional/concorrência antes de codificar; nunca gravar resultado de sucesso fora da transação do evento.
4. Após timeout, perda de conexão, 5xx ou reload, mostrar Resultado ainda não confirmado e bloquear novas intenções naquela conta até reconciliar as tentativas locais. Primeiro confirmar a sessão e consultar o recibo pela mesma chave. Consultar apenas o estado atual não prova qual tentativa produziu um evento. Recibo existente confirma sucesso ou rejeição; ausência (404) não prova falha definitiva, pois a primeira requisição ainda pode estar em andamento.
5. Se não houver recibo, oferecer repetição explícita com a mesma chave e payload, nunca nova chave automática nem envio automático ao reconectar/reentrar. Avisar que, se a tentativa anterior não foi gravada, a marcação terá o instante do servidor no novo processamento, não o horário do clique original. Se for necessário um horário passado, usar regularização autorizada, não timestamp inventado. Nova identidade não pode consultar/repetir tentativas de outra conta.
6. Com recibo terminal verificado, mostrar o resultado e atualizar a jornada atual antes de habilitar novas ações; remover somente aquela tentativa local. Se a atualização falhar, continuar com estado indisponível, sem usar estado antigo como confirmado. Logout preserva tentativas incertas, mas oculta seus detalhes; só reconciliar após autenticar novamente a mesma identidade. Não guardar credenciais para permitir recuperação.
7. Duas abas devem observar mudanças do armazenamento e reconciliar todas as tentativas da identidade antes de habilitar ações; coordenação local é auxiliar, não lock confiável. Concorrência, versões e constraints no SQL Server continuam responsáveis pela integridade, inclusive entre dispositivos.

Limitações: localStorage é legível por JavaScript e pode ser apagado/alterado; tratar seu conteúdo como não confiável e reduzir dados/validar formato. Persistência local não garante recuperação da chave em outro dispositivo, após limpeza do navegador ou em modo que apague armazenamento ao fechar. Nesses casos, consultar histórico/estado e não repetir cegamente uma intenção antiga. Preservar recibos no servidor durante o MVP; retenção futura exige política que não permita reexecutar chave expirada. Sem conexão não aceitar nova marcação offline nem prometer envio posterior. Regularizações têm idempotência própria; este diário não armazena proposta/justificativa sensível de correção, cujo protocolo de recuperação será detalhado no contrato específico.

## Requisitos não funcionais

| ID | Origem | Requisito | Verificação proposta |
|---|---|---|---|
| RNF01 | E/C | Responsividade | Fluxos essenciais em celular (320/360 CSS px), tablet e desktop, sem cortes ou rolagem horizontal geral; alvos de toque utilizáveis |
| RNF02 | E/D | Persistência confiável | Eventos continuam após reiniciar API e banco com volume persistente |
| RNF03 | E/C | Autorização por recurso | Testes cruzados com dois usuários impedem leitura e marcação alheias |
| RNF04 | D | Segurança de sessão e CSRF | Cookies configurados, senha com hash adaptativo, token validado, logout e expiração testados |
| RNF05 | C/D | Integridade transacional | Evento, estado e idempotência são atômicos; falha reverte tudo |
| RNF06 | C/D | Concorrência | Requisições simultâneas não criam duas jornadas abertas nem eventos incompatíveis |
| RNF07 | C/D | Correção temporal | UTC, IANA e cálculo de duração testados em viagens e mudanças de horário de verão |
| RNF08 | D | Validação e erros previsíveis | DTOs validados, Problem Details, sem stack traces ou dados sensíveis |
| RNF09 | D | Execução reproduzível | Clone limpo, dependências fixadas, migrations e seed geram ambiente funcional |
| RNF10 | D | Manutenibilidade | Regras no backend, componentes coesos, DTOs distintos de entidades e testes determinísticos |
| RNF11 | C/D | Privacidade | Dados mínimos e fictícios; segredos, tokens e senhas ausentes dos logs e Git |
| RNF12 | D | Acessibilidade básica | Labels, teclado, foco visível, contraste e feedback textual |
| RNF13 | C/D | Consulta limitada | Paginação com máximo proposto de 100 itens e período limitado proposto de 366 dias; servidor valida limites |
| RNF14 | D | Observabilidade | Logs de erro com correlação, sem segredos; eventos relevantes de autenticação e jornada rastreáveis |

Limites de consulta são decisões revisáveis. Não há SLA, escala de usuários ou meta de latência informados. Medir latência com ambiente e massa documentados antes de assumir compromisso de desempenho.

O requisito explícito de utilização em diferentes dispositivos e contextos será verificado pelo [checklist de dispositivos e contextos antes da entrega](validacao.md#dispositivos-e-contextos--aceite-antes-da-entrega). Ele combina RNF01/RNF12, segurança, recuperação de rede e regras temporais existentes; não exige aplicativo nativo, GPS, biometria ou marcação offline. As larguras e a matriz de navegadores são decisões de teste do projeto, não números impostos pelo enunciado.

## Regras de negócio

RN01–RN12 são definidas em proposta.md e constituem a referência para estados, pausas, UTC, fuso de referência, data de início, total consolidado e ausência de operação offline. Os requisitos acima não autorizam cálculo de horas extras, desconto salarial ou enquadramento legal por país.

## Fluxos essenciais

| Caso | Pré-condição | Fluxo de sucesso | Alternativa |
|---|---|---|---|
| UC01 — Login | Conta fictícia provisionada | Obter CSRF → autenticar → consultar identidade | Credencial inválida: erro genérico; sessão ausente: 401 |
| UC02 — Marcar | Sessão válida e conexão online | Consultar estado → preservar diário → enviar tipo + jornada alvo + chave → confirmar recibo → atualizar tela | Estado incompatível: 409; timeout/reload: consultar recibo e repetir explicitamente com mesma chave/payload; CSRF inválido: renovar de forma controlada |
| UC03 — Histórico | Sessão válida | Selecionar período/fuso → receber página → abrir detalhe | Período inválido: 400; recurso alheio: 404; lista vazia: mensagem explicativa |

## Pendências e premissas

- Prazo, versões instaladas, sistema operacional e arquitetura do computador ainda não informados.
- Regras de pausas remuneradas, jornadas contratuais e fechamento legal por país não informadas; não inferir.
- Fuso de referência provisionado no cadastro; viagens alteram visualização, não a vinculação histórica.
- Um usuário pode abrir mais de uma jornada no mesmo dia após encerrar a anterior; não há sobreposição.
- Fusos IANA são validados pelo Java. Mudanças administrativas no fuso não reclassificam jornadas anteriores.
- Sem aprovação formal dos stakeholders; estas decisões serão publicadas como hipóteses do desafio.

## Definição de pronto do núcleo

RF01–RF14, recorte P0 de RF17/RF18 e RNF aplicáveis implementados; migrations executadas em SQL Server vazio; testes críticos aprovados com evidências; checklist de dispositivos/contextos verificado no fluxo completo; interface utilizável; README reproduzido; limitações declaradas; repositório sem segredos. O núcleo não está concluído: autenticação, schema/API/jornada online e recuperação possuem evidência integrada; histórico/detalhe/resumo, restante das consultas, regularização mínima e avaliação completa de IHC/dispositivos continuam pendentes.
