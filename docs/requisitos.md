# Requisitos e recorte da primeira versão

Este documento registra as decisões tomadas para preencher os pontos que o desafio deixou em aberto. Elas são hipóteses de produto, não regras trabalhistas da empresa.

## Atores

### Colaborador

- Autentica-se com uma conta previamente provisionada.
- Registra e consulta somente a própria jornada.
- Não escolhe a identidade nem o instante de uma marcação.

### RH

- Consulta o estado atual dos colaboradores ativos.
- Abre o histórico e o detalhe de uma pessoa específica.
- Não cria nem altera marcações de outra pessoa.

### Gestor

- Consulta a lista de colaboradores diretamente vinculados a ele na data atual.
- Consulta histórico, detalhe e resumo apenas dessas pessoas.
- Analisa solicitações de ajuste pendentes apenas dessas pessoas.
- Não registra marcações nem altera contratos, escalas ou fechamentos da equipe.

O cadastro público e a administração de contas não fazem parte desta versão.

## Funcionalidades implementadas

| Área | Comportamento |
|---|---|
| Autenticação | Login e logout por sessão, com CSRF e mensagem genérica para credenciais inválidas |
| Entrada | Abre uma jornada para o colaborador autenticado |
| Pausa | Permite iniciar e encerrar múltiplas pausas |
| Saída | Encerra a jornada a partir dos estados trabalhando ou em pausa |
| Jornada atual | Mostra estado, última marcação, duração provisória e ações válidas |
| Histórico | Filtra jornadas por período e apresenta resultados paginados |
| Resumo | Consolida duração efetiva, dias, jornadas abertas, encerradas e ajustadas no período |
| Detalhe | Lista as marcações originais na ordem em que aconteceram |
| Fusos | Permite visualizar instantes em São Paulo, Lisboa ou UTC sem alterar o registro |
| Modalidade | Guarda escritório, remoto ou viagem em cada marcação |
| RH | Apresenta visão geral e consulta individual com permissão específica |
| Busca do RH | Filtra colaboradores ativos pelo início do nome, mantendo paginação e ordenação estáveis |
| Recuperação | Reconcilia uma tentativa após timeout ou recarregamento sem duplicar o evento |
| Ajustes | Colaborador solicita uma marcação esquecida; RH ou gestor direto aprova ou rejeita com trilha de auditoria |
| Linha efetiva | Detalhe combina originais e ajustes aprovados e recalcula a duração sem alterar a origem |
| Escala | RH configura minutos planejados por dia da semana e início de vigência para cada colaborador |
| Comparativo | Resumo confronta tempo efetivo e planejado quando a escala cobre todo o período consultado |
| Tolerância | RH define por vigência uma tolerância operacional diária, aplicada apenas em dias planejados |
| Exceções de escala | RH substitui carga e tolerância em uma data para feriado, folga, treinamento ou jornada especial |
| Auditoria | RH consulta em ordem cronológica as ações sensíveis, seus responsáveis e recursos afetados |
| Contrato | RH registra versões de país, tipo de vínculo e carga semanal por colaborador |
| Equipes | RH registra vínculos de gestor com vigência; gestores consultam somente jornadas de subordinados diretos |
| Relatório | RH exporta o resumo individual do período em CSV, usando os mesmos totais exibidos na consulta |
| Relatório consolidado | RH exporta em CSV os fechamentos concluídos de uma competência, sem recalcular resultados históricos |
| Políticas por país | RH registra parâmetros operacionais versionados e auditáveis, sem apresentá-los como interpretação jurídica |

## Regras de jornada

- Uma jornada começa com `ENTRY` e termina com `EXIT`.
- `PAUSE_START` só é aceito enquanto a pessoa está trabalhando.
- `PAUSE_END` só é aceito durante uma pausa.
- Uma saída durante a pausa encerra a jornada sem contar a pausa final como trabalho.
- Uma pessoa pode ter várias jornadas no mesmo dia, mas apenas uma pode estar aberta.
- Uma jornada pode atravessar a meia-noite e continua vinculada à data de entrada.
- A duração exclui todos os intervalos de pausa.
- Jornada aberta tem duração provisória e não deve ser tratada como total fechado.
- Eventos já confirmados não são editados ou apagados.
- Solicitações de ajuste não reescrevem os eventos originais.
- Apenas o dono da jornada solicita um ajuste; RH ou gestor direto vigente decide.
- Uma decisão é terminal; novas alterações exigem uma nova solicitação.
- Quando uma correção aprovada muda o estado atual de uma jornada aberta, ela é materializada como novo evento corretivo na mesma transação.
- Pausa, retorno ou saída corretivos só podem ocorrer a partir do último evento persistido e precisam manter uma sequência válida.

## Tempo, viagens e localização

- O servidor define o instante da marcação e o persiste em UTC.
- O cadastro possui um fuso IANA de referência, usado para determinar a data da jornada.
- O navegador informa o fuso IANA observado em cada marcação.
- Alterar o fuso de visualização não muda o instante, a duração ou a data de referência.
- A modalidade e o fuso são informações declaradas; não comprovam localização física.
- GPS e geolocalização não fazem parte do escopo.

## Segurança e integridade

- Identidade, permissões e autoria são obtidas da sessão no servidor.
- Recursos próprios são filtrados também pelo colaborador autenticado.
- O papel de gestor é derivado dos vínculos vigentes e cada consulta revalida o vínculo no servidor.
- Trocas de gestor criam versões imutáveis, impedem autoatribuição e ciclos e geram auditoria.
- Senhas são armazenadas com hash adaptativo.
- Mutações exigem token CSRF.
- Chaves de idempotência impedem repetição da mesma intenção.
- Uma chave reutilizada com outro conteúdo é rejeitada.
- Locks, versionamento e constraints do SQL Server protegem transições concorrentes.
- Falha na transação não pode deixar evento, estado e recibo parcialmente gravados.
- Consultas são limitadas a 366 dias e 100 itens por página.
- Exportações CSV neutralizam células interpretáveis como fórmulas e não colocam dados pessoais no nome do arquivo.

## Requisitos de experiência

- Os fluxos principais devem funcionar em celular, tablet e desktop.
- Campos têm labels, foco visível e mensagens textuais de erro ou sucesso.
- O estado confirmado pelo servidor prevalece sobre o estado visual do navegador.
- Uma falha de rede não deve ser apresentada como marcação confirmada.
- A interface não promete funcionamento offline.

## Fora desta versão

- Cálculo legal de horas extras e banco de horas; a diferença e a tolerância operacional são apenas informativas e ainda não aplicam regras legais por país.
- Integração com folha de pagamento.
- Cadastro, recuperação de senha e gestão de usuários.
- Single sign-on corporativo.
- Regras legais específicas do Brasil ou de países europeus.

## Critérios usados para considerar o MVP pronto

- Ambiente reproduzível a partir de um clone limpo.
- Migrations aplicadas em SQL Server real.
- Autorização cruzada testada com pelo menos duas contas.
- Transições, pausas, madrugada e fusos cobertos por testes.
- Build e testes de backend e frontend aprovados.
- Fluxos de contrato, exceção de escala e auditoria validados em navegador com SQL Server real.
- Limitações visíveis no README, sem anunciar prontidão de produção.
