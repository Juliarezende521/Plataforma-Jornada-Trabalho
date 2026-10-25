# API da aplicação

A API usa sessão no servidor. Mutações exigem o token obtido em `GET /api/auth/csrf`; nenhuma rota aceita o ID do usuário como prova de identidade.

## Autenticação

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/auth/csrf` | Retorna nome do header e token CSRF |
| POST | `/api/auth/login` | Recebe `email` e `password` como formulário e cria a sessão |
| POST | `/api/auth/logout` | Invalida a sessão atual |
| GET | `/api/me` | Retorna a identidade autenticada |

Login e logout bem-sucedidos retornam `204`. Credenciais inválidas retornam `401` sem indicar se o e-mail existe.

## Jornada do colaborador

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/me/work-sessions/current` | Jornada aberta, estado e ações disponíveis |
| POST | `/api/me/work-events` | Registra uma transição de jornada |
| GET | `/api/me/work-event-requests/{key}` | Consulta o recibo de uma tentativa idempotente |
| GET | `/api/me/work-sessions` | Histórico próprio paginado |
| GET | `/api/me/work-sessions/summary` | Resumo efetivo do período |
| GET | `/api/me/work-schedule?on=2026-11-01` | Escala própria vigente na data |
| GET | `/api/me/work-sessions/{id}` | Detalhe de uma jornada própria |
| POST | `/api/me/work-adjustments` | Solicita inclusão auditável de uma marcação esquecida |
| GET | `/api/me/work-adjustments?workSessionId={id}` | Lista as solicitações próprias da jornada |

### Registrar uma marcação

Header obrigatório:

```http
Idempotency-Key: 0f0f61bc-7c3d-42b8-b729-70015639cfff
```

Exemplo de entrada:

```json
{
  "type": "ENTRY",
  "expectedWorkSessionId": null,
  "timeZoneId": "America/Sao_Paulo",
  "workMode": "REMOTE"
}
```

Para pausa, retorno e saída, `expectedWorkSessionId` deve identificar a jornada aberta confirmada pelo servidor. Tipos aceitos: `ENTRY`, `PAUSE_START`, `PAUSE_END` e `EXIT`. Modalidades aceitas: `OFFICE`, `REMOTE` e `TRAVEL`.

Uma operação nova retorna `201`. Repetir a mesma chave e o mesmo conteúdo devolve o resultado original. Reutilizar a chave com outro conteúdo retorna conflito.

### Consultar histórico

```http
GET /api/me/work-sessions?from=2026-10-01&to=2026-10-31&page=0&size=20
```

- `from` e `to` são datas inclusivas no formato ISO.
- O período máximo é de 366 dias.
- `page` começa em zero.
- `size` aceita valores de 1 a 100.
- Uma jornada inexistente ou pertencente a outra pessoa não fica visível pela rota de detalhe.
- O detalhe contém `events` com os registros originais e `effective` com a linha do tempo projetada e a duração recalculada.

O resumo recebe somente `from` e `to`, com o mesmo limite de 366 dias. Ele soma a duração efetiva, incluindo ajustes aprovados, e informa quantas jornadas ainda estão abertas. Totais abertos usam o instante da consulta e são provisórios.

### Solicitar uma marcação esquecida

```json
{
  "workSessionId": "5fd81cc7-bc67-4f71-8555-2e41331195ed",
  "requestedEventType": "PAUSE_START",
  "proposedOccurredAtUtc": "2026-10-20T15:00:00Z",
  "reason": "A pausa não foi registrada por falta de conexão."
}
```

O horário não pode estar no futuro, a justificativa aceita de 10 a 500 caracteres e o corpo é limitado a 4 KiB. A solicitação não modifica a linha do tempo original.

## Consulta e decisão do RH

Todas as rotas abaixo exigem a permissão persistida de leitura do RH:

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/hr/employees` | Diretório paginado de colaboradores ativos |
| GET | `/api/hr/employees/overview` | Estado mais recente de cada colaborador da página |
| GET | `/api/hr/employees/{employeeId}/work-sessions` | Histórico individual |
| GET | `/api/hr/employees/{employeeId}/work-sessions/summary` | Resumo efetivo individual |
| GET | `/api/hr/employees/{employeeId}/work-schedule?on=2026-11-01` | Escala vigente do colaborador |
| POST | `/api/hr/employees/{employeeId}/work-schedules` | Cria nova versão auditável da escala |
| GET | `/api/hr/employees/{employeeId}/work-schedules` | Lista versões da escala com paginação |
| GET | `/api/hr/employees/{employeeId}/work-schedule-exceptions` | Lista exceções de escala auditáveis |
| POST | `/api/hr/employees/{employeeId}/work-schedule-exceptions` | Cria nova versão de exceção para uma data |
| GET | `/api/hr/audit-entries` | Lista a trilha de auditoria com paginação, restrita ao RH |
| GET | `/api/hr/employees/{employeeId}/employment-contract` | Consulta o contrato vigente na data atual ou em `on` |
| GET | `/api/hr/employees/{employeeId}/employment-contracts` | Lista versões do contrato com paginação |
| POST | `/api/hr/employees/{employeeId}/employment-contracts` | Cria uma versão imutável e auditável do contrato |
| GET | `/api/hr/employees/{employeeId}/monthly-closures/{YYYY-MM}` | Consulta o fechamento de uma competência |
| GET | `/api/hr/employees/{employeeId}/monthly-closures` | Lista fechamentos com paginação |
| POST | `/api/hr/employees/{employeeId}/monthly-closures/{YYYY-MM}` | Fecha e audita uma competência encerrada |
| GET | `/api/hr/employees/{employeeId}/manager-assignment?on=2026-11-01` | Consulta o gestor vigente na data |
| GET | `/api/hr/employees/{employeeId}/manager-assignments` | Lista o histórico versionado de gestores |
| POST | `/api/hr/employees/{employeeId}/manager-assignments` | Cria um vínculo de gestor com vigência e auditoria |
| GET | `/api/hr/employees/{employeeId}/work-sessions/{id}` | Detalhe individual |
| GET | `/api/hr/work-adjustments` | Fila paginada de solicitações pendentes |
| POST | `/api/hr/work-adjustments/{id}/decision` | Aprova ou rejeita uma solicitação |

As consultas de jornada são somente de leitura. A decisão de ajuste é terminal, exige CSRF e gera uma entrada de auditoria. Antes da aprovação, o servidor considera também ajustes já aprovados e verifica a sequência inteira. Uma pausa, retorno ou saída esquecida pode ser materializada como evento corretivo quando muda o estado de uma jornada aberta; decisão, evento, checkpoint e auditoria pertencem à mesma transação. A permissão do RH não autoriza marcações online em nome de outra pessoa.

Os resumos retornam `totalWorkDuration`, `plannedWorkDuration`, `scheduledDays`, `periodDays`, `balanceSeconds`, `toleranceSeconds` e `withinTolerance`. O saldo e a classificação só são calculados quando uma escala vigente cobre todas as datas do período; caso contrário, os campos derivados são `null`. A tolerância é somada apenas nos dias com carga planejada. Esses valores são comparativos operacionais e não representam cálculo legal de horas extras ou banco de horas.

O fechamento mensal grava uma fotografia imutável do resumo. Ele exige mês já encerrado no fuso do colaborador, cobertura integral da escala, ausência de jornadas abertas e de ajustes pendentes. Depois do fechamento, solicitações de ajuste e alterações retroativas de escala que afetariam a competência são recusadas. A operação exige CSRF, permissão atual do RH e gera auditoria.

O vínculo de gestor recebe `managerEmployeeId` e `effectiveFrom`. Uma nova vigência não altera versões anteriores. Autoatribuição, versão duplicada e ciclos na hierarquia são rejeitados.

## Consulta do gestor

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/manager/team` | Lista os subordinados diretos vigentes, com paginação |
| GET | `/api/manager/employees/{employeeId}/work-sessions` | Histórico de um subordinado direto |
| GET | `/api/manager/employees/{employeeId}/work-sessions/summary` | Resumo do período do subordinado |
| GET | `/api/manager/employees/{employeeId}/work-sessions/{id}` | Detalhe de uma jornada do subordinado |

O papel é concedido quando a conta possui ao menos um vínculo vigente. A API não confia apenas nesse papel: cada consulta verifica novamente se o colaborador solicitado pertence diretamente à equipe atual. O acesso do gestor é somente de leitura e não inclui contrato, escala, fechamento ou decisões do RH.

## Erros

Os erros são devolvidos como Problem Details sem stack trace ou mensagem de banco:

| Status | Uso |
|---|---|
| 400 | Parâmetro, UUID ou corpo inválido |
| 401 | Sessão ausente, expirada ou credencial inválida |
| 403 | CSRF ou permissão insuficiente |
| 404 | Recurso inexistente ou não visível para a conta |
| 409 | Transição inválida, alvo alterado ou conflito idempotente |
| 429 | Limite de tentativas de login |
| 503 | Falha transitória de banco |

Instantes são retornados em ISO 8601 UTC. Entidades JPA, hashes de senha e detalhes internos não fazem parte dos contratos públicos.
