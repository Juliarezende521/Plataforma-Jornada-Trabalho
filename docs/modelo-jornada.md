# Modelo inicial de jornada — domínio online

Modelo iniciado em 11/10/2026 e validado em CI em 12/10/2026. Validação inicial: compilação/empacotamento backend e 24 testes aprovados (12 existentes + 12 novos de domínio); build frontend e seus 14 testes aprovados. PR #1, inicialmente em rascunho na branch validate/journey-domain, integrada à main em 12/10/2026 após autorização e CI do head final 23363f5 aprovada; merge f88ffe6. A refatoração posterior e suas contagens atualizadas estão em [revisao-qualidade.md](revisao-qualidade.md). Não há endpoints, mapeamento JPA ou migrations de jornada; journeyAvailable permanece false.

## Revisão antes da implementação

As três decisões estão alinhadas em requisitos, histórias e plano de validação: RN05 permite saída durante pausa; RF17/RF18 incluem regularização mínima P0; RF14/HU14 definem recuperação por diário mínimo e recibo autenticado. Esses documentos não comprovam implementação. O recorte de regularização não concede consulta geral RF16 e continua dependendo de aprovador. A recuperação não cria fila offline ou marcação retroativa.

## Estrutura implementada

Pacote backend: io.github.juliarezende521.workjourney.journey.

| Tipo | Responsabilidade |
|---|---|
| WorkSession | Agregado de eventos online, estado, data/fuso congelados e duração por intervalos UTC |
| WorkEvent | Registro imutável com IDs, autor, sequência, tipo e instantes de ocorrência/registro |
| WorkState | WORKING, PAUSED e CLOSED |
| WorkEventType | ENTRY, PAUSE_START, PAUSE_END e EXIT |

WorkSession.open cria a jornada e o primeiro evento. record valida transição e instante antes de alterar o agregado; não aceita nova ENTRY ou eventos após fechamento. EXIT é permitido em WORKING/PAUSED, sem produzir PAUSE_END artificial. Eventos com instantes iguais mantêm ordem pela sequência. Instantes são truncados para 100 ns antes de armazenar/calcular; arredondamento visual permanece fora do domínio.

durationAt fornece duração provisória com instante do servidor, interrompendo acumulação em pausa. closedDuration rejeita jornada aberta. O modelo não contém total de período: o serviço de consulta futuro deve excluir jornadas sem encerramento efetivo do consolidado.

O modelo recebe IDs e Instant como parâmetros internos; isso **não comprova autenticação, autorização ou origem confiável do horário**. O futuro serviço deve derivar IDs da sessão, obter Clock do servidor e validar jornada alvo. Não vincular o agregado diretamente a JSON nem aceitar IDs/timestamps arbitrários da interface.

Sem anotações JPA nesta etapa: primeiro isolamos regras determinísticas. A integração deverá definir mapeamento, reconstituição e fronteira de persistência sem duplicar regras, usando transação, @Version efetivamente incrementado e constraints SQL Server. A classe não é um lock/thread-safe compartilhado entre requisições e não impede sozinha duas jornadas abertas.

Regularização manual deverá usar pedido/revisão separada dos eventos originais; não reutilizar record(EXIT) com instante retroativo como se fosse saída online. Idempotência/recibos ainda não fazem parte deste agregado.

## Testes escritos e validação atual

WorkSessionTest contém 12 métodos aprovados cobrindo múltiplas pausas, saída em pausa, madrugada/mês, instantes em mudanças de horário de verão, visualização em outro fuso, transições inválidas, relógio regressivo, sequência com instantes iguais, duração provisória, precisão, snapshots imutáveis e entradas inválidas. Esses testes unitários não aprovam T03–T34 integralmente: não verificam banco, HTTP, autorização, regularização, recibos ou interface de jornada.

Comando esperado no backend: ./mvnw --batch-mode verify. Em 11/10/2026, a tentativa offline falhou por ausência do parent Spring Boot 3.5.16 no cache; a tentativa online falhou por resolução DNS de repo.maven.apache.org. Nenhum teste foi executado nessas tentativas. A alternativa de compilação isolada também não ocorreu: o ambiente dispõe de java, mas javac/jshell não estão disponíveis. Os 12 testes de base e evidências SQL/browser anteriores não validam este novo código.

Em 12/10/2026, a desenvolvedora autorizou usar GitHub Actions: código registrado na branch validate/journey-domain e [PR #1 em rascunho](https://github.com/Juliarezende521/work-journey-platform/pull/1). Commit de código 2f04d758803f565eca22f839a36878de1d95f8c1, testado pelo checkout de merge da PR. [Foundation checks 38199349907](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38199349907) aprovado: backend 24 testes, zero falhas/erros/ignorados e BUILD SUCCESS; frontend build aprovado e 14 testes aprovados. Resultados conferidos nos logs dos jobs backend 114649059513 e frontend 114649059663, não apenas no ícone da CI.

O workflow de regressão SQL Server/autenticação 38199350023 falhou no controle de exatamente duas contas do seed, após SqlServerIT (2 testes) e a suíte backend (24 testes) aprovados; testes de navegador não começaram. Revisão encontrou corrida possível: /api/status pode responder antes do ApplicationRunner/commit do seed, e o script matava a primeira instância imediatamente. A espera foi ajustada para exigir também duas contas e dois colaboradores persistidos antes de reiniciar; limite/asserção final permanecem. Esse workflow cobre schema/login existentes, não persistência de jornadas.

A repetição com o ajuste, commit 2d15a39e6d5449c77b32f9819196e6d9df1b27c6, checkout de merge da PR, passou em [Foundation checks 38199549465](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38199549465) e [SQL Server/browser 38199549481](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38199549481). Logs do job integrado 114649675182 confirmam 2 testes SqlServerIT, 24 testes backend sem falhas/erros/ignorados, BUILD SUCCESS e 2 percursos Chromium aprovados (2,3 min). Verificações de contagem/reinício do seed passaram; não foi necessário relaxar asserções ou proteções de autenticação. Os 24 testes reaparecem nos dois workflows: não são 48 testes distintos.

## Revisão da fatia e limites de aceite

- Interpretação/modelagem: estados, saída em pausa, madrugada, data/fuso de início e cálculo por instantes aderem às decisões RN04–RN09; correção e recuperação têm desenho, não implementação.
- Organização/qualidade: regras isoladas de HTTP/JPA, eventos imutáveis, leitura por snapshots e comandos inválidos sem alteração observada nos testes. Identidade e hora são entradas internas a obter de fontes confiáveis no serviço futuro.
- Segurança/boas práticas: o agregado não concede autorização, idempotência ou exclusão mútua entre requisições. Não expor sua construção como DTO público. A regressão de sessão/CSRF/login existente passou, mas não comprova proteção de recursos de jornada.
- Comunicação/experiência: resultados e limitações registrados; interface de jornada e validação de acessibilidade/dispositivos continuam pendentes. Não anunciar conformidade legal, prontidão de produção ou conclusão global do desafio.

Aceite desta fatia: modelo online compilado e testes unitários aprovados, com regressão da autenticação existente aprovada no commit indicado. Persistência/reconstituição do agregado, transações/versão, uma jornada aberta por colaborador, autorização por recurso, recibos/idempotência e regularização auditada são critérios das próximas fatias, não garantias deste código.

A PR #1 foi integrada mediante autorização em 12/10/2026, após Foundation checks 38200186898 e SQL Server/browser 38200186913 aprovados no head final 23363f5. Os registros acima preservam o histórico do desenvolvimento/validação; o modelo está na main. Próximo passo, após revisão/refatoração de qualidade: implementar/testar migrations, mapeamento/reconstituição e serviços com Clock/identidade. Somente após essas validações expor API/interface de jornada e validar com SQL Server real.
