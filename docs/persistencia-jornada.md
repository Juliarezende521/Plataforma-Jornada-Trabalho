# Persistência de jornada — schema SQL Server

Fatia iniciada em 13/10/2026, após a segunda análise de segurança/desempenho/legibilidade. Status inicial: migration e testes escritos; execução integrada pendente. Esta etapa entrega o schema, não o mapeamento JPA, serviço transacional, API ou interface de jornada. `journeyAvailable` continua false.

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

Campos `opened_at_utc` e `last_event_at_utc` permitem conferir limites temporais da linha de jornada; `closed_at_utc >= last_event_at_utc` admite a futura saída efetiva regularizada sem inserir evento online artificial. A revisão manual permanece pendente. Duração não é duplicada em uma coluna nesta fatia; cálculo/reconstituição continuam no domínio e exigirão integração própria.

## Verificações

CI usa um SQL Server descartável: aplica primeiro V1 (`spring.flyway.target=1`), executa os testes existentes e depois migra para V2 e executa `SqlServerIT,JourneySchemaSqlServerIT`. Isso verifica evolução a partir de V1 e aplicação ordenada em banco inicialmente vazio. Não altera migrations aplicadas nem simula SQL Server com H2.

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

## Próxima fatia

Mapeamento/reconstituição do agregado, incremento efetivo de versão, serviço com identidade/Clock confiáveis e transações/recibos idempotentes; depois API e frontend. Regularização P0 continua separada e pendente. Responsividade/IHC não foram validadas por testes de banco. Não há aceite global do desafio nem aprovação do técnico da empresa nesta etapa.
