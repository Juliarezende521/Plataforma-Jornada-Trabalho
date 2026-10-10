# Levantamento de requisitos — Plataforma de Jornada de Trabalho

Status: baseline proposta para implementação, sem requisitos ou testes considerados concluídos.

## Objetivo e fontes

Centralizar as marcações e consultas de jornada da Dungeons & Dragons Group, com colaboradores no Brasil e Europa, inclusive remotos e em viagem. Fonte principal: enunciado fornecido na entrevista. Fonte complementar: escolha de Java pela desenvolvedora e informação recebida de que a empresa utiliza SQL Server; não houve entrevista adicional com RH ou usuários.

Origem E = explícita no enunciado; C = derivada do contexto; D = decisão de projeto. Prioridade P0 = núcleo de entrega; P1 = complemento após o núcleo. A prioridade é nossa proposta, não uma classificação informada pela empresa. “Núcleo” inclui decisões necessárias à confiabilidade; não significa que todas foram exigidas textualmente.

## Atores e limites de acesso

| Ator | Necessidade | Acesso proposto |
|---|---|---|
| Colaborador | Marcar jornada e consultar seus registros | Apenas registros próprios |
| Gestor (P1) | Acompanhar rotina da equipe | Consulta dos colaboradores vinculados; sem marcar por eles |
| RH (P1) | Consultar pendências e tratar correções | Consulta geral e aprovação de correções, se implementada |
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
| RF06 | E/D | P0 | Registrar saída | Só é aceito enquanto trabalhando; encerra a jornada |
| RF07 | C/D | P0 | Exibir jornada atual | Mostra estado confirmado, última marcação e ações permitidas |
| RF08 | E/D | P0 | Consultar histórico próprio | Filtra por data de referência e apresenta resultados paginados, sem registros alheios |
| RF09 | E/D | P0 | Consultar detalhe | Lista eventos ordenados e explica os intervalos que compõem a duração |
| RF10 | C/D | P0 | Apresentar resumo do período | Soma jornadas encerradas e apresenta quantidade de pendências separadamente |
| RF11 | C/D | P0 | Consultar em fusos distintos | Mudar fuso de visualização altera horários exibidos, sem mudar instantes, duração ou data de referência |
| RF12 | C/D | P0 | Suportar madrugada e múltiplas pausas | Jornada atravessa meia-noite e desconta todos os intervalos de pausa corretamente |
| RF13 | C/D | P0 | Sinalizar jornada incompleta | Sem saída, aparece pendência e eventual total provisório; não entra no total fechado |
| RF14 | C/D | P0 | Proteger repetição da marcação | Mesma chave e payload retornam o resultado original; payload diferente gera 409 |
| RF15 | C/D | P1 | Consultar equipe por gestor | Usuário gestor só visualiza colaboradores vinculados |
| RF16 | C/D | P1 | Consultar registros por RH | Perfil RH consulta colaboradores com autorização no servidor |
| RF17 | C/D | P1 | Solicitar correção com motivo | Guarda proposta e justificativa sem alterar marcação original |
| RF18 | C/D | P1 | Aprovar/rejeitar correção | RH diferente do solicitante decide; histórico e cálculo efetivo preservam rastreabilidade |

Consulta por gestor/RH é especialmente aderente ao problema, mas o núcleo será entregue primeiro. A limitação sem RF17/RF18 é relevante: uma saída esquecida mantém a jornada aberta e bloqueia nova entrada. Não criar uma saída artificial apenas para desbloquear o usuário. Declarar essa limitação e priorizar correção caso haja tempo.

## Requisitos não funcionais

| ID | Origem | Requisito | Verificação proposta |
|---|---|---|---|
| RNF01 | E/C | Responsividade | Fluxos essenciais em 360 px e desktop, sem rolagem horizontal geral |
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

## Regras de negócio

RN01–RN12 são definidas em proposta.md e constituem a referência para estados, pausas, UTC, fuso de referência, data de início, total consolidado e ausência de operação offline. Os requisitos acima não autorizam cálculo de horas extras, desconto salarial ou enquadramento legal por país.

## Fluxos essenciais

| Caso | Pré-condição | Fluxo de sucesso | Alternativa |
|---|---|---|---|
| UC01 — Login | Conta fictícia provisionada | Obter CSRF → autenticar → consultar identidade | Credencial inválida: erro genérico; sessão ausente: 401 |
| UC02 — Marcar | Sessão válida e conexão online | Consultar estado → enviar tipo + chave → confirmar resposta → atualizar tela | Estado incompatível: 409; timeout: repetir com mesma chave; CSRF inválido: renovar de forma controlada |
| UC03 — Histórico | Sessão válida | Selecionar período/fuso → receber página → abrir detalhe | Período inválido: 400; recurso alheio: 404; lista vazia: mensagem explicativa |

## Pendências e premissas

- Prazo, versões instaladas, sistema operacional e arquitetura do computador ainda não informados.
- Regras de pausas remuneradas, jornadas contratuais e fechamento legal por país não informadas; não inferir.
- Fuso de referência provisionado no cadastro; viagens alteram visualização, não a vinculação histórica.
- Um usuário pode abrir mais de uma jornada no mesmo dia após encerrar a anterior; não há sobreposição.
- Fusos IANA são validados pelo Java. Mudanças administrativas no fuso não reclassificam jornadas anteriores.
- Sem aprovação formal dos stakeholders; estas decisões serão publicadas como hipóteses do desafio.

## Definição de pronto do núcleo

RF01–RF14 e RNF aplicáveis implementados; migrations executadas em SQL Server vazio; testes críticos aprovados com evidências; interface utilizável; README reproduzido; limitações declaradas; repositório sem segredos. Todos os itens estão inicialmente pendentes.
