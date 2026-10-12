# Plataforma de Jornada de Trabalho — documentação inicial

Status: proposta de solução de negócio. A base técnica implementada e seus testes estão descritos em [base-tecnica.md](base-tecnica.md); funcionalidades de jornada e critérios abaixo continuam planejados.

## 1. Interpretação do problema

A Dungeons & Dragons Group precisa centralizar as marcações de jornada e permitir sua consulta, substituindo planilhas e comunicações dispersas. A distribuição entre Brasil e Europa, o trabalho remoto e as viagens exigem tratamento explícito de fusos, consistência das marcações e autorização de acesso.

Esta primeira versão prioriza registros confiáveis, cálculo explicável e execução reproduzível. Não presume regras trabalhistas iguais entre países nem conformidade com sistemas oficiais de registro de ponto.

## 2. Rastreabilidade do enunciado

|Origem   |Necessidade               |Solução proposta                                            |Evidência de aceite                             |
|---------|--------------------------|------------------------------------------------------------|------------------------------------------------|
|Explícita|Interface web             |Angular com layout responsivo                               |Fluxo completo em desktop e celular             |
|Explícita|Persistência              |SQL Server, Spring Data JPA e migrations Flyway versionadas |Dados preservados após reiniciar a aplicação    |
|Explícita|Autenticação              |Spring Security e sessão no servidor identificada por cookie|Rotas protegidas; login e logout funcionais     |
|Explícita|Eventos de jornada        |Entrada, início de pausa, retorno e saída                   |Eventos persistidos e sequência validada        |
|Explícita|Consulta                  |Histórico por período e detalhes da jornada                 |Usuário visualiza registros e totais permitidos |
|Contexto |Operação internacional    |Instantes em UTC e fusos identificados                      |Testes Brasil/Europa, viagens e horário de verão|
|Contexto |Divergências de fechamento|Totais reproduzíveis e jornadas incompletas sinalizadas     |Consulta mensal sem inventar horas ausentes     |
|Entrega  |Repositório público       |Código, README, decisões e testes                           |Avaliador executa a partir de um clone limpo    |

## 3. Escopo e prioridades

### Núcleo obrigatório da primeira versão

- Login e logout; contas provisionadas, sem cadastro público.
- Registro online de entrada, início de pausa, retorno da pausa e saída.
- Uma jornada aberta por colaborador, com múltiplas pausas e possibilidade de atravessar a meia-noite.
- Histórico próprio por período, detalhe dos eventos e total de duração trabalhada.
- Tratamento de fusos, autorização no servidor, idempotência e concorrência.
- Interface responsiva com estados de carregamento, sucesso e erro.
- Migrations, dados fictícios para avaliação e instruções de execução.
- Regularização mínima de saída esquecida, com solicitação, decisão autorizada e revisão auditada (RF17/RF18, recorte P0 aprovado em 11/10/2026).

### Complemento priorizado após estabilizar o núcleo

- Consulta por gestor apenas dos colaboradores vinculados à sua equipe e consulta por RH de todos os colaboradores.
- Correções de outros tipos de marcação, além da saída esquecida, preservando originais e auditoria.

A saída esquecida permanece pendente até aprovação da regularização mínima ou saída online legítima; não há saída automática, edição direta no banco ou garantia de atendimento imediato. O recorte, permissões e regras são definidos em [requisitos.md](requisitos.md#regularização-mínima-de-saída-esquecida--decisão-p0).

### Fora do escopo inicial

Folha de pagamento, cálculo legal de horas extras, banco de horas, férias, escalas, geolocalização, biometria, integração com dispositivos, operação offline, notificações e fechamento contábil definitivo. Consulta mensal não equivale a aprovação ou fechamento de folha.

## 4. Decisões de negócio propostas

Estas regras são hipóteses documentadas, pois não constam integralmente no enunciado.

|ID  |Decisão                                                                   |Justificativa                                                                                       |
|----|--------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
|RN01|O servidor determina o instante das marcações online                      |Alterar o relógio do dispositivo não altera a marcação                                              |
|RN02|O colaborador registra somente a própria jornada                          |A identidade vem da sessão; o cliente não escolhe o autor                                           |
|RN03|Existe no máximo uma jornada aberta por colaborador                       |Evita sobreposição e totais duplicados                                                              |
|RN04|São permitidas múltiplas pausas; todas são descontadas neste MVP          |Simplifica o cálculo, sem presumir regras locais de pausas remuneradas                              |
|RN05|Saída pode encerrar jornada trabalhando ou em pausa; neste último caso, delimita a pausa final sem gerar retorno|Evita marcação artificial e mantém o cálculo por intervalos de trabalho|
|RN06|A jornada pode atravessar a meia-noite                                    |Viagens e diferentes rotinas tornam o limite de dia civil inadequado                                |
|RN07|O fuso de referência do colaborador é congelado ao abrir a jornada        |Trocar de país ou fuso de visualização não redistribui o histórico                                  |
|RN08|A jornada pertence à data de início nesse fuso de referência              |Estabelece um critério estável de consulta e agrupamento mensal                                     |
|RN09|Uma jornada incompleta não entra no total consolidado de jornadas fechadas|Evita apresentar duração definitiva sem saída                                                       |
|RN10|Não são presumidas oito horas diárias ou tolerância de atraso             |Contratos e regras por país não foram informados                                                    |
|RN11|O mês agrupa jornadas pela data de referência de início                   |Uma jornada que cruza o mês é atribuída integralmente ao mês inicial; trata-se de resumo operacional|
|RN12|Falha de conexão não produz marcação local automática                     |Uma tentativa offline não pode ser apresentada como registro confirmado                             |

## 5. Estados e cálculo

Transições permitidas:

|Estado atual      |Evento         |Próximo estado|
|------------------|---------------|--------------|
|Sem jornada aberta|Entrada        |Trabalhando   |
|Trabalhando       |Início de pausa|Em pausa      |
|Em pausa          |Retorno        |Trabalhando   |
|Trabalhando       |Saída          |Encerrada     |
|Em pausa          |Saída          |Encerrada     |

Qualquer outra transição é rejeitada pelo backend. Desabilitar um botão no frontend melhora a experiência, mas não substitui essa validação.

Duração trabalhada de uma jornada encerrada = soma dos intervalos entre entrada/retorno e início de pausa/saída, calculados a partir dos instantes UTC. Armazenar e calcular com precisão definida; arredondar somente na apresentação, sem arredondar cada intervalo antes da soma.

Exemplo: 09:00 entrada, 12:00 pausa, 13:00 retorno e 18:00 saída, no mesmo fuso e sem mudança de offset: 8 horas trabalhadas. Uma jornada em andamento pode mostrar duração provisória até o instante atual do servidor, claramente identificada como provisória.

### Revisão RN05 — saída durante pausa (11/10/2026)

Decisão assumida para o desafio, ainda sem validação de RH e sem implementação: permitir saída diretamente do estado Em pausa. A saída delimita o fim da pausa final e encerra a jornada na mesma transação; persistir apenas o evento de saída solicitado, sem sintetizar retorno ou novo início de pausa. O intervalo entre o início dessa pausa e a saída não integra a duração trabalhada, conforme RN04. O backend determina o instante e valida identidade, estado, ordem temporal, concorrência e idempotência como nas demais transições.

Exemplo determinístico: entrada 09:00, pausa 12:00 e saída 12:30, no mesmo fuso e sem mudança de offset: 3 eventos, 3h trabalhadas, 30min de pausa e jornada encerrada. Uma saída repetida com a mesma chave retorna o resultado original; uma nova saída com outra chave após encerramento é rejeitada, sem evento adicional. Não presumir regra remuneratória ou obrigação trabalhista para Brasil/Europa. Essa revisão substitui a restrição anterior de retorno obrigatório; aprovação do comportamento depende dos testes e da interface futura.

## 6. Estratégia temporal

- Persistir `OccurredAtUtc` como instante e `RecordedAtUtc` como momento da gravação. Nas marcações online ambos são determinados pelo servidor.
- Persistir `ReferenceTimeZoneId` e `WorkDate` na jornada. Usar identificadores IANA, por exemplo `America/Sao_Paulo` e `Europe/Amsterdam`, validados no ambiente escolhido.
- O fuso do dispositivo é apenas uma sugestão de visualização. Não deve alterar silenciosamente o fuso de referência do colaborador.
- Exibir o fuso usado nos horários e permitir consultar a jornada no fuso de referência ou no fuso de visualização selecionado.
- Guardar o offset aplicado na referência quando necessário para explicar o registro histórico. Offset isolado não substitui um identificador de fuso.
- Subtrair instantes UTC para calcular durações; não subtrair textos de horários locais. A duração real continua correta quando o horário de verão muda.
- Alterações futuras no fuso de referência valem para novas jornadas; não recalcular a data de jornadas antigas.
- Em eventual correção manual, um horário local inexistente deve ser rejeitado. Se for ambíguo, exigir seleção explícita do offset/instante correspondente.
- Usar um relógio injetável nos testes e manter o relógio do servidor sincronizado na operação.

No Java, usar `Instant` para os instantes, `LocalDate` para a data de referência, `ZoneId` para o fuso e `Duration` para duração. Injetar `Clock` para testes determinísticos. No SQL Server, persistir instantes em `datetimeoffset(7)` normalizados para offset `+00:00`, data de referência em `date` e identificador IANA em coluna separada. `datetimeoffset` guarda o offset, mas não o identificador IANA nem regras históricas. Manter `Instant` no domínio e validar a conversão JDBC/Hibernate, usando `OffsetDateTime` UTC na fronteira de persistência se necessário. Fixar precisão de 100 nanossegundos compatível com a coluna e comparar instantes normalizados. Usar JSON ISO 8601 com `Z`. As conversões de fuso são feitas pelo Java (`ZoneId`), sem depender de equivalência automática entre identificadores IANA e identificadores Windows do SQL Server.

Ordenar eventos pela sequência transacional da jornada, sem depender apenas de timestamps iguais ou do relógio de parede. Rejeitar um novo instante anterior ao último evento, com erro e registro operacional, em vez de produzir duração negativa. O relógio do servidor reduz manipulação pelo cliente, mas não elimina falhas de sincronização.

Exemplo de viagem: um colaborador vinculado a `America/Sao_Paulo` consulta sua jornada em `Europe/Amsterdam`. Os horários exibidos mudam; os instantes, a duração e a data de referência da jornada permanecem iguais.

## 7. Arquitetura e stack propostas

- Backend: Java com Spring Boot e Spring Web MVC.
- Frontend: Angular e TypeScript.
- Persistência: SQL Server com Spring Data JPA/Hibernate.
- Autenticação e autorização: Spring Security com sessão no servidor e cookie de sessão.
- Migrations: Flyway como fonte de evolução do schema.
- Testes: JUnit para regras de domínio, Spring Boot Test/MockMvc para a API e Testcontainers com SQL Server para persistência e concorrência; testes dos principais fluxos da interface.
- Execução: ambiente reproduzível com versões fixadas e, se compatível com o equipamento, Docker Compose.

### Decisão técnica: Java em um ecossistema predominantemente Microsoft

O enunciado descreve Microsoft no backend como direcionamento, sem torná-lo requisito obrigatório. A solução utilizará Java/Spring Boot para aproveitar a experiência da desenvolvedora e concentrar esforço nas regras de jornada, segurança e qualidade da implementação. Angular mantém aderência ao frontend informado. SQL Server foi escolhido pela familiaridade da desenvolvedora na faculdade e pela informação recebida de que a empresa o utiliza. Essa informação complementa o enunciado, que não especifica o banco.

Trade-off: o backend não utiliza a tecnologia predominante da empresa e exigiria conhecimento de Java para manutenção pela equipe. A decisão será comunicada explicitamente, com API HTTP documentada, ambiente reproduzível e testes que permitam avaliar a solução. Não se presume integração com sistemas Microsoft ou garantia de aprovação pelo uso de determinada stack.

Versões estáveis, suportadas e mutuamente compatíveis de Java, Spring Boot, Angular, Node e SQL Server serão verificadas e fixadas antes da implementação. Usar Maven Wrapper, lockfile do frontend e imagens de banco com versão definida; evitar dependências ou imagens com tag `latest`.

### Organização e persistência

Organizar o backend por funcionalidades (autenticação, colaboradores, jornadas), com controllers, DTOs, serviços e repositories de responsabilidades claras. Usar Bean Validation nos DTOs, serviços para regras e limites transacionais, Spring Data JPA para acesso parametrizado e tratamento centralizado de erros. Não expor entidades JPA diretamente na API nem aceitar campos internos por vinculação automática.

Usar Flyway para criar e evoluir o schema, incluindo índices e constraints. Configurar `spring.jpa.hibernate.ddl-auto=validate`; Hibernate valida o mapeamento, sem criar ou alterar tabelas. Não usar `update`, `create` ou `create-drop` no ambiente de avaliação. Não ativar baseline automático para ocultar um banco sem histórico de migrations. Separar seed fictício por perfil e comprovar criação desde banco vazio. Não editar migrations já aplicadas; adicionar novas versões.

Usar `@Transactional` na operação que grava evento, estado e resultado idempotente. Mapear `version` com `@Version` e traduzir conflitos de concorrência para resposta coerente. Índices filtrados devem estar no SQL do Flyway, não depender apenas de anotações JPA.

Adotar um monólito com separação simples entre API, regras de jornada e persistência. Controllers recebem DTOs e delegam a serviços de aplicação. Evitar microserviços, abstrações genéricas de repositório e infraestrutura que não resolvam uma necessidade concreta.

Servir frontend e API na mesma origem simplifica cookies e reduz configuração de CORS. No desenvolvimento, usar proxy do Angular para a API. Não há necessidade de JWT no armazenamento do navegador no para este cenário.

### Integração de segurança

Configurar `SecurityFilterChain` com endpoints públicos explícitos, autenticação para os demais e autorização por recurso nos serviços/consultas. Usar o fluxo de autenticação do Spring Security, `PasswordEncoder` com hash adaptativo e proteção contra fixação de sessão. Login deve estabelecer o contexto na sessão; logout deve invalidá-la. Não basta verificar a senha e retornar sucesso em um controller. A API deve responder 401/403 em formato JSON, sem redirecionar o Angular para páginas de login do servidor.

Manter proteção CSRF nas mutações. O cookie de sessão deve ser HttpOnly, Secure no ambiente HTTPS e SameSite definido. Se adotado o cookie de token CSRF para integração com Angular, ele precisa ser legível pelo cliente; é diferente do cookie de sessão. Integrar `XSRF-TOKEN`/`X-XSRF-TOKEN` segundo a configuração da versão escolhida, incluindo emissão/renovação após login e logout. SameSite e CORS não substituem CSRF. Sessão expirada e token inválido devem ter mensagens recuperáveis na interface. Sessões podem ser locais a uma instância no MVP; reiniciar o backend pode exigir novo login, sem perder os registros persistidos.

### Operação do SQL Server

Usar o driver JDBC oficial da Microsoft, SQL Server Developer somente para desenvolvimento/avaliação e dependências Flyway compatíveis com o SQL Server. Fixar a versão após validar driver, Hibernate, Flyway e imagem do banco. Conexão em ambiente publicado deve usar TLS com validação do certificado; não tornar `trustServerCertificate=true` um padrão de produção. O usuário da aplicação deve ter permissões limitadas; separar credenciais de migration, com DDL, das credenciais de execução, com DML. Não executar a aplicação com a conta `sa`.

As imagens oficiais de SQL Server Linux têm suporte em hosts Intel/AMD x86-64. Antes de prometer Docker Compose/Testcontainers em qualquer máquina, verificar a arquitetura e recursos disponíveis. Para ARM, oferecer conexão configurável a uma instância SQL Server em host suportado; não apresentar emulação como caminho oficialmente suportado. Registrar a alternativa e suas instruções no README.

A coluna numérica `version` de `@Version` é independente do tipo `rowversion` do SQL Server: este último não é data/hora e não será utilizado como timestamp de marcação. Um campo `closed_at_utc` nulo identifica a jornada aberta. Estado e encerramento devem permanecer coerentes por constraint e pela mesma transação.

## 8. Modelo inicial

|Entidade              |Campos principais                                                      |Responsabilidade                             |
|----------------------|-----------------------------------------------------------------------|---------------------------------------------|
|Usuário               |ID, email, hash de senha, estado da conta                              |Autenticação e perfis                        |
|Colaborador           |ID, UserId único, nome, ReferenceTimeZoneId, gestor opcional           |Identidade de negócio e escopo de consulta   |
|Jornada               |ID, EmployeeId, WorkDate, ReferenceTimeZoneId, estado, version         |Agrupar eventos e controlar concorrência     |
|Evento                |ID, WorkSessionId, tipo, OccurredAtUtc, RecordedAtUtc, sequência, autor|Registrar a marcação original                |
|Requisição idempotente|EmployeeId, chave, hash do payload, resultado                          |Evitar duplicação por repetição de requisição|
|Auditoria             |autor, ação, entidade, instante, correlação                            |Registrar ações relevantes sem expor segredos|

Índices/constraints propostos: UserId único; chave idempotente única por colaborador; sequência única por jornada; índice para consulta por colaborador e data; uma única jornada não encerrada por colaborador, por índice único filtrado sobre EmployeeId com predicado `closed_at_utc IS NULL`. Chaves estrangeiras e campos obrigatórios devem existir no banco.

O estado da jornada e seus eventos devem mudar na mesma transação. Usar concorrência otimista com um campo numérico `version` anotado com `@Version`, além de constraints no banco. A criação simultânea de duas jornadas também precisa ser protegida; versionamento de uma jornada ainda inexistente não resolve esse caso. Se houver conflito, a transação deve ser revertida integralmente; não continuar consultando/gravar na mesma transação marcada para rollback. Repetições devem reavaliar o estado em nova transação, preservando a chave idempotente.

No recorte P0 de regularização de saída esquecida, adicionar solicitação e revisão aprovada contendo justificativa, autor, aprovador e valores anteriores/novos. O cálculo passa a usar a saída efetiva sem apagar eventos originais nem simular marcação online. O próprio solicitante não aprova sua correção. Outras correções permanecem P1; regras canônicas em requisitos.md.

## 9. Contrato de API proposto

|Método e rota                            |Finalidade                                                |
|-----------------------------------------|----------------------------------------------------------|
|GET /api/auth/csrf                       |Obter/renovar token CSRF para a integração com a interface|
|POST /api/auth/login                     |Iniciar sessão                                            |
|POST /api/auth/logout                    |Encerrar sessão                                           |
|GET /api/me                              |Identidade e permissões atuais                            |
|GET /api/me/work-sessions/current        |Jornada aberta e ações permitidas                         |
|POST /api/me/work-events                 |Registrar evento com chave de idempotência                |
|GET /api/me/work-event-requests/{key}     |Consultar recibo próprio para recuperar tentativa incerta |
|GET /api/me/work-sessions?from=&to=&page=|Histórico próprio paginado                                |
|GET /api/me/work-sessions/{id}           |Eventos e cálculo de uma jornada própria                  |
|GET /api/team/work-sessions              |Consulta do gestor, se implementada                       |
|GET /api/hr/work-sessions                |Consulta do RH, se implementada                           |

Na marcação online, o payload contém tipo e expectedWorkSessionId (null na entrada; ID alvo nas outras transições), validado contra jornada própria aberta; não recebe timestamp arbitrário ou EmployeeId escolhido pelo cliente. Recuperação após falha/reload segue [requisitos.md](requisitos.md#recuperação-de-marcação-online--decisão-p0), com diário mínimo antes do envio e consulta autenticada do recibo. Documentar limites de período, paginação, formatos de data e semântica inclusiva/exclusiva dos filtros.

Usar Problem Details para erros, sem stack traces. Exemplos: 401 para sessão ausente; 403 para operação sem permissão; 404 para recurso não visível ou inexistente; 409 para transição incompatível, conflito de concorrência ou reutilização de chave com outro payload. Payload inválido recebe 400. Repetição válida com a mesma chave retorna o resultado original, sem criar outro evento.

## 10. Segurança e privacidade

|Risco                                |Medida proposta                                                                          |Como verificar                                   |
|-------------------------------------|-----------------------------------------------------------------------------------------|-------------------------------------------------|
|Ler ou marcar por outro colaborador  |Identidade da sessão e autorização por recurso no backend                                |Tentar acessar o ID de outro usuário             |
|Roubo de sessão por acesso de scripts|Cookie HttpOnly, Secure em HTTPS e expiração definida                                    |Inspecionar atributos e comportamento após logout|
|Requisição forjada com cookie        |Proteção antiforgery nos endpoints de mutação, incluindo autenticação conforme integração|Requisição sem token ou com token inválido falha |
|Tentativas repetidas de senha        |Limitação de requisições, bloqueio controlado e erro genérico                            |Testar sucessivas tentativas inválidas           |
|Injeção SQL                          |Consultas parametrizadas pelo JPA/Hibernate, sem concatenar SQL                          |Revisão e teste de entradas maliciosas           |
|XSS em dados exibidos                |Binding seguro do Angular; não renderizar HTML de usuário                                |Nome/justificativa maliciosos aparecem como texto|
|Duplo clique ou duas abas            |Idempotência, transação e constraints                                                    |Disparar requisições repetidas e simultâneas     |
|Alteração do relógio local           |Timestamp do servidor                                                                    |Mudar relógio do dispositivo não altera evento   |
|Vazamento no repositório             |Segredos fora do Git, exemplos fictícios e configuração de desenvolvimento explícita     |Revisar arquivos e histórico antes de publicar   |
|Vazamento em logs                    |Não registrar senhas, cookies ou tokens; minimizar dados pessoais                        |Revisar logs de sucesso e falha                  |

Não coletar CPF, endereço, GPS ou biometria sem necessidade. Guardar apenas os dados necessários à jornada. Não afirmar adequação jurídica automática à LGPD, GDPR ou normas de ponto. Políticas de retenção e requisitos por país exigem definição posterior.

Contas demonstrativas devem usar apenas dados fictícios e existir somente no modo de desenvolvimento/avaliação. Produção não deve iniciar com senha pública de seed ou credenciais padrão.

## 11. Experiência de uso

- Tela inicial com estado atual, última marcação confirmada, fuso e próxima ação válida.
- Botão bloqueado durante o envio e confirmação somente após resposta de sucesso do servidor.
- Em timeout/reload, consultar recibo próprio pela chave preservada antes do envio e atualizar estado; repetição explícita mantém chave/payload, sem envio automático ou horário retroativo. A resposta pode ter se perdido após a gravação.
- Histórico com filtro por período, total fechado e quantidade de jornadas pendentes, apresentados separadamente.
- Horários formatados conforme a localidade da interface, mantendo o fuso explícito.
- Navegação por teclado, labels, foco visível, contraste e mensagens que não dependam apenas de cor.
- Datas e totais calculados no backend; o frontend apenas apresenta os dados e, quando necessário, uma estimativa provisória.

## 12. Plano de validação

Nenhum teste abaixo foi executado; são critérios de implementação e aceite.

|Cenário                                                       |Resultado esperado                                                                   |
|--------------------------------------------------------------|-------------------------------------------------------------------------------------|
|Entrada, pausa, retorno e saída                               |Sequência persistida e duração correta                                               |
|Duas pausas                                                   |Ambos os intervalos descontados                                                      |
|Retorno sem pausa ou duas entradas                            |Conflito sem novo evento                                                             |
|Duas requisições simultâneas incompatíveis                    |Uma transição válida; a outra rejeitada                                              |
|Mesma chave e mesmo payload após timeout                      |Mesmo resultado; um único evento                                                     |
|Mesma chave e payload diferente                               |Conflito explícito                                                                   |
|Jornada entre 22h e 06h                                       |Duração correta e data de referência do início                                       |
|Mudança de horário de verão                                   |Duração corresponde aos instantes UTC                                                |
|Visualização em outro fuso durante viagem                     |Horários mudam; duração e agrupamento permanecem                                     |
|Jornada atravessa o mês                                       |Agrupamento segue a regra documentada                                                |
|Jornada sem saída                                             |Pendência sinalizada; separada do total fechado                                      |
|Outro usuário tenta consultar o registro                      |Acesso negado sem exposição de dados                                                 |
|Token antiforgery ausente                                     |Mutação rejeitada                                                                    |
|Login, renovação CSRF e logout                                |Sessão estabelecida corretamente; token renovado e sessão antiga inválida após logout|
|Dois usuários em sessões distintas                            |Cada um consulta e marca somente sua própria jornada                                 |
|Relógio do servidor retorna instante anterior ao último evento|Rejeição controlada; nenhuma duração negativa                                        |
|Reiniciar API e banco persistente                             |Registros continuam disponíveis                                                      |
|Banco vazio                                                   |Migrations e seed de avaliação funcionam                                             |
|Celular, teclado e conexão interrompida                       |Fluxos utilizáveis e confirmação coerente                                            |

Testar constraints, migrations e concorrência com SQL Server real, preferencialmente via Testcontainers. H2 ou mocks não comprovam índices filtrados, tipos temporais e comportamento transacional do SQL Server.

## 13. Documentação de entrega e ordem de implementação

O README final deve conter: problema, funcionalidades realmente implementadas, stack e versões, pré-requisitos, comandos exatos de execução, migrations e seed, acesso demonstrativo, testes, decisões de fuso e segurança, limitações e roteiro de avaliação. Não incluir comandos especulativos antes de existir o projeto.

Ordem sugerida:

1. Registrar prazo e ambiente disponível; preparar o escopo mínimo para análise autônoma do repositório pelo técnico.
2. Criar estrutura, banco e autenticação; validar execução a partir de banco vazio.
3. Implementar estados, persistência, UTC, autorização e concorrência.
4. Implementar telas de registro e consulta responsivas.
5. Executar testes de fusos, acesso indevido, repetição e persistência.
6. Incluir regularização mínima de saída esquecida e seus testes antes do aceite do núcleo; consulta geral de gestor/RH e outras correções somente após núcleo estável e conforme prazo.
7. Atualizar documentação com comportamento real, revisar segredos e preparar o repositório público.

Antes de cada funcionalidade: identificar requisito atendido, regra assumida, risco de segurança, critério de aceite e complexidade justificada. Depois: conferir código, comportamento observado e correspondência com a documentação.

## 14. Correspondência com os critérios de avaliação

Esta matriz orienta a implementação e a revisão. É uma proposta de evidências; não representa testes aprovados nem critérios adicionais atribuídos à empresa.

|Critério informado                    |Como será atendido                                                                     |Evidência para o técnico                                                 |
|--------------------------------------|---------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
|Capacidade de interpretação de cenário|Tratar Brasil/Europa, viagens, trabalho remoto, meia-noite e registros incompletos     |Regras assumidas identificadas e testes de cenários temporais            |
|Organização e estruturação da solução |Separar responsabilidades, manter estrutura previsível e execução reproduzível         |Estrutura do repositório, configuração por ambiente e README             |
|Qualidade de código                   |Nomes claros, DTOs validados, regras testáveis e tratamento consistente de erros       |Código legível e testes relevantes, sem duplicação de regras no frontend |
|Modelagem da aplicação                |Separar usuário, colaborador, jornada e evento; definir relações e invariantes         |Modelo descrito, migrations, constraints e testes de persistência        |
|Tomada de decisões técnicas           |Justificar Java, Angular, SQL Server, sessão, UTC e escopo; registrar trade-offs       |Decisões técnicas e limitações explícitas, coerentes com o código        |
|Clareza na comunicação técnica        |Descrever execução, API, regras e estado real das funcionalidades                      |README, documentação da API e roteiro de avaliação sem depender da autora|
|Experiência de uso da aplicação       |Layout responsivo, ações válidas, feedback, acessibilidade e recuperação de falhas     |Fluxo completo em celular/desktop, estados de erro e teste por teclado   |
|Adoção de boas práticas de mercado    |Segurança no servidor, migrations, transações, idempotência, testes e segredos externos|Testes de acesso indevido/concorrência, banco limpo e configuração segura|

### Revisão antes da entrega

- Cada requisito explícito deve ter uma funcionalidade utilizável e uma forma concreta de verificação.
- Regras inferidas devem permanecer identificadas como decisões, sem inventar exigências do avaliador.
- Código, schema, contratos de API, interface e documentação devem descrever o mesmo comportamento.
- Repetir o procedimento do README a partir de um clone limpo e um banco vazio; registrar comandos e resultados reais.
- Incluir ao menos dois colaboradores fictícios em fusos diferentes para avaliar isolamento de acesso e visualização internacional. Incluir gestor/RH apenas se esses fluxos estiverem implementados.
- Documentar cenários manuais e automatizados, suas evidências e limitações; não inventar cobertura ou resultados.
- Entregar a base completa e estável antes de adicionar funcionalidades secundárias.
- Conferir a entrega solicitada: repositório Git público, instruções, decisões e informações de avaliação. O enunciado indica envio do link para `lucas.freitas@dreamerstudios.io`; preparar esse envio após concluir a solução, sem enviar nesta etapa.

## 15. Referências técnicas

- Spring Security — senhas: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
- Spring Security — CSRF e integração SPA: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- Spring Boot — inicialização do banco e Flyway: https://docs.spring.io/spring-boot/how-to/data-initialization.html
- Java — Instant: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Instant.html
- SQL Server — datetimeoffset: https://learn.microsoft.com/en-us/sql/t-sql/data-types/datetimeoffset-transact-sql
- SQL Server — índices filtrados: https://learn.microsoft.com/en-us/sql/relational-databases/indexes/create-filtered-indexes
- Segurança no Angular: https://angular.dev/best-practices/security

As referências fundamentam mecanismos técnicos; as regras de jornada deste documento são decisões propostas para o desafio.
## 16. Documentos de apoio

- [Levantamento de requisitos](requisitos.md): requisitos com origem, prioridade e aceite.
- [EAP e pacotes de entrega](eap.md): decomposição do escopo e dependências.
- [Arquitetura e decisões](arquitetura.md): modelo, segurança e trade-offs.
- [Plano de validação e riscos](validacao.md): testes, evidências e riscos de entrega.

Todos são documentos de planejamento. Seu conteúdo deve acompanhar a implementação; não são evidência de funcionalidades concluídas.
