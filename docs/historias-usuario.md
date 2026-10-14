# Histórias de usuário e critérios de aceite

Status: histórias derivadas da baseline, sem ampliar o escopo. HU01/HU02 têm evidência integrada de aprovação; demais histórias estão planejadas. Critérios são observáveis, ligados aos RF, RNF e cenários T de validação. Não são entrevistas nem comprovação de satisfação dos usuários.

P0 = núcleo proposto; P1 = complemento condicionado. Cada história deve ser refinada em tarefas pequenas de backend/interface/testes antes da implementação; HU08/HU10/HU14 ainda podem exigir divisão. Conclusão exige critérios atendidos e evidência, não apenas criação da tela. RF e RN continuam canônicos; alteração de história não muda automaticamente a regra de negócio.

## Núcleo P0

### HU01 — Entrar na plataforma

Como colaborador com conta provisionada, quero entrar com meu email e senha para acessar minhas informações com segurança.

- Dado conta válida, quando envio credenciais, então vejo minha identidade confirmada pelo servidor.
- Dado credenciais inválidas, então recebo erro genérico sem confirmação de existência da conta; posso tentar novamente respeitando o limite.
- Falha de conexão não confirma login; posso consultar meu acesso. Não há cadastro público.

Rastreabilidade: ST01; RF01; RNF04/RNF08/RNF11; T01/T23. Estado: aprovado no ambiente descrito em [validacao-integrada.md](validacao-integrada.md), não aceite de produção.

### HU02 — Sair da plataforma

Como colaborador, quero encerrar meu acesso para impedir o uso posterior da minha sessão.

- Dado sessão autenticada, quando escolho Sair e a API confirma, então retorno ao formulário e a sessão anterior não acessa minha identidade.
- Se a resposta falhar, a tela informa que a saída não foi confirmada e permite verificar o acesso.

Rastreabilidade: ST01; RF02; RNF04; T01/T23. Estado: aprovado no ambiente de CI documentado.

### HU03 — Registrar entrada

Como colaborador, quero registrar o início do trabalho para iniciar minha jornada.

- Dado que não existe jornada aberta, quando marco entrada, então vejo estado Trabalhando e horário confirmado pelo servidor.
- Dado jornada já aberta, então nova entrada é rejeitada sem criar evento/jornada duplicados.

Rastreabilidade: ST01; RF03; RN01–RN03; T03/T05/T14. Dependências: HU01/HU07/HU14 e integridade transacional.

### HU04 — Iniciar pausa

Como colaborador trabalhando, quero registrar uma pausa para identificar o intervalo sem trabalho nesta versão.

- Dado estado Trabalhando, quando inicio pausa, então vejo Em pausa e evento confirmado.
- Qualquer outro estado rejeita a ação sem mudança parcial. A tela explica que pausas são descontadas conforme a hipótese RN04.

Rastreabilidade: ST01/ST03; RF04; RN04; T03/T05. Dependências: HU03/HU07/HU14.

### HU05 — Retornar da pausa

Como colaborador em pausa, quero registrar meu retorno para retomar a contagem de trabalho.

- Dado Em pausa, quando retorno, então vejo Trabalhando e o intervalo da pausa fica delimitado.
- Dado estado incompatível, então retorno é rejeitado e a tela apresenta o estado atualizado.

Rastreabilidade: ST01; RF05; T03/T05. Dependências: HU04/HU07/HU14.

### HU06 — Encerrar jornada

Como colaborador, quero registrar a saída para concluir minha jornada e consultar sua duração.

- Dado Trabalhando, quando marco saída, então vejo Encerrada e duração calculada a partir dos instantes confirmados.
- Dado Em pausa, quando marco saída, então a jornada é encerrada no instante confirmado e a pausa final termina nesse instante, sem evento de retorno artificial nem inclusão da pausa na duração trabalhada.
- Dado entrada 09h, pausa 12h e saída 12h30, então consulto 3 eventos, 3h trabalhadas e 30min de pausa. Repetir a saída com a mesma chave não gera outro evento.

Rastreabilidade: ST01/ST03; RF06; RN04/RN05; T03/T05/T27. Dependências: HU03/HU04/HU07/HU14 e cálculo temporal. RN05 revisada em 11/10/2026 é decisão assumida do projeto; UX05 ainda requer implementação e validação, não aprovação como regra real do RH.

### HU07 — Entender a jornada atual

Como colaborador, quero ver o estado e a última marcação para saber quais ações posso realizar.

- Dado acesso autenticado, quando abro a tela, então vejo o estado confirmado, datas/fusos e ações coerentes.
- Pedido em andamento não permite outro envio local; duração em aberto, se exibida, é provisória. Nenhum estado muda só porque cliquei.

Rastreabilidade: ST01; RF07/RF13; RNF01/RNF12; T03/T11/T20. Dependências: API de estado e autorização.

### HU08 — Consultar histórico próprio

Como colaborador, quero filtrar minhas jornadas por período para acompanhar os registros anteriores.

- Dado período válido, então recebo somente jornadas próprias, com paginação; máximo proposto de 100 itens/página e 366 dias por consulta.
- Sem resultados, a tela explica o período pesquisado e permite alterar filtros. Recurso alheio não é exposto.

Rastreabilidade: ST01; RF08; RNF03/RNF13; T16/T21. Dependências: persistência de jornadas e API de consulta.

### HU09 — Compreender os detalhes

Como colaborador, quero ver eventos e intervalos de uma jornada para entender como sua duração foi calculada.

- Dado uma jornada própria, quando abro o detalhe, então vejo eventos na ordem registrada, datas/fusos e intervalos de trabalho/pausa.
- ID inexistente ou alheio retorna 404 sem revelar dados; totais não ocultam pendências.

Rastreabilidade: ST01; RF09; RNF03/RNF07; T03/T16. Dependências: HU08 e cálculo.

### HU10 — Consultar resumo do período

Como colaborador, quero entender minhas horas registradas e pendências para revisar o período.

- Dado período válido, então totalizo apenas jornadas encerradas e vejo pendências separadamente.
- Jornada que cruza o mês pertence ao mês da data de início no fuso de referência, conforme RN11; a interface explica esse agrupamento e não anuncia folha/horas extras.

Rastreabilidade: ST01/ST03; RF10; RN09/RN11; T10/T11. Dependências: HU08/HU09.

### HU11 — Consultar em outro fuso

Como colaborador em viagem, quero escolher um fuso de visualização para interpretar horários sem alterar meus registros.

- Dado fuso válido, quando o seleciono, então mudam horários exibidos, mas não instantes, duração, data de referência ou vínculo do histórico.
- A tela distingue fuso de referência do fuso de visualização; não muda o primeiro automaticamente pelo dispositivo.

Rastreabilidade: ST01; RF11; RN07/RN08; RNF07; T07/T08/T09. Dependências: consultas e conversão temporal.

### HU12 — Trabalhar entre dias e fazer várias pausas

Como colaborador, quero registrar trabalho noturno e múltiplas pausas para representar minha rotina sem encerrar artificialmente à meia-noite.

- Dado entrada em um dia e saída no seguinte, então eventos mostram suas datas completas e a jornada mantém a data de referência do início.
- Todas as pausas confirmadas são descontadas; a duração usa instantes, inclusive nas mudanças de horário de verão.

Rastreabilidade: ST01; RF12; RN04/RN06/RN08; T04/T06/T08/T09. Dependências: HU03–HU06 e cálculo temporal.

### HU13 — Identificar pendência

Como colaborador, quero identificar jornada sem saída para não confundir registros incompletos com horas definitivas.

- Dado jornada aberta, então vejo pendência/estado atual e eventual duração marcada como provisória; ela não compõe o consolidado.
- Nova entrada permanece bloqueada até saída efetiva; não há saída automática nem edição direta. Oferecer HU17/HU18 no recorte P0 quando implementadas, com status do pedido e explicação da espera/rejeição.

Rastreabilidade: ST01/ST03; RF13; RN09; T11/T28. Dependências: HU07/HU10/HU17/HU18. UX06 recebeu recorte P0 autorizado em 11/10/2026; atendimento pelo aprovador ainda é limitação operacional.

### HU14 — Recuperar uma marcação sem duplicá-la

Como colaborador com conexão instável, quero confirmar uma tentativa cujo resultado não chegou para não registrar o mesmo evento duas vezes.

- Dado resposta perdida, então a tela informa resultado não confirmado, reconcilia no servidor e reutiliza a mesma chave/payload ao repetir aquela tentativa.
- Repetição confirmada retorna o resultado original; chave reutilizada com payload diferente gera 409. Clique duplo, duas abas e concorrência também exigem proteção no servidor.
- Antes do envio, guardar diário mínimo da tentativa (chave, identidade, tipo e jornada alvo) sem credenciais; falha de armazenamento bloqueia envio. Após reload/timeout, confirmar mesma identidade, consultar recibo autenticado e atualizar estado. Recibo ausente não comprova que a requisição anterior terminou.
- Sem conexão, não simular sucesso ou registro offline. Repetição depende de ação explícita, mantém chave/payload e informa que marcação ainda não gravada receberá horário do novo processamento. Troca de conta não permite recuperar tentativa alheia; múltiplas abas e limpeza do armazenamento seguem as limitações do protocolo canônico.

Rastreabilidade: ST01; RF14; RN12; RNF05/RNF06; T12/T13/T14/T15/T22/T31–T34. Dependências: idempotência persistida, transação e recibo autenticado. Protocolo decidido em 11/10/2026 em requisitos.md; não está implementado/testado.

## Complementos P1 — não implementados

### HU15 — Acompanhar equipe

Como gestor, quero consultar a jornada dos colaboradores vinculados para acompanhar a rotina da equipe.

Dado perfil e vínculo autorizados, quando consulto, então vejo apenas a equipe permitida; alterar IDs não amplia acesso e não posso marcar por eles.

Rastreabilidade: ST02; RF15; RNF03; T25. Dependências: núcleo estável, papel e vínculos de equipe.

### HU16 — Consultar pendências no RH

Como profissional de RH, quero consultar registros e pendências autorizados para apoiar a revisão operacional.

Dado perfil RH autorizado, quando consulto, então recebo dados no escopo permitido; conta EMPLOYEE não ganha esse acesso por alterar a interface.

Rastreabilidade: ST03; RF16; RNF03/RNF11; T25. Dependências: núcleo estável e autorização de RH; sem cálculo legal de folha.

## Regularização mínima P0 — não implementada

Recorte autorizado em 11/10/2026: somente saída esquecida; outras correções continuam P1. Regras canônicas em requisitos.md. Esta seção não implementa autoridade de aprovação ou concede consulta geral de RH.

### HU17 — Solicitar regularização de saída esquecida

Como colaborador, quero informar a saída esquecida com justificativa para regularizar minha jornada sem apagar os originais.

- Dado jornada própria aberta, quando envio saída válida, fuso/offset e justificativa, então um pedido fica PENDING e o original permanece; instantes antes do último evento ou futuros são rejeitados. Horários inexistentes são rejeitados e ambíguos exigem offset válido.
- Dado pedido pendente, então vejo proposta/status sem tratá-lo como saída confirmada; repetição idempotente não duplica, novo pedido simultâneo é rejeitado e posso cancelar meu pedido ainda pendente. Rejeição permite nova solicitação, sem apagar a anterior.
- Dado saída online legítima enquanto há pedido, então a jornada encerra e o pedido superado fica cancelado com auditoria.

Rastreabilidade: ST01/ST03; RF17 recorte P0; RNF07/RNF11/RNF14; T26/T28/T29/T30. Dependências: HU13, modelo de revisão/auditoria e idempotência. Outros tipos de correção permanecem P1.

### HU18 — Decidir correção

Como aprovador autorizado, quero decidir uma solicitação de saída esquecida no meu escopo para regularizar a jornada com rastreabilidade.

- Dado escopo autorizado e outro solicitante, quando aprovo pedido válido, então decisão, revisão efetiva, cálculo/encerramento e auditoria são atômicos; o histórico distingue saída manual de evento online. Nova entrada é liberada sem apagar originais.
- Autoaprovação ou acesso fora de escopo é rejeitado no servidor. Pedido com jornada/versão alterada não é aplicado; decisões concorrentes ou repetidas não reaplicam correção.
- Quando rejeito com motivo, então o colaborador vê a decisão e pode solicitar novamente; jornada continua pendente e não entra no consolidado.

Rastreabilidade: ST03; RF18 recorte P0; RNF03/RNF05/RNF14; T26/T28/T30. Dependências: HU17, permissões/escopo provisionados e transações. Consulta geral RF16 e outras correções não fazem parte dessa autorização mínima.

## Qualidade transversal e controle de mudanças

Todas as histórias aplicáveis devem incluir acesso próprio, feedback textual, teclado, loading/erro/vazio quando pertinentes, segredos externos e testes. Esses critérios não podem ser substituídos por guards de interface. Histórias não criam autorização para geolocalização, offline, biometria, folha ou jornadas de oito horas.

STs: [stakeholders.md](stakeholders.md); RF/RNF: [requisitos.md](requisitos.md); RN: [proposta.md](proposta.md); Ts: [validacao.md](validacao.md). Antes de marcar uma história como pronta, registrar commit, ambiente e evidência. Ainda não há estimativas de esforço nem aprovação de stakeholders.

## Evidência de consultas — 14/10/2026

HU08–HU11 implementadas no recorte próprio online, com detalhe de eventos/intervalos, resumo de encerradas e pendências separadas e fuso apenas visual. Contrato, revisão, CI e alcance em [consultas-jornada.md](consultas-jornada.md). Não encerra HU17/HU18 nem consulta de equipe/RH. Os status do início deste documento são registros de planejamento; não substituem essas evidências.
