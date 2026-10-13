# Serviço de marcação online e recibos

Etapa iniciada em 13/10/2026 na PR #3, após schema e JPA validados. Status inicial: código e testes escritos, CI pendente. Não há endpoint HTTP, diário Angular ou regularização nesta fatia; journeyAvailable continua false. Referências: RF03–RF06/RF14, RNF03/RNF05/RNF06/RNF07/RNF10/RNF11 e ADR03/ADR04.

## Contrato interno e responsabilidade

`OnlineMarkingService.execute(UUID key, MarkingCommand command)` não recebe usuário, colaborador, fuso, horário ou versão do cliente. `CurrentEmployeeIdentity` obtém IDs do AccountPrincipal autenticado, com ROLE_EMPLOYEE; repository revalida vínculo e conta ativa no banco. O teste usa SecurityContext sintético; a sessão HTTP real ainda precisa ser validada quando houver controller. A verificação de ativo vale para este serviço, não revoga automaticamente todas as sessões/rotas existentes.

Command contém tipo e expectedWorkSessionId: ENTRY exige alvo nulo; demais eventos exigem ID. Chave UUID e comando são obrigatórios. Validação estrutural não produz recibo de marcação; o contrato público de 400/401/403 depende da próxima camada. ENTRY usa fuso atual do cadastro, não o snapshot antigo da sessão. Clock.systemUTC injetável fornece o instante após adquirir o lock; precisão truncada a 100 ns.

| Componente | Responsabilidade |
|---|---|
| CurrentEmployeeIdentity | Ponte entre contexto de autenticação e IDs internos |
| MarkingCommand | Estrutura válida e hash canônico do payload |
| OnlineMarkingService | Vínculo/ativo, lock, replay, decisão de negócio, tempo e transação |
| JpaWorkSessionStore / WorkSession | Persistência com versão / transições e cálculo, respectivamente |
| OnlineMarkingReceipt / Store | Snapshot imutável / leitura própria e INSERT dentro de transação obrigatória |
| MarkingResult | Resultado original, sem entidade JPA, identidade pessoal ou credenciais |

## ADR — serialização por colaborador no SQL Server

PESSIMISTIC_WRITE na consulta do colaborador ativo/vinculado serializa comandos daquele colaborador na transação do banco, inclusive entre instâncias da aplicação. Não é mutex local nem garantia de lock físico limitado a uma única linha em qualquer plano. Versão otimista e índices únicos permanecem como defesa de integridade.

Todos os futuros escritores de jornada, incluindo aprovação manual, devem seguir a mesma ordem: bloquear colaborador alvo → verificar recibo → ler/validar jornada → gravar → recibo → commit. Não bloquear várias contas em ordens arbitrárias. O serviço online tem timeout transacional de 15 segundos como limite técnico inicial, sem SLA prometido; lock timeout, deadlock e demais falhas de banco propagam para rollback, sem retry automático.

Isso permite validar conflitos conhecidos antes de provocar um erro SQL que marcaria a transação como inválida. Não capturar uma violação de constraint/versão e gravar recibo na mesma transação já falhada. Falhas de infraestrutura não são resultados terminais de marcação; não há resposta de sucesso antes do commit do serviço. Se houver transação externa, sucesso só pode ser comunicado após seu commit.

## Resultado e recuperação

1. Bloquear/revalidar colaborador derivado da sessão.
2. Consultar recibo por colaborador + ONLINE_EVENT + chave **antes** do estado atual.
3. Mesmo payload: retornar o snapshot original, com os mesmos IDs, instantes e status, inclusive após fechar/abrir outra jornada. Payload diferente: 409 KEY_REUSED; o recibo original não é alterado.
4. Sem recibo: obter instante do servidor, validar intenção e alvo. ENTRY com jornada aberta → ALREADY_OPEN; transição sem aberta → NO_OPEN_SESSION; alvo distinto → TARGET_CHANGED, sem consultar/expor o recurso alheio. Relógio anterior ao último evento ou ao encerramento anterior numa nova ENTRY → CLOCK_REGRESSION. Transição inválida → INVALID_TRANSITION.
5. Gravar resultado 201 RECORDED ou conflito definitivo 409, na mesma transação de qualquer evento/estado/versão. Rejeição não grava evento e mantém resultado terminal para replay.

Hash: SHA-256 hexadecimal de `v1|ONLINE_EVENT|TYPE|TARGET`, com UUID canônico ou `-` para ENTRY. Tipo/alvo também são comparados explicitamente. Formato v1 é estável; futura evolução precisa preservar significado dos recibos antigos, sem descartar chaves por expiração neste MVP. Hash não é autenticação.

`lookup(key)` só retorna recibo próprio após revalidar vínculo/ativo. Retorna Optional vazio se ainda não visível; isso **não prova** que a primeira requisição terminou ou falhou. Na futura API, consulta de recibo usa HTTP 200 com httpStatus original no corpo; ausência usa 404. O POST deverá retornar o status original 201/409. Recibo guarda tipo, IDs de resultado, ocorrência e confirmação UTC; conflitos não expõem sessão/evento alheios. A ausência de recibo nunca autoriza gerar automaticamente nova chave/repetir uma nova intenção.

V3 cria idempotency_request, chave única por colaborador/operação, hashes/códigos/UTC validados e FKs compostas que ligam sucesso à jornada própria e ao evento correto. expected_work_session_id não é FK: um alvo inexistente/alheio pode gerar rejeição definitiva. Campo de operação é fixo ONLINE_EVENT, reservado ao fluxo online; regularização continua separada. V1/V2 não foram editadas.

Runtime recebe somente SELECT/INSERT nos recibos, sem UPDATE/DELETE; eventos mantêm a mesma restrição. Não há senha, cookie, CSRF, email, nome ou motivo no recibo. Retenção de todos os recibos do MVP é uma decisão de confiabilidade; volume e futura política de retenção precisam ser dimensionados sem permitir reexecutar chave antiga.

## Verificação escrita

- 4 novos testes unitários: estrutura/hash canônico do comando e resolução da identidade com principal/autoridade reconhecidos.
- 14 testes SQL do serviço: fonte temporal e fuso do banco; replay de sucesso após mudanças de estado; intenção velha contra jornada nova; rejeição persistida; chave com outro payload; contas/chaves separadas e alvos invisíveis; vínculo adulterado; conta desativada com snapshot ativo; transição e relógio inválidos; regressão antes do encerramento anterior; rollback de evento/estado/recibo; chave igual concorrente; ENTRY com chaves distintas; pausa incompatível concorrente; ausência de recibo durante processamento; FK de sucesso próprio e unicidade SQL independente do lock. Vários aspectos compõem um mesmo teste; conferir métodos/logs, não somar cenários como testes distintos.
- Teste de runtime repete somente runtimePermissionsPreserveOriginalReceipts com a conta restrita. Massa transacional é revertida; concorrência usa contas fictícias confirmadas e cleanup privilegiado restrito ao banco descartável, na ordem recibo → eventos → jornadas → colaborador → usuário.
- Upgrade Flyway passa de V1 a V3, valida checksums e confirma reaplicação sem mudanças. SQL Server real, sem H2.
- Local: diff e sintaxe Bash aprovados. Testes Java dependem da CI; resultados JPA anteriores não validam esta nova etapa.

## Revisão e segunda análise dos achados

| ID | Evidência/causa e impacto inicial → final | Falso positivo, validação adicional e ação |
|---|---|---|
| M01 — idempotência/integridade | Lock, replay anterior ao estado e transação com recibo implementados. Alto potencial se houvesse gravação dupla → médio como risco em validação, não vulnerabilidade confirmada. | Anotação/índice isolados não comprovam atomicidade. Testes de chave igual/distinta, retorno original, rollback e runtime escritos; CI pendente. |
| M02 — tempo/sobreposição | Só conferir relógio na jornada aberta deixaria a ENTRY posterior vulnerável a regressão antes do fechamento passado. Médio → médio mitigado por MAX(closedAtUtc) sob lock. | Não havia exploração exposta demonstrada; Clock regressivo é condição real possível. Teste de nova entrada anterior ao encerramento escrito. |
| M03 — autorização | IDs vêm do SecurityContext e vínculo/ativo do banco; recibos filtrados por colaborador. Alto potencial de acesso cruzado → médio em validação, sem acesso indevido confirmado. | UUID/chave não são credenciais; testes sintéticos não aprovam HTTP/CSRF. Cruzamentos, claims incompatíveis e conta inativa escritos; HTTP pendente. |
| M04 — recuperação | Ausência de recibo pode coexistir com comando em andamento. Médio → médio de protocolo, condição testada por barreira antes do INSERT. | Não tratar ausência como falha; frontend ainda não implementado. Testar diário/reload/rede e repetir explicitamente mesma chave na próxima camada. |
| M05 — desempenho | Lock mantém consistência mas serializa conta; replay é O(n), MAX de encerramento e revalidação interna acrescentam consultas. Baixo condicional → baixo, sem gargalo medido. | Não propor remover lock/validação por estilo. Medir plano, espera e latência com massa; índice de histórico limita busca por colaborador, sem SLA ou custo constante prometido. |
| M06 — responsabilidades/legibilidade | Resolver, comando, domínio, serviço e stores têm responsabilidades distintas; regras de transição não são copiadas, nextFor é reutilizado. Baixo → baixo de manutenção potencial. | Validação pura anterior à escrita é necessária para recibos de rejeição sem transação inválida. Não confundir checagem + gravação protegida com duplicação de código a eliminar cegamente. |

Segurança exclusivamente: queries parametrizadas e UUID/tipo estruturados, sem SQL concatenado com entrada. Nenhum HTML, upload, caminho de arquivo ou API externa novo; XSS/manipulação de arquivos não ganharam superfície nesta fatia. Mensagens/DTOs não contêm credenciais ou stack traces; tratamento HTTP ainda precisa ser implementado e testado. Constraints não substituem identidade autenticada; grants de CI não comprovam implantação real. Ausência de achado alto confirmado não é certificação de segurança.

A aderência internacional mantém UTC/IANA/data congelada e permite múltiplas jornadas sem sobreposição pelo fluxo online. Organização/modelagem, qualidade, decisões e comunicação têm rastreabilidade; UX/dispositivos e aceite global continuam pendentes. Não há conformidade trabalhista por país inferida.

Referências: [Spring Data JPA — locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html), [Microsoft — table hints](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table). Mecanismos precisam da evidência SQL desta versão, não apenas da documentação.

## Próximo passo

Após validar o serviço, expor DTOs/controllers e erros públicos, conferir sessão/CSRF e consultas próprias; depois diário mínimo, reconciliação e interface Angular. Regularização P0, histórico/resumo e validação completa de IHC/dispositivos continuam no backlog.
