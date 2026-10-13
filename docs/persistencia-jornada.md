# Persistência de jornada — SQL Server e fronteira JPA

Status desta camada: schema, mapeamento JPA, reconstituição e operações transacionais de armazenamento aprovados em CI no commit 4553954, propostos na [PR #3](https://github.com/Juliarezende521/work-journey-platform/pull/3), sem integração à main. A fatia posterior de serviço com identidade/Clock e recibos tem status em [marcacoes-idempotencia.md](marcacoes-idempotencia.md). API/Angular e regularização continuam pendentes; `journeyAvailable` continua false.

Histórico: fatia iniciada em 13/10/2026, após a segunda análise de segurança/desempenho/legibilidade. A primeira fatia entregou somente o schema, aprovado inicialmente no commit 375f8d6; sua evidência abaixo não deve ser confundida com a validação posterior do JPA.

## Aderência e decisões

Referências canônicas: RF03–RF14, RNF02/RNF05/RNF06/RNF07/RNF10/RNF13 e ADR03–ADR05. V1 permanece intacta. V2 adiciona `work_session` e `work_event`, sem criar registros fictícios de jornada ou alterar o login.

| Decisão | Motivo e limite |
|---|---|
| `datetimeoffset(7)` e CHECK de offset zero | Armazenar UTC com precisão de 100 ns. IANA é uma coluna separada, validada pelo Java; SQL não valida nomes IANA nem calcula a data de referência. |
| Data/fuso congelados na jornada | Histórico é vinculado à data inicial, inclusive madrugada/viagens. O serviço futuro deve impedir sua alteração; UPDATE da tabela não garante imutabilidade de campos individualmente. |
| Índice único filtrado por encerramento nulo | Uma jornada aberta por colaborador, inclusive com entradas concorrentes. Permite mais de uma jornada encerrada no mesmo dia. Não impede sobreposição histórica por si só. |
| FK composta colaborador/usuário e jornada/autor | Impede atribuir evento online a outra conta. Não substitui autorização HTTP; regularizações terão autor/aprovador e tabelas próprios. |
| Sequência positiva e única por jornada | Desempate de eventos com instantes iguais. ENTRY somente na sequência 1; outros tipos somente após ela. Não garante sequência contígua, evento inicial presente ou todas as transições. |
| `version bigint >= 0` | Prepara a versão otimista. Só o futuro mapeamento/UPDATE condicional pode comprovar incremento e rejeição de versão obsoleta. |
| Índice por colaborador/data/id | Segue o filtro e a ordenação previstos para histórico paginado. Não há ganho de latência medido ou promessa de SLA. |
| Runtime sem UPDATE/DELETE de eventos | Permissões verificadas no ambiente CI; originais são preservados por esse usuário. Uma conta privilegiada ainda pode alterá-los. Provisionamento real deverá reproduzir as permissões. |

Campos `opened_at_utc` e `last_event_at_utc` permitem conferir limites temporais da linha de jornada; `closed_at_utc >= last_event_at_utc` admite a futura saída efetiva regularizada sem inserir evento online artificial. A revisão manual permanece pendente. Duração não é duplicada em uma coluna; cálculo/reconstituição usam o domínio, com a integração JPA descrita na segunda fatia abaixo.

## Verificações do schema — histórico da primeira fatia

Na primeira fatia, a CI usou um SQL Server descartável: aplicou primeiro V1 (`spring.flyway.target=1`), executou os testes existentes e depois migrou para V2 e executou `SqlServerIT,JourneySchemaSqlServerIT`. Isso verificou evolução a partir de V1 e aplicação ordenada em banco inicialmente vazio. Na segunda fatia, esse fluxo foi substituído pelo teste Flyway independente de JPA, descrito abaixo. Nenhuma migration aplicada foi alterada e SQL Server não foi simulado com H2.

Sete testes novos:

1. Unicode, data/fuso, precisão 100 ns, offset zero e sequência com instantes iguais.
2. Rejeição de segunda jornada aberta, liberação após fechamento e independência entre colaboradores.
3. Rejeição de órfãos e autoria de outra conta.
4. Estado, versão, limites temporais e offset inválidos na jornada.
5. Sequência duplicada, tipo/posição inválidos e instantes inválidos no evento.
6. Rollback JDBC restaura estado/versão e remove evento. Isso não comprova transação do serviço ainda inexistente.
7. Duas conexões independentes: exatamente uma abertura confirmada e outra rejeitada pelo índice único. Isso não comprova concorrência de transições ou idempotência.

Os testes usam prepared statements, dados fictícios e rollback; a massa confirmada do teste concorrente é removida após terminar os workers. O perfil de avaliação continua criando exatamente duas contas/colaboradores. Verificação separada de permissões exige INSERT e impede UPDATE/DELETE de eventos, DELETE de jornadas e ALTER da tabela para o runtime.

Local: `git diff --check` e `bash -n scripts/ci/validate-auth.sh` aprovados. Maven offline bloqueado por parent ausente; online bloqueado por DNS do Maven Central, antes de compilar/executar testes. Não declarar a suíte nova aprovada com base em execuções anteriores.

### Evidência integrada — 13/10/2026

- Código 375f8d6fd8d8ae291b8efefb3f5be166b2d550b6; checkout de merge da PR 7602f726505859e1cad4c35679c3b84989efc8e9.
- [Foundation checks 38401824218](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38401824218) aprovado; logs dos jobs 115311100051/115311100392 conferidos: backend 29 testes sem falhas/erros/ignorados e BUILD SUCCESS; frontend build e 18 testes aprovados.
- [SQL Server/browser 38401824440](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38401824440) aprovado; log do job 115311101060 confirma schema inicialmente vazio → V1 → V2, 7 testes JourneySchemaSqlServerIT + 2 SqlServerIT sem falhas/erros/ignorados, verificação de grants, seed/reinício e 2 percursos Chromium HTTPS aprovados. Os testes de contas são repetidos antes/depois da migration e os 29 testes backend reaparecem; não somar repetições como testes distintos.
- A primeira execução 38401651598 falhou no teste de autoria: o fixture violava também o índice de jornada aberta, que foi verificado antes da FK (2601 em vez de 547). O teste passou a verificar a FK antes de inserir a jornada válida, isolando a condição; migration e proteções não foram alteradas. Achado baixo de desenho de teste, resolvido; não era evidência de FK ausente ou autoria indevida aceita.
- Esta atualização registra a evidência do código acima. Conferir também os checks do head final antes de eventual integração; documento não concede autorização de merge.

## Revisão e segunda análise

| Achado/critério | Evidência, impacto inicial/final e falso positivo | Validação/ação |
|---|---|---|
| P01 — integridade/autorização | FK composta implementada; afirmar que ela autoriza requisições seria falso positivo. Médio como risco futuro de API; nenhum acesso indevido atual demonstrado. | Testes cruzados HTTP quando houver serviço; IDs devem vir da sessão. |
| P02 — concorrência | Índice único implementado; versão sozinha não é lock. Médio, pendência do serviço, não falha comprovada do schema. | Teste concorrente de abertura nesta fatia; versão obsoleta/transições/recibos atômicos na próxima. |
| P03 — dados temporais | CHECKs UTC e ordem local implementados; data IANA, ordem entre eventos e estado reconstruído não são garantidos pelo DDL. Médio como risco de integração, mantido. | Reconstituir pelo domínio e testar madrugada/DST/precisão na fronteira Java/JDBC. |
| P04 — preservação | Permissões restritas previstas em CI; não existe garantia contra conta privilegiada ou implantação com grants diferentes. Médio condicional, mantido. | Conferir permissões reais; revisão manual separada, sem reescrever original. |
| P05 — desempenho | Índices ligados às consultas previstas; não há gargalo ou benefício medido. Sem severidade de desempenho atribuída. | Medir plano/latência com massa quando houver consultas; sem novos índices por preferência. |
| P06 — coesão/legibilidade | DDL isolado e helpers de teste com vários consumidores; sete cenários distintos. Contagem de linhas não comprova complexidade excessiva. Baixo como manutenção potencial. | Manter nomes de teste e barreiras explícitos; não misturar HTTP/JPA nestes testes do schema. |

XSS/manipulação de arquivos/APIs externas: sem novos caminhos nesta mudança. SQL usa parâmetros nos testes, sem entrada de usuário em DDL. Sem nova credencial real, resposta pública, DTO ou regra de sessão. Limites de segurança são registrados acima; falta de implementação não é prova de exploração atual.

## Fronteira JPA e reconstituição — implementação atual

`WorkSession.restore` reconstrói o agregado pelos eventos originais, preservando IDs, autor, sequência e instantes. Verifica ENTRY inicial, identidade, sequência contígua, IDs distintos, data inicial no fuso e transições/ordem temporal. `record` e replay usam a mesma atualização de intervalos, sem copiar a regra de duração para o banco. Não gera novos IDs durante o replay.

`WorkSessionRow`/`WorkEventRow`, no pacote journey.persistence, representam o armazenamento. OffsetDateTime UTC com TimeZoneStorage NATIVE e datetimeoffset(7) na fronteira; Instant/Duration continuam no domínio. Campos de referência/autoria são updatable=false; eventos são Immutable e não têm setters. O carregamento verifica que estado, entrada, último evento e encerramento correspondem ao replay; não repara dados inconsistentes silenciosamente. Fechamento manual precisará incorporar a revisão efetiva no modelo de leitura, ainda não implementada.

`JpaWorkSessionStore.create` aceita somente ENTRY inicial. `append` aplica uma única transição, exige versão esperada e atualiza o pai com @Version antes de inserir o novo evento. As duas gravações usam uma transação REQUIRED: podem integrar a futura transação do recibo. Não há retry automático nem captura que continue em transação inválida. Um comando válido muda o estado mesmo com instante igual, gerando UPDATE real e incremento de versão.

`findOwned`/`append` filtram por jornada e colaborador; isso impede acesso cruzado nesse componente, mas não comprova a derivação da identidade autenticada no HTTP. O componente ainda recebe identidade/Instant de chamadas internas. Não expor o agregado como JSON nem tratar esse armazenamento como serviço de marcação pronto.

Sob READ COMMITTED, a leitura de eventos pode observar uma gravação posterior à leitura do pai. Uma consulta escalar da versão, após ler os eventos, identifica essa situação como conflito antes de comparar o checkpoint; o UPDATE com @Version cobre mudanças posteriores. Não há lock em memória ou promise de snapshot global. O retorno pode ficar obsoleto após a transação, por isso mutações exigem versão esperada.

### Testes novos desta fatia

- 3 testes unitários de replay: IDs/duração/continuação em pausa no horário de verão; entrada/identidade/data/sequência inválidas; IDs duplicados, transições inválidas e relógio regressivo.
- 7 testes JourneyPersistenceSqlServerIT: roundtrip UTC/100 ns, incremento com instantes iguais e originais preservados; outro colaborador; versão antiga; checkpoint divergente; rejeição de create com transições sem versão; rollback após flush da operação JPA em transação externa; duas transições em threads/transações independentes com exatamente uma vencedora e um evento novo.
- 1 teste MigrationUpgradeSqlServerIT: vazio → V1 → V2 → validate → nova migrate sem alterações. Substitui o boot com target=1: entidades atuais exigem V2, portanto o teste da etapa antiga não deve iniciar Hibernate. Nenhuma migration aplicada foi editada e ddl-auto=validate continua no ambiente normal.
- Após grants, a CI repete somente o método runtimePermissionsSupportAppendWithoutRewritingOriginals com a conta real da aplicação; ele é revertido ao final e comprova que o armazenamento funciona sem UPDATE/DELETE nos eventos. O restante da suíte SQL usa a conta de migration para limpar a massa confirmada dos testes concorrentes. Repetições não aumentam a contagem de testes distintos.

### Revisão e segunda análise da fatia JPA

| ID / critério | Causa, evidência, impacto inicial/final e falso positivo | Verificação e ação |
|---|---|---|
| J01 — integridade/concorrência | Somente inserir filho não incrementaria a versão do pai. Médio → baixo residual após validação; checkpoint muda em toda transição e @Version participa do UPDATE. Anotação sozinha não comprova a proteção. | Testes de versão igual/antiga e concorrência aprovados em SQL Server no commit indicado abaixo; manter a regressão. |
| J02 — consistência de leitura | Duas consultas READ COMMITTED podem observar versões diferentes. Médio → médio, mitigado por consulta escalar da versão; não classificar como corrupção confirmada. | Teste concorrente cobre conflito; interleaving específico entre consultas ainda precisa de teste determinístico. Não anunciar isolamento de snapshot. |
| J03 — autorização/fonte temporal | Filtro de colaborador implementado; chamadas internas ainda recebem IDs/Instant. Médio condicional → médio pendente na aplicação HTTP, sem desvio exposto demonstrado. | Derivar identidade da sessão e instante de Clock no futuro serviço; teste cruzado desta camada não aprova RNF03 completo. |
| J04 — preservação/modelagem | Campos JPA congelados, eventos imutáveis e replay sem novos IDs. Médio como risco de integração → baixo residual condicionado a grants/conta privilegiada. Não afirmar auditoria completa por usar Immutable. | Testar roundtrip e runtime restrito; revisão manual permanece separada. |
| J05 — desempenho | Replay é O(n) por jornada, com memória O(n); leitura própria usa três consultas de quantidade constante, sem lazy loop por evento. Baixo condicional → baixo; nenhum gargalo medido. | Consulta extra resolve a consistência J02. Medir com massa antes de otimizar; não adicionar checkpoint de duração nem remover verificações por preferência. |
| J06 — coesão/legibilidade | Domínio preserva regras; rows fazem mapeamento; store coordena armazenamento. Compartilhar apply evita duplicar cálculo no replay. Baixo → baixo como manutenção potencial, sem defeito confirmado. | Nomes, comentários explicando flush/READ COMMITTED e testes por cenário; sem interface genérica ou dependência nova. |
| J07 — comunicação de falhas | Repository traduz IllegalArgumentException/IllegalStateException para InvalidDataAccessApiUsageException; testes confirmam o contrato interno. Baixo → baixo no armazenamento, sem endpoint exposto. Usar essa exceção como contrato público indistinto seria uma conclusão indevida. | Definir erros de aplicação/HTTP distintos para recurso invisível, transição e corrupção antes de expor API; não devolver causas SQL ao cliente. |

Segurança separada: novas consultas JPQL são parametrizadas; não há novo template HTML, upload, caminho de arquivo, API externa ou log de credencial. Sessão/CSRF não foram modificados. Validação do replay impede aceitar dados estruturais inválidos nesta fronteira; não substitui validação de DTO, autenticação ou autorização de requisições. RuntimeException de infraestrutura não é contrato HTTP: tradução pública ainda será definida antes de expor endpoints. Sem achado alto confirmado nesta revisão; evidência integrada desta fatia abaixo, com limites J02/J03/J07 mantidos.

### Evidência integrada JPA — 13/10/2026

- Código 4553954200bc1ee377001f306e989d44ccf0ef9c; checkout de merge 9ffda019e392514f6c08c9bbb827ebd25f4ab08a. Ajuste de teste nesta versão usa um segundo colaborador provisionado e UUID textual nas consultas nativas do fixture.
- [Foundation checks 38408107860](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38408107860) aprovado: backend compilado/empacotado com 32 testes (29 anteriores + 3 replay), frontend build e 18 testes aprovados. Jobs 115332957947/115332957339.
- [SQL Server/browser 38408107981](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38408107981) aprovado; log 115332957455 conferido: 1 teste de upgrade/reaplicação Flyway, 16 testes SQL (7 schema + 7 JPA + 2 contas) sem falhas/erros/ignorados; repetição do roundtrip com usuário runtime restrito aprovada; regressão de seed/reinício e 2 percursos Chromium HTTPS aprovados. São 17 testes SQL distintos, não 18 por repetir o runtime.
- O primeiro código JPA 22315b3 também passou em 38407745185/38407745606; a evidência final acima inclui o reforço do acesso cruzado. Não foram relaxados grants, schema, versão ou asserções para obter aprovação.
- Local: diff e Bash aprovados; tentativa Maven offline bloqueada antes de compilar por parent ausente, sem execução de testes local. Não confundir resultado local com CI.
- Testes de banco confirmam modelagem, organização, qualidade, decisões e práticas da fatia. Comunicação inclui limitações rastreáveis; interpretação internacional tem evidência temporal parcial. UX/dispositivos e autorização HTTP permanecem pendentes, sem aceite global ou aprovação do técnico da empresa.
- Esta atualização é documental; o código acima foi validado. Conferir também os checks do head final antes de eventual integração, que ainda depende da autorização aplicável.

Referências técnicas: [Hibernate 6.6 — tipos temporais e locking](https://docs.hibernate.org/orm/6.6/userguide/html_single/), [SQLServerDialect 6.6](https://github.com/hibernate/hibernate-orm/blob/6.6/hibernate-core/src/main/java/org/hibernate/dialect/SQLServerDialect.java). As referências explicam os mecanismos; a aprovação do mapeamento/precisão depende dos testes SQL indicados.

## Próxima fatia após validar JPA

No checkpoint JPA, a próxima fatia era serviço com identidade/Clock e recibos; essa evolução agora tem status em [marcacoes-idempotencia.md](marcacoes-idempotencia.md). API e frontend continuam pendentes, assim como regularização P0. Responsividade/IHC não foram validadas por testes de banco. Não há aceite global do desafio nem aprovação do técnico da empresa nesta etapa.
