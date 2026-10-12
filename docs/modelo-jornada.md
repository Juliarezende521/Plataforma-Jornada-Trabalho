# Modelo inicial de jornada — domínio online

Modelo iniciado em 11/10/2026 e validado em CI em 12/10/2026. Status: compilação/empacotamento backend e 24 testes aprovados (12 existentes + 12 novos de domínio); build frontend e seus 14 testes aprovados. Não há endpoints, mapeamento JPA ou migrations de jornada. O produto continua oferecendo somente autenticação integrada; journeyAvailable permanece false. Branch validate/journey-domain, PR #1 em rascunho, sem integração automática à main.

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

O workflow de regressão SQL Server/autenticação 38199350023 foi também iniciado; seu resultado ainda não foi confirmado neste registro. Ele cobre schema/login existentes, não persistência de jornadas. Não integrar automaticamente à main. Próximo passo: revisão final do modelo e decisão de integração; depois implementar/testar migrations, serviços com Clock/identidade, autorização por recurso, concorrência, recibos e regularização mínima. Somente então expor API/interface de jornada e validar com SQL Server real.
