# Experiência do usuário e plano de avaliação de IHC

Status: decisões de interação, inspeção inicial e plano de avaliação. Não é relatório de pesquisa com usuários nem certificação WCAG. Login foi testado em Chromium/SQL Server e suas capturas foram revisadas; isso não comprova satisfação, acessibilidade completa ou uso em iPhone físico. Não esperar o frontend terminar para planejar os fluxos.

## Contexto, objetivos e hipóteses

Usuário principal: colaborador que precisa marcar e compreender a própria jornada. Usos possíveis: desktop, celular, remoto, viagem, interrupção e teclado/tecnologia assistiva. São cenários derivados do desafio; sua frequência e dificuldades ainda não foram pesquisadas. ST01 é o foco do P0; necessidades de ST02/ST03 não autorizam telas P1 antes do núcleo.

Objetivos observáveis: reconhecer o estado atual; escolher a ação adequada; distinguir tentativa de registro confirmado; interpretar duração/fuso/data; encontrar pendências; recuperar acesso sem duplicar marcações. Não estabelecer taxa de sucesso, tempo médio ou satisfação sem medições. Idioma atual é português brasileiro; adequação às equipes europeias é uma pergunta aberta.

## Princípios aplicados ao projeto

Usamos as [heurísticas de Nielsen](https://www.nngroup.com/articles/ten-usability-heuristics/) como referência de inspeção, não como certificado de qualidade.

| Princípio | Decisão específica | Como verificar |
|---|---|---|
| Visibilidade do estado | Diferenciar aguardando, confirmado e resultado desconhecido | Resposta lenta/perdida nunca mostra marcação confirmada |
| Linguagem do usuário | Entrada, pausa, retorno, saída; sem CSRF/HTTP na tela | Usuário explica a ação sem conhecer o backend |
| Controle e liberdade | Filtros reversíveis; saída de acesso explícita | Cancelar consulta não altera jornada; logout é verificável |
| Consistência | Mesmos nomes para os mesmos eventos em botão, lista e detalhe | Comparar telas e histórias |
| Prevenção de erros | Ações coerentes com o estado; servidor valida também | Clique duplo/estado desatualizado não duplica eventos |
| Reconhecimento | Estado, última marcação, data e fuso visíveis | Não exigir lembrar se já houve entrada/pausa |
| Recuperação | Explicar problema e próximo passo sem culpar a pessoa | Testar falhas e consultar resultado no servidor |
| Simplicidade e ajuda | Priorizar tarefa diária e explicar apenas as regras relevantes | Sem controles falsos ou painel genérico sem função |

Controle e liberdade não significam apagar uma marcação ou desfazer evento confirmado. Saída esquecida exige regularização auditada mínima P0; outras correções permanecem P1. Não oferecer botão Desfazer que altere registros silenciosamente.

## Fluxos e estados de interação

### Acesso — implementado

Abrir página → consultar sessão → mostrar identidade ou formulário. Credenciais válidas → obter CSRF → autenticar → renovar token → consultar identidade. Loading bloqueia envio duplicado. Credencial inválida mantém email e limpa senha; erro genérico protege a privacidade. Falha incerta orienta verificar acesso. Logout só anuncia sucesso após confirmação. Não adicionar cadastro público nem recuperação de senha fictícia.

Existe evidência integrada em [validacao-integrada.md](validacao-integrada.md); estado do foco, leitura por tecnologia assistiva e linguagem ainda exigem avaliação específica.

### Jornada — proposta, não implementada

| Situação confirmada | Informação essencial | Ações e resposta esperada |
|---|---|---|
| Sem jornada aberta | Nenhuma jornada em andamento | Entrada; só mudar para Trabalhando após confirmação |
| Trabalhando | Última marcação, data/fuso, duração provisória se disponível | Iniciar pausa ou registrar saída |
| Em pausa | Início da pausa e regra de cálculo | Retorno ou registrar saída; saída encerra a jornada e a pausa final sem fabricar retorno (RN05 revisada) |
| Pedido em andamento | Ação solicitada e aguardando resposta | Bloquear nova submissão local; não inventar horário confirmado |
| Resultado desconhecido | Tentativa ainda sem confirmação | Reconciliar/repetir a mesma tentativa com idempotência; não criar outra chave automaticamente |
| Encerrada | Eventos e duração fechada | Consultar detalhe; nova entrada pode abrir outra jornada sem sobreposição |
| Pendência anterior | Jornada continua aberta e total não consolidado | Explicar bloqueio e oferecer solicitação de saída esquecida quando implementada; exibir proposta/fuso, justificativa, status e motivo de rejeição, sem prometer aprovação imediata |

Estados técnicos não devem aparecer como códigos 401/403/409. Conflito deve apresentar o estado recuperado e motivo compreensível. Não ocultar a explicação de um botão indisponível em tooltip acessível apenas por mouse. Não exigir confirmação modal em toda marcação por hábito; avaliar risco de clique e custo da tarefa antes de decidir.

Horário exibido no registro vem da confirmação do servidor; relógio do dispositivo não é comprovante. Não há offline. HU14 usa diário mínimo local antes do envio e consulta autenticada do recibo após reload/timeout, conforme requisitos.md. Consulta sem recibo não comprova falha: repetir exige ação explícita com mesma chave/payload. Avisar que tentativa não gravada receberá horário do novo processamento; não confundir diário local com proteção persistida do backend. Sem armazenamento válido, bloquear envio e explicar a limitação.

### Consulta, viagens e resumo — proposta

Histórico → período válido → página de jornadas próprias → detalhe com eventos e intervalos. Manter filtros ao voltar. Estado vazio informa período/filtros, sem tratar ausência de registros como falha. Período inválido indica o campo e a correção possível. Paginação e limites seguem RNF13.

Mostrar separadamente fuso de referência da jornada e fuso de visualização. Usar rótulo amigável acompanhado do identificador IANA, sem transformar viagem em alteração automática do histórico. Datas precisam ser explícitas nos eventos que atravessam dia/mês; em horários repetidos por mudança de offset, exibir informação que permita distinguir instantes. Total provisório não usa o mesmo tratamento de total fechado. Resumo mensal segue data de início no fuso de referência e não equivale a folha ou horas extras.

## Checklist de acessibilidade

Referência: [WCAG 2.2 — W3C](https://www.w3.org/WAI/WCAG22/quickref/). Meta de projeto: aplicar os critérios A/AA pertinentes; não declarar conformidade sem avaliação do conjunto.

| Critério de referência | Verificação planejada |
|---|---|
| 1.3.1 / 3.3.2 | Labels, instruções e associação dos erros aos campos |
| 1.4.1 | Estado/erro explicado por texto, não somente cor |
| 1.4.3 | Contraste de texto: 4,5:1; texto grande: 3:1 |
| 1.4.4 / 1.4.10 | Texto a 200%; reflow a 320 CSS px, além de 360 px |
| 1.4.11 | Contraste de controles/indicadores relevantes: 3:1 |
| 2.1.1 / 2.1.2 | Tarefas por teclado, sem armadilha |
| 2.4.3 / 2.4.7 / 2.4.11 | Ordem de foco, foco visível e não encoberto |
| 2.5.8 | Alvos de 24 × 24 CSS px ou exceções aplicáveis; preferência do projeto por 44 px |
| 3.3.1 / 3.3.3 | Identificar erros e orientar recuperação |
| 3.3.8 | Permitir gerenciadores de senha e colar credenciais |
| 4.1.2 / 4.1.3 | Nome/papel/estado acessíveis e anúncio das mensagens |

320 px não foi coberto pelo teste anterior de 360 px. 44 px é preferência de conforto, não o mínimo AA citado. Inspeção visual não substitui medição de contraste, teclado, zoom ou leitura assistiva. Não presumir que ARIA/role=status garante anúncio correto.

## Inspeção inicial do login

Método: revisão de HTML/CSS/fluxos e das quatro capturas preservadas em docs/evidencias/autenticacao. Há uma revisão assistida, sem painel de avaliadores ou participantes. Evidências favoráveis: labels visíveis, autocomplete, senha mascarada, feedback textual, foco CSS, botões bloqueados durante pedido e ausência de overflow nas capturas de 360 px. Essas observações têm escopo restrito.

Em 10/10/2026, calculamos luminância relativa sRGB para pares opacos declarados em app.scss/styles.scss, com razão (Lmaior + 0,05)/(Lmenor + 0,05). Valores arredondados apenas nesta apresentação:

| Par | Cores | Razão calculada |
|---|---|---|
| Texto sobre branco | #172033 / #ffffff | 16,27:1 |
| Marca sobre fundo da página | #3857ab / #f4f6fb | 6,23:1 |
| Texto do botão principal | #ffffff / #30458a | 8,96:1 |
| Erro sobre branco | #a32020 / #ffffff | 7,54:1 |
| Borda do input sobre branco | #64748b / #ffffff | 4,76:1 |
| Foco do botão sobre branco | #b45309 / #ffffff | 5,02:1 |

Esses pares superam os limiares pertinentes indicados no checklist; não representam todos os estados, transparências, foco sobre outras superfícies ou cores futuras. Cálculo não é prova de conformidade da página nem inspeção completa do navegador.

Gravidade preliminar: 1 = cosmético; 2 = dificuldade relevante contornável; 3 = pode bloquear tarefa ou comprometer interpretação; 4 = bloqueador confirmado. Não há achado 4 confirmado. Frequência/impacto em usuários reais não medidos; prioridades abaixo são hipóteses de revisão, não resultado estatístico.

| ID | Achado/risco | Evidência e gravidade preliminar | Próxima ação |
|---|---|---|---|
| UX01 | Foco pode se perder quando formulário é removido após login/logout | HTML dinâmico sem gerenciamento explícito; 2, a confirmar | Testar foco após transições/erros; definir destino lógico, sem foco automático disruptivo |
| UX02 | Verificar sessão pode ser linguagem técnica | Texto atual; compreensão não medida; 2 | Testar Verificar acesso como alternativa, sem alterar contrato HTTP |
| UX03 | Falta orientação de suporte para conta/senha | Sem cadastro/recuperação e sem canal fornecido; 2 | Obter canal legítimo; não criar link/contato fictício |
| UX04 | Português pode não atender parte da equipe europeia | Frontend pt-BR; hipótese de contexto; 2 | Confirmar idiomas; avaliar i18n sem ampliar P0 silenciosamente |
| UX05 | Exigir retorno antes de saída em pausa pode não representar trabalho real | Restrição anterior; 3 | Decisão documentada em 11/10/2026: RN05 permite saída direta. Implementação, T27 e compreensão da pausa final ainda pendentes; não declarar risco resolvido em uso |
| UX06 | Saída esquecida bloqueia nova entrada; depende de regularização | Risco operacional; 3 | Recorte RF17/RF18 antecipado para P0 em 11/10/2026; implementar e testar solicitação/decisão. Até aprovação, bloqueio permanece; não reparar via banco/auto-saída |
| UX07 | Fuso IANA isolado pode ser difícil de interpretar | Captura mostra America/Sao_Paulo; 2 | Avaliar rótulo amigável sem perder informação técnica/histórica |

UX05/UX06 são riscos da proposta de jornada, não bugs observados na interface atual. Em 11/10/2026, RN05 passou a permitir saída durante pausa (RF06/HU06/T27) e a regularização mínima de saída esquecida de RF17/RF18 foi antecipada para P0 com autorização da desenvolvedora. Não houve implementação ou testes desses comportamentos; UX06 mantém dependência operacional do aprovador, sem prazo de atendimento definido.

## Plano de teste de usabilidade

Realizar quando houver fluxo utilizável, começando pelo login e repetindo após jornada/histórico. Se houver participantes disponíveis, iniciar rodada exploratória pequena (por exemplo, 3–5), buscando variação de dispositivo, viagem/fuso e experiência digital. Essa amostra não garante representatividade nem cobertura de acessibilidade. Participante usando tecnologia assistiva e avaliação especializada devem ser considerados quando viáveis; não inventar sua participação.

Convidar com consentimento, explicar que avaliamos o produto, não a pessoa; usar contas fictícias. Evitar orientar caminhos durante a tarefa; registrar a ajuda necessária. Não pedir senhas pessoais, CPF, geolocalização ou dados da empresa. Não gravar tela/voz sem consentimento específico; separar dados de recrutamento dos resultados e publicar apenas síntese anonimizada.

| Tarefa | O que observar | Dependência |
|---|---|---|
| Entrar, corrigir credencial e sair | Entendimento do erro, foco, confirmação e recuperação | HU01/HU02, já implementadas |
| Registrar entrada/pausa/retorno/saída | Ação correta e compreensão do estado | HU03–HU07, pendentes |
| Recuperar resultado após falha | Não interpretar tentativa como sucesso nem duplicar | HU14, pendente |
| Consultar outra data/fuso | Distinguir horário exibido e data de referência | HU08–HU12, pendentes |
| Explicar total com jornada incompleta | Entender pendência e limitação de correção | HU10/HU13, pendentes |
| Repetir tarefa por teclado/zoom/celular | Foco, leitura, alcance e ausência de bloqueio | RNF01/RNF12 |

Registrar por tarefa: concluída sem ajuda/com ajuda/não concluída, erro observado, tempo quando fizer sentido, número de participantes e dispositivo. Relatar contagens com denominador; amostra pequena pede interpretação descritiva, não prova estatística. Perguntas posteriores: o que foi confirmado, qual fuso foi usado e qual próximo passo faria diante da falha. Não registrar sucesso apenas porque a pessoa clicou.

Roteiro de relatório futuro: data/commit/ambiente, método e limitações, participantes anonimizados, tarefas, resultados, achados por gravidade, ações realizadas e reteste. Nenhum resultado de participante existe agora. A documentação de planejamento não encerra RNF01/RNF12 nem equivale a teste com usuários.

## Próximas ações e vínculo com avaliação

Antes do código de jornada: aplicar protocolo HU14 decidido em 11/10/2026 e detalhar contratos do recorte RF17/RF18; aplicar RN05 revisada e planejar T27 para UX05. Implementar e retestar regularização de UX06 e recuperação HU14, incluindo compreensão de espera, rejeição e horário de repetição. Na próxima melhoria de interface: testar foco/teclado, rótulos, suporte e contraste; sem quebrar login/CSRF. Depois: testar reflow/zoom, Safari/dispositivo físico e fluxos completos. Revisar regras, histórias, interface e testes em conjunto.

Interpretação do cenário: contextos internacionais e hipóteses explícitas. Organização/modelagem: ST → HU → RF/RN → T. Comunicação/decisões: riscos e limites registrados. Experiência/boas práticas: prevenção, recuperação e avaliação planejadas. Qualidade de código permanece sustentada por implementação/testes, não por este documento sozinho.

## Etapa de tela de jornada — 14/10/2026

Implementação, relação com os conteúdos de IHC, revisão e evidências em [tela-jornada.md](tela-jornada.md). Estados, ações, foco, diário de recuperação, fluxo de navegador/SQL e reflow possuem evidência na CI, com alcance e limitações descritos em tela-jornada.md. A ampliação de cenários com abas/troca de conta só é validada quando os logs correspondentes forem conferidos. Os registros anteriores de UX05/HU03–HU07/HU14 são históricos e recebem evidência progressiva nesse documento. UX06/regularização e pesquisa com usuários seguem pendentes.
