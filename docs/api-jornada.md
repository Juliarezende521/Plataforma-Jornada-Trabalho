# API de jornada online

Etapa da PR #3, sem merge. Java/Spring MVC sobre o serviço transacional e SQL Server já existentes. Abrange a fronteira HTTP de RF03–RF07/RF14 e RNF03–RNF10; a etapa posterior de [tela de jornada](tela-jornada.md) implementa interface e diário/reconciliação; histórico e regularização continuam pendentes. Não representa aprovação integral desses requisitos. Na etapa original de API, journeyAvailable era false. Desde a tela online, o status anuncia JOURNEY_ONLINE/true fora de bootstrap e FOUNDATION/false em bootstrap; isso não representa aceite completo nem saúde do banco.

## Contrato implementado

HTTPS, sessão autenticada de colaborador e conta ativa no banco para todos os endpoints. POST exige CSRF válido; obtenção/renovação conforme [autenticação](autenticacao.md). Nenhum identificador de usuário/colaborador é aceito no comando. GET não altera jornada. Respostas protegidas recebem `Cache-Control: no-store` da cadeia de segurança.

| Endpoint | Entrada e resposta |
|---|---|
| POST `/api/me/work-events` | `Content-Type: application/json`, um único header `Idempotency-Key` UUID completo; corpo abaixo. 201 aceito ou 409 rejeição de negócio; repetição devolve status e corpo originais |
| GET `/api/me/work-event-requests/{key}` | UUID completo. 200 com recibo próprio e `httpStatus` original (201/409); 404 se nenhum recibo próprio estiver visível |
| GET `/api/me/work-sessions/current` | 200 com `session` e `observedAtUtc`, mais `allowedActions`. Sem jornada aberta: `session=null`, ações `["ENTRY"]` |

```json
{"type":"ENTRY","expectedWorkSessionId":null}
```

Para `PAUSE_START`, `PAUSE_END` e `EXIT`, `expectedWorkSessionId` é obrigatório e deve ser UUID completo. ENTRY aceita campo ausente ou null; rejeita alvo preenchido. Tipo exige nome exato em maiúsculas. UUID permite dígitos hexadecimais maiúsculos/minúsculos, sem espaços nem segmentos abreviados. Objetos extras, campos desconhecidos/duplicados e JSON concatenado são rejeitados. Corpo máximo: 1.024 bytes, verificado pelo tamanho declarado **e pelos bytes lidos**, inclusive sem Content-Length. A leitura é limitada antes de desserializar; a configuração de formulário do Tomcat não seria suficiente para JSON. 415 para mídia incompatível; 400 para contrato inválido; 413 para corpo excedente. Essas falhas não chamam o serviço nem criam recibos.

Recibo: `requestKey`, `type`, `httpStatus`, `code`, `workSessionId`, `eventId`, `occurredAtUtc`, `confirmedAtUtc`. Rejeições não incluem IDs/horário de evento. `KEY_REUSED` nunca substitui o recibo da primeira intenção. GET devolve exatamente o snapshot do recibo original; seu HTTP 200 apenas indica sucesso da consulta.

Jornada atual: `id`, `state`, `workDate`, `referenceTimeZoneId`, `openedAtUtc`, `lastEvent` (id/tipo/instante) e `provisionalWorkDuration` como duração ISO 8601. Datas/instantes vêm do servidor; fuso IANA/data de referência permanecem congelados na jornada. Enquanto trabalhando, duração provisória é calculada até `observedAtUtc`; durante pausa, permanece no tempo já trabalhado. Não representa total fechado nem valor de folha. Sem eventos/identidade completos na resposta. Ações WORKING: PAUSE_START/EXIT; PAUSED: PAUSE_END/EXIT. Servidor revalida cada comando mesmo que uma tela esteja desatualizada.

## Erros e recuperação

Falhas de segurança mantêm 401/403 e o Problem Details compartilhado dos filtros. Erros tratados no controller usam `application/problem+json`, título genérico, status, `correlationId` e header `X-Correlation-Id`; não retornam mensagens de exceção, SQL, identidade ou stack trace. O handler de falhas inesperadas registra somente correlação e classe da exceção. Isso facilita diagnóstico inicial sem registrar payload; instrumentação completa de autenticação/jornada de RNF14 continua pendente. Erros de resolução de rota/mídia são tratados pelo Spring e não têm correlação garantida nesta etapa.

Falhas transitórias de acesso a dados traduzidas pelo Spring retornam 503; demais falhas inesperadas, 500. **Não são recibos terminais nem prova de que a marcação não foi persistida.** Consultar a chave após recuperar acesso. 404 também não prova falha: pode haver primeira transação em andamento. Repetição exige ação explícita, mesma chave e payload, sem retry automático. 409 com recibo é diferente de 5xx sem recibo. Regressão do relógio na consulta não produz duração negativa ou valor silenciosamente corrigido; falha genérica exige nova consulta.

## Organização e revisão com segunda análise

`JourneyController` adapta HTTP; `MarkingRequestReader` limita/desserializa/valida só este contrato; `JourneyResponses` define DTOs explícitos; `JourneyExceptionHandler` traduz falhas. `CurrentWorkSessionService` revalida sessão/conta e carrega exclusivamente a jornada própria. Domínio e entidades não dependem de HTTP. Não há novas permissões SQL ou migrations; V1–V3 permanecem intactas. O serviço de marcação mantém lock, versão e recibo na mesma transação.

| ID / critério / localização | Causa, evidência e impacto inicial → final | Segunda análise e ação |
|---|---|---|
| H01 — segurança/validação — leitura do JSON | Desserialização padrão poderia ignorar identidade/horário extra e aceitar corpo sem limite específico. Médio → médio, preventivamente resolvido antes da exposição: leitor estrito e limite real de bytes | Não era vulnerabilidade HTTP confirmada na etapa anterior, que não tinha endpoint. Tests de campos/duplicação/tamanho e tamanho desconhecido verificam proteção; limites de conexão/tempo do proxy ainda precisam de configuração para produção |
| H02 — segurança/autorização — consulta atual/recibos | Login/UUID isoladamente não comprovam propriedade. Alto potencial → resolvido no caminho implementado: identidade do servidor, par colaborador/usuário ativo e consultas parametrizadas próprias | Não há exploração demonstrada anterior; era risco da nova fronteira. Teste SQL com dois logins reais verifica isolamento e alvo alheio/aleatório equivalentes; conta desativada testa revalidação. Não prova revogação global dos demais endpoints |
| H03 — segurança/erros — controller/advice | Exceções de banco/domínio podem expor detalhe ou sugerir resultado terminal falso. Médio → resolvido no contrato: respostas genéricas, correlação e 5xx sem recibo | A configuração existente do Boot já ocultava mensagem/stack trace; ausência de advice não provaria vazamento. Nenhum vazamento anterior confirmado. Teste injeta mensagens sensíveis e valida ausência na resposta; testes transacionais existentes sustentam atomicidade. Falha de transporte real pós-commit/recuperação Angular ainda depende da próxima etapa |
| H04 — desempenho — consulta atual | Restauração percorre eventos O(n), com consultas de checkpoint/eventos/versão já existentes. Baixo → baixo, custo confirmado; gargalo não demonstrado | Não é N+1 entre jornadas: só uma jornada aberta. Não otimizar por estilo nem remover verificação de versão. Medir quantidade de eventos, latência e concorrência antes de projeção/cache |
| H05 — legibilidade/coesão — fronteira HTTP | Misturar parsing, JPA, regra temporal e tratamento de erro no controller dificultaria manutenção. Baixo potencial → resolvido: responsabilidades explícitas e DTOs independentes | Era prevenção, não defeito observado em código anterior. Controller não replica transições/autorizações; ações derivam do estado confirmado e são apenas orientação, não autorização. Revisar conjunto ao incluir regularização |

Segurança: nenhum caminho de HTML/XSS, upload/manipulação de arquivos ou SQL dinâmico novo. JSON não é renderização HTML; Angular futuro ainda exige revisão própria. JPQL/nativo de produção continuam parametrizados; fixture de SQL isolada não recebe SQL do cliente. Desempenho: limite evita desserialização de payload arbitrariamente grande; não há benchmark nem novo cache. Qualidade: componentes coesos, sem duplicação de regra de duração/transação; acoplamento do controller aos serviços é deliberado. A tradução genérica de erro fica restrita a este controller para preservar demais contratos.

## Validação

Testes escritos: `JourneyHttpTest` (cadeia de segurança real com serviços simulados), `MarkingRequestReaderTest` (corpo sem tamanho conhecido) e `JourneyHttpSqlServerIT` (MockMvc + serviços/repositórios reais + SQL Server, login/CSRF reais, dois usuários, replay, pausa/retorno/saída e desativação). O fluxo principal SQL também é executado com a conta runtime sem DDL/update/delete de eventos/recibos. Testes SQL usam rollback de fixtures; atomicidade/concorrência/commit independente têm cobertura específica no serviço, não são comprovados apenas pelo MockMvc transacional.

Verificação local: `git diff --check` e `bash -n scripts/ci/validate-auth.sh`. Maven offline bloqueado antes da compilação porque o parent Spring Boot não está disponível no cache local. Build/testes Java e SQL foram executados na CI autorizada, com evidências abaixo. O bloqueio local não foi tratado como teste aprovado. Regressão Angular/Chromium continua no workflow existente, sem alegar validação visual de jornada que ainda não existe.

Próximo recorte: integração Angular com jornada atual, marcação e diário mínimo/reconciliação (RF14), antes de histórico/resumo e regularização. Dispositivos/contextos do checklist permanecem obrigatórios no fluxo completo.

### Evidências — 14/10/2026

Código validado: `12078cc31b0552f15ba80520a988d62c83e0685a`; checkout de merge `4b3b935` sobre main `c0853a9`. Logs conferidos, sem testes ignorados nas suítes Java/SQL:

- [Foundation checks 38580118351](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38580118351): build/empacotamento + 54 testes backend (18 adicionais nesta etapa); build + 18 testes Angular. Jobs backend 115907483990 e frontend 115907483598.
- [SQL Server/browser 38580118524](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38580118524), job 115907485663: 1 migration + 33 SQL agrupados = 34 testes SQL distintos, incluindo os 3 novos HTTP; repetições de JPA, serviço e HTTP com a conta runtime aprovadas, sem contar como testes adicionais. Regressão de seed/reinício e 2 percursos Chromium HTTPS (desktop/360 px), incluindo expiração real, aprovados. Não houve interface de jornada nesses percursos.
- Primeiras execuções 38579916310/38579915962 falharam em um teste de simulação de falhas: restub com `when` executava a exceção configurada anteriormente. Alterado para `doThrow`, mantendo todas as verificações de 503/500/403/401, resposta genérica e ausência de recibo. Achado H06: baixo inicial → resolvido; causa confirmada no stack do teste, sem evidência de defeito na API. Não é falso positivo: a falha impedia completar a suíte. Na segunda análise, o alcance ficou restrito ao teste; nenhuma regra/asserção/permissão foi relaxada. A nova execução passou integralmente. As execuções antigas não são apresentadas como sucesso global.

Este registro posterior altera documentação, não código de produção/teste. A PR continua em rascunho e sem merge; resultados do head final ficam na própria PR. Aprovação técnica da empresa e aceite completo do desafio permanecem pendentes.
