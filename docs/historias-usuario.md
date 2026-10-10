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
- Dado Em pausa, a baseline RN05 rejeita saída; interface deve explicar a restrição, sem fabricar retorno/saída.

Rastreabilidade: ST01/ST03; RF06; RN05; T03/T05. Dependências: HU03–HU05/HU14. Antes de implementar, resolver ou manter explicitamente a questão UX05 do plano UX; esta história não aprova RN05 como regra real do RH.

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
- Nova entrada permanece bloqueada; não há saída automática nem edição direta pelo usuário. Sem HU17/HU18, a limitação de regularização precisa estar visível.

Rastreabilidade: ST01/ST03; RF13; RN09; T11. Dependências: HU07/HU10. Risco UX06: avaliar o recorte de correção antes do núcleo de jornada.

### HU14 — Recuperar uma marcação sem duplicá-la

Como colaborador com conexão instável, quero confirmar uma tentativa cujo resultado não chegou para não registrar o mesmo evento duas vezes.

- Dado resposta perdida, então a tela informa resultado não confirmado, reconcilia no servidor e reutiliza a mesma chave/payload ao repetir aquela tentativa.
- Repetição confirmada retorna o resultado original; chave reutilizada com payload diferente gera 409. Clique duplo, duas abas e concorrência também exigem proteção no servidor.
- Sem conexão, não simular sucesso ou registro offline. Sem poder reconciliar após reload, bloquear nova tentativa automática e consultar estado; estratégia de retenção da chave ainda deve ser detalhada.

Rastreabilidade: ST01; RF14; RN12; RNF05/RNF06; T12/T13/T14/T15/T22. Dependências: idempotência persistida, transação e protocolo de recuperação. Não está implementado.

## Complementos P1 — não implementados

### HU15 — Acompanhar equipe

Como gestor, quero consultar a jornada dos colaboradores vinculados para acompanhar a rotina da equipe.

Dado perfil e vínculo autorizados, quando consulto, então vejo apenas a equipe permitida; alterar IDs não amplia acesso e não posso marcar por eles.

Rastreabilidade: ST02; RF15; RNF03; T25. Dependências: núcleo estável, papel e vínculos de equipe.

### HU16 — Consultar pendências no RH

Como profissional de RH, quero consultar registros e pendências autorizados para apoiar a revisão operacional.

Dado perfil RH autorizado, quando consulto, então recebo dados no escopo permitido; conta EMPLOYEE não ganha esse acesso por alterar a interface.

Rastreabilidade: ST03; RF16; RNF03/RNF11; T25. Dependências: núcleo estável e autorização de RH; sem cálculo legal de folha.

### HU17 — Solicitar correção

Como colaborador, quero propor uma correção com motivo para regularizar uma marcação incorreta sem apagar o original.

Dado registro próprio, quando envio proposta válida e justificativa, então a solicitação fica pendente e o registro original permanece; horários locais ambíguos exigem escolha explícita do instante, e inexistentes são rejeitados.

Rastreabilidade: ST01/ST03; RF17; RNF07/RNF11/RNF14; T26. Dependências: modelo de revisão/auditoria e fluxo de decisão. Prioridade P1 vigente; qualquer mudança deve ser registrada.

### HU18 — Decidir correção

Como profissional de RH autorizado, quero aprovar ou rejeitar uma solicitação para manter registros rastreáveis.

Dado que não sou o solicitante, quando decido, então decisão e autoria ficam auditadas; aprovação altera a visão efetiva/cálculo, não apaga o original. Autoaprovação é rejeitada.

Rastreabilidade: ST03; RF18; RNF03/RNF05/RNF14; T26. Dependências: HU17 e permissões do RH.

## Qualidade transversal e controle de mudanças

Todas as histórias aplicáveis devem incluir acesso próprio, feedback textual, teclado, loading/erro/vazio quando pertinentes, segredos externos e testes. Esses critérios não podem ser substituídos por guards de interface. Histórias não criam autorização para geolocalização, offline, biometria, folha ou jornadas de oito horas.

STs: [stakeholders.md](stakeholders.md); RF/RNF: [requisitos.md](requisitos.md); RN: [proposta.md](proposta.md); Ts: [validacao.md](validacao.md). Antes de marcar uma história como pronta, registrar commit, ambiente e evidência. Ainda não há estimativas de esforço nem aprovação de stakeholders.
