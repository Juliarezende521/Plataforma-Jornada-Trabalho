# Plano de validação, riscos e evidências

Status: todos os cenários pendentes. Não há testes executados ou resultados aprovados nesta etapa.

## Estratégia de testes

- Domínio: relógio fixo, sequências, cálculo e regras de fuso sem dependência da hora atual.
- API/segurança: sessão real de teste, DTOs, status HTTP, CSRF e isolamento entre usuários.
- Integração: SQL Server real para migrations, tipo temporal, constraints e concorrência; não declarar H2 equivalente.
- Interface: fluxo completo, responsividade, teclado, estados vazios, erros e timeout.
- Execução: clone limpo e banco vazio, usando exclusivamente os passos publicados.

## Casos de teste rastreáveis

| ID | Requisito | Cenário | Resultado esperado |
|---|---|---|---|
| T01 | RF01/RF02 | Login válido, inválido e logout | Sessão correta; erro genérico; sessão antiga rejeitada após logout |
| T02 | RNF03/RNF04 | Sem sessão; sem CSRF; token inválido | 401 sem sessão; mutações autenticadas sem CSRF válido rejeitadas |
| T03 | RF03–RF06 | 09h entrada, 12h pausa, 13h retorno, 18h saída | Quatro eventos e duração de 8h |
| T04 | RF12 | Duas pausas | Ambas descontadas |
| T05 | RF03–RF06 | Retorno sem pausa, duas entradas ou saída em pausa | 409, sem evento ou alteração parcial |
| T06 | RF12/RNF07 | Entrada 22h e saída 06h seguinte | 8h sem pausas; data de referência do início |
| T07 | RF11/RNF07 | Mesmos instantes consultados em Brasil e Amsterdam | Horários diferentes; duração e data de referência idênticas |
| T08 | RNF07 | Europe/Amsterdam, 29/03/2026 00:30Z até 02:30Z | 2h reais, apesar do salto no horário local |
| T09 | RNF07 | Europe/Amsterdam, 25/10/2026 00:30Z até 02:30Z | 2h reais, apesar da repetição de horários locais |
| T10 | RF10 | Jornada entre último e primeiro dia de meses distintos | Total atribuído ao mês da data de início |
| T11 | RF13 | Jornada sem saída | Pendência separada do consolidado; nova entrada rejeitada |
| T12 | RF14 | Repetição após resposta perdida | Mesma chave devolve mesmo resultado; um único evento |
| T13 | RF14 | Chave reutilizada com outro tipo | 409 e preservação do resultado original |
| T14 | RNF06 | Duas entradas simultâneas com chaves diferentes | Só uma jornada aberta; perdedora recebe conflito |
| T15 | RNF06 | Duas transições incompatíveis simultâneas | Uma confirmada; outra reavaliada/rejeitada; estado e eventos coerentes |
| T16 | RNF03 | Usuário A solicita detalhes de B ou injeta EmployeeId | Não acessa/gera registros de B |
| T17 | RNF02/RNF05 | Falha transacional e reinício | Sem gravação parcial; registros confirmados continuam persistidos |
| T18 | RNF09 | SQL Server vazio | Flyway cria schema; Hibernate validate passa; seed não duplica |
| T19 | RNF07 | Round-trip JDBC de instante e offset | Instante normalizado e precisão preservada conforme decisão |
| T20 | RNF01/RNF12 | Tela 360 px, desktop e teclado | Fluxos utilizáveis, foco visível e mensagens textuais |
| T21 | RNF08/RNF13 | Tipo desconhecido, data inválida e paginação excessiva | 400 sem stack trace, com erro explicativo |
| T22 | RF07/RF14 | Timeout após commit e nova tentativa | Interface reconcilia estado com mesma chave; não duplica |
| T23 | RNF04 | Login/logout e emissão do CSRF | Fluxo continua válido após renovação; sessão/token obsoletos não autorizam mutação |
| T24 | RNF07 | Relógio retorna instante anterior ao último evento | Erro controlado sem duração negativa |
| T25 | RF15/RF16 P1 | Gestor consulta equipe externa; RH consulta autorizado | Escopo de acesso correto, se implementado |
| T26 | RF17/RF18 P1 | Correção aprovada, rejeitada e autoaprovação | Original preservado; revisão efetiva correta; autoaprovação rejeitada |

Datas de horário de verão são exemplos determinísticos a validar com ZoneId e a base de fusos da versão de Java utilizada; não dependem de esperar a data ocorrer.

Para concorrência, usar barreira de sincronização ou disparo coordenado em múltiplas transações. Testar o estado final do banco, não apenas status HTTP. Registrar IDs e contagens esperadas sem dados pessoais reais.

## Roteiro do avaliador

1. Executar passos do README em ambiente suportado e SQL Server vazio.
2. Entrar com colaborador A fictício; conferir fuso e estado inicial.
3. Registrar entrada, pausa, retorno e saída; consultar detalhe e cálculo.
4. Consultar período e mudar fuso de visualização, conferindo invariantes.
5. Entrar com colaborador B; verificar que não vê registros de A.
6. Executar suíte documentada, incluindo migrations, CSRF, idempotência e concorrência.
7. Reiniciar serviços preservando volume; conferir dados e autenticar novamente se necessário.
8. Ler limitações e conferir recursos P1 somente quando declarados implementados.

## Registro de evidências

| Evidência | Conteúdo necessário | Estado atual |
|---|---|---|
| Execução limpa | Commit, ambiente, versões, comandos exatos, logs sanitizados e resultado | Pendente |
| Testes automatizados | Comando, commit, testes/falhas e relatório gerado | Pendente |
| Testes manuais | T20/T22 e demais fluxos, ambiente e resultado observado | Pendente |
| Banco | Histórico Flyway, constraints e contagens relevantes | Pendente |
| Segurança | Resultado de acesso cruzado, CSRF, logout e inspeção de segredos | Pendente |
| Interface | Capturas fictícias e descrição do fluxo | Pendente |

Não publicar cookies, tokens, strings de conexão com senha ou dumps reais. Evidência de uma execução local não comprova prontidão de produção.

## Registro de riscos

Classificação qualitativa proposta, sem estimativas estatísticas.

| ID | Risco | Probabilidade/impacto | Mitigação | Gatilho e resposta |
|---|---|---|---|---|
| R01 | Ambiente ARM ou recursos insuficientes para SQL Server | Desconhecida/alto | Verificar máquina antes da implementação; conexão externa configurável | Banco não inicia: usar host suportado e documentar caminho |
| R02 | Prazo curto e escopo excessivo | Desconhecida/alto | Núcleo P0 primeiro; P1 condicionado | Núcleo incompleto: suspender complementos |
| R03 | Configuração incorreta de Spring Security/Angular | Média/alto | Testar sessão, CSRF e logout cedo | Login aparente sem sessão: corrigir fluxo antes de implementar telas restantes |
| R04 | Inconsistência temporal ou de precisão JDBC | Média/alto | Testes UTC, DST e round-trip SQL Server | Divergência: ajustar mapeamento antes de publicar totais |
| R05 | Duas marcações concorrentes passam | Média/alto | @Version, transação e constraints reais | Estado final inválido: bloquear aceite do núcleo |
| R06 | Backend Java menos alinhado à empresa | Certa/moderado | ADR transparente e execução clara | Questionamento técnico: explicar trade-off, sem alegar superioridade universal |
| R07 | Saída esquecida bloqueia novas jornadas | Média/moderado | Pendência explícita e correção P1 priorizada | Sem prazo para corrigir: declarar limitação, sem inventar saída |
| R08 | Credenciais fictícias ativadas fora da avaliação | Baixa/alto | Perfil exclusivo e verificação de inicialização | Seed ativo em produção: impedir inicialização insegura |
| R09 | Dependências incompatíveis ou migration falha | Média/alto | Versões fixadas e teste em banco vazio | Falha de criação: corrigir migration ainda não publicada e repetir validação |
| R10 | Documentação diverge do código | Média/moderado | Conferência por pacote e execução do README | Comando/fluxo diverge: atualizar antes da entrega |

## Checklist de encerramento

Todos os requisitos explícitos funcionais; testes críticos aprovados; regras assumidas documentadas; API e interface coerentes; README executado desde zero; licença de desenvolvimento e arquitetura do banco esclarecidas; sem segredos; repositório público preparado. Enviar o link ao endereço do enunciado somente após concluir a entrega e por ação autorizada naquela etapa.
