# Work Journey Platform

Plataforma web de registro e consulta de jornada de trabalho para a empresa fictícia **Dungeons & Dragons Group**, com colaboradores no Brasil e na Europa, trabalho remoto e viagens internacionais.

## O que funciona nesta versão

- Login e logout com sessão, proteção CSRF e contas provisionadas, sem cadastro público.
- Registro de **entrada, início de pausa, retorno e saída**, sempre para o colaborador autenticado.
- Validação da sequência de eventos, recibos idempotentes e proteção transacional contra marcações concorrentes.
- **Jornada atual**, histórico paginado por período e detalhe das marcações, com acesso somente aos próprios registros.
- Recuperação de tentativas sem confirmação: consulta do recibo e repetição explícita com a mesma chave, sem reenvio automático.
- Instantes definidos pelo servidor em **UTC** e **fuso IANA do navegador registrado em cada marcação**.
- Interface responsiva, com verificações automatizadas em Chromium desktop e viewport móvel de 360 px; os testes de jornada também verificam reflow em 320, 768 e 1280 px e texto a 200%.
- Persistência em **SQL Server**, migrations **V1–V5** pelo Flyway e validação com SQL Server real na CI.

A regularização de saída esquecida e o resumo consolidado por período ainda não integram esta versão. A aprovação das suítes automatizadas não representa aceite integral do desafio nem validação de produção.

## Tecnologias e decisões

| Parte | Tecnologia |
|---|---|
| Backend | Java 17, Spring Boot 3.5.16, Spring Security |
| Interface | Angular 22.2 e TypeScript |
| Banco de dados | Microsoft SQL Server 2022 |
| Persistência | Spring Data JPA/Hibernate e Flyway |
| Ferramentas | Maven Wrapper, Node 24.19.0, Playwright |

Java foi escolhido pela minha experiência com a linguagem. O backend difere do ecossistema Microsoft predominante na empresa; esse trade-off está registrado em [arquitetura e decisões](docs/arquitetura.md). Angular segue o direcionamento do desafio, e SQL Server combina familiaridade acadêmica com a informação recebida sobre seu uso pela empresa.

As versões Node e npm estão indicadas em `.nvmrc` e `frontend/package.json`; as dependências npm resolvidas estão em `frontend/package-lock.json`. A CI usa SQL Server 2022 CU20 e Java 17.

## Como executar localmente

Os comandos abaixo são para **Bash em macOS/Linux**, em um banco exclusivo de desenvolvimento. Pré-requisitos: Git, **JDK 17**, **Node 24.19.0** e **Docker** com suporte à imagem do SQL Server. O Maven Wrapper dispensa instalação global do Maven; a primeira execução precisa de internet.

```bash
git clone https://github.com/Juliarezende521/work-journey-platform.git
cd work-journey-platform
```

O Spring Boot **não lê `.env.example` automaticamente**: exporte as variáveis no terminal ou configure a IDE. As senhas dos exemplos abaixo são fictícias, para um ambiente local isolado; não são credenciais de um serviço publicado.

### 1. Iniciar e provisionar o SQL Server

A imagem abaixo acompanha a versão usada pela CI. A porta fica acessível somente pelo computador local; o volume preserva os dados ao reiniciar o contêiner.

```bash
export MSSQL_SA_PASSWORD='LocalSqlServer@2026'
docker run -d --name work-journey-sql \
  -p 127.0.0.1:1433:1433 \
  -e ACCEPT_EULA=Y -e MSSQL_PID=Developer -e MSSQL_SA_PASSWORD \
  -v work-journey-sql-data:/var/opt/mssql \
  mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04

sql() {
  docker exec -i -e SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" work-journey-sql \
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b "$@"
}
```

Verifique a prontidão com `sql -Q 'SELECT 1'`. Se a conexão ainda não estiver disponível, aguarde e repita. Após conectar, execute **uma única vez em uma instância nova**:

```bash
sql <<'SQL'
CREATE DATABASE work_journey;
GO
CREATE LOGIN work_journey_migrator WITH PASSWORD = 'LocalMigrator@2026';
CREATE LOGIN work_journey_app WITH PASSWORD = 'LocalApplication@2026';
GO
USE work_journey;
CREATE USER work_journey_migrator FOR LOGIN work_journey_migrator;
ALTER ROLE db_ddladmin ADD MEMBER work_journey_migrator;
ALTER ROLE db_datareader ADD MEMBER work_journey_migrator;
ALTER ROLE db_datawriter ADD MEMBER work_journey_migrator;
CREATE USER work_journey_app FOR LOGIN work_journey_app;
GO
SQL
```

### 2. Criar o schema e conceder permissões específicas

Na raiz do projeto, configure a conexão e inicialize o schema com a conta de provisionamento. Essa primeira inicialização não usa o perfil de contas demonstrativas.

```bash
export DB_URL='jdbc:sqlserver://localhost:1433;databaseName=work_journey;encrypt=true;trustServerCertificate=true'
export DB_MIGRATION_USERNAME='work_journey_migrator'
export DB_MIGRATION_PASSWORD='LocalMigrator@2026'
export DB_USERNAME="$DB_MIGRATION_USERNAME"
export DB_PASSWORD="$DB_MIGRATION_PASSWORD"
export SERVER_ADDRESS='127.0.0.1'

(cd backend && ./mvnw spring-boot:run)
```

Espere a mensagem `Started WorkJourneyApplication`, que indica a conclusão das migrations e da validação do schema, e encerre essa inicialização com **Ctrl+C**. A aplicação não deve continuar rodando com a conta de provisionamento.

No mesmo terminal, conceda as permissões de execução agora que as tabelas existem:

```bash
sql -d work_journey <<'SQL'
GRANT SELECT, INSERT ON dbo.app_user TO work_journey_app;
GRANT SELECT, INSERT ON dbo.employee TO work_journey_app;
GRANT SELECT, INSERT, UPDATE ON dbo.work_session TO work_journey_app;
GRANT SELECT, INSERT ON dbo.work_event TO work_journey_app;
GRANT SELECT, INSERT ON dbo.idempotency_request TO work_journey_app;
GO
SQL
```

A conta de execução não recebe DDL, nem UPDATE/DELETE em eventos ou recibos. As credenciais de migration continuam separadas. `trustServerCertificate=true` e `sqlcmd -C` aceitam o certificado do SQL Server local; em um ambiente publicado, a conexão deve validar o certificado. Detalhes em [autenticação](docs/autenticacao.md) e [permissões de marcação](docs/marcacoes-idempotencia.md).

### 3. Iniciar o backend com as contas de avaliação

```bash
export DB_USERNAME='work_journey_app'
export DB_PASSWORD='LocalApplication@2026'
export EVALUATION_PASSWORD='EscolhaUmaSenhaLocal@2026'

(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=evaluation)
```

O backend atende em `http://127.0.0.1:8080`. O perfil `evaluation` cria as três contas abaixo se ainda não existirem. A senha deve ter pelo menos **12 caracteres e no máximo 72 bytes UTF-8**. Alterar `EVALUATION_PASSWORD` e reiniciar não altera senhas de contas já existentes. Esse perfil é somente para avaliação.

| E-mail | Fuso de referência |
|---|---|
| `brasil@example.invalid` | `America/Sao_Paulo` |
| `europa@example.invalid` | `Europe/Amsterdam` |
| `rh@example.invalid` (consulta RH) | `America/Sao_Paulo` |

A senha das três contas é o valor usado em `EVALUATION_PASSWORD` quando as contas foram criadas.

### 4. Iniciar o frontend com HTTPS

Em outro terminal, na raiz do projeto:

```bash
cd frontend
npm ci
npm start -- --ssl
```

Abra **https://localhost:4200**. A API é encaminhada pelo proxy para `http://127.0.0.1:8080`; o navegador acessa frontend e API pela mesma origem HTTPS.

O servidor de desenvolvimento pode apresentar um certificado local não confiável. Para usar um certificado local já confiável pelo seu navegador, informe os caminhos:

```bash
npm start -- --ssl --ssl-cert /caminho/localhost.pem --ssl-key /caminho/localhost-key.pem
```

**Por que HTTPS?** O cookie de sessão tem `Secure=true` no modo com SQL Server. HTTP puro não é o caminho documentado para demonstrar o login: esta execução usa HTTPS explicitamente, sem desabilitar essa proteção. O certificado de desenvolvimento não substitui a configuração TLS de produção.

No Windows/PowerShell, use `$env:NOME="valor"` em vez de `export` e `.\mvnw.cmd` no lugar de `./mvnw`; os comandos SQL são os mesmos, mas funções Bash e heredocs precisam ser adaptados.

### Visão de RH nesta branch

O perfil de avaliação também cria `rh@example.invalid` (Recursos Humanos, referência `America/Sao_Paulo`), com a mesma senha de avaliação definida na criação. A permissão `hr_reader` é persistida pela V5 e vem desabilitada para contas preexistentes; o seed não promove contas já existentes com esse e-mail. É necessário autenticar novamente após conceder uma permissão.

A conta habilitada vê a opção **Visão do RH**, seleciona um colaborador ativo e consulta seu histórico por período e os detalhes. A tela reaproveita o histórico existente, sem trocar a identidade autenticada. Diretório paginado: 20 itens por página; limite de API: 100. Listagens não expõem e-mails, hashes ou credenciais. Fusos, filtros e paginação seguem o histórico próprio.

As rotas GET `/api/hr/employees`, `/api/hr/employees/{employeeId}/work-sessions` e `/api/hr/employees/{employeeId}/work-sessions/{id}` exigem autoridade de RH e revalidam no banco a permissão, a conta ativa e o vínculo autenticado a cada consulta. Uma jornada precisa pertencer ao colaborador selecionado; acesso pela rota própria continua restrito aos registros próprios. As rotas RH rejeitam mutações. Essa permissão não inclui gestão de contas, correção de marcações ou aprovação de regularizações. RH conserva as operações de colaborador somente para os próprios registros.

A implementação permanece isolada em `feature/hr-read-only` até revisão e testes; a main segue como alternativa de entrega. O teste de upgrade verifica que uma conta anterior à V5 não recebe permissão de RH. Evidências finais da branch devem ser consultadas nos checks da PR; não se confundem com as evidências históricas da PR #7 abaixo.

### Diagnóstico sem banco

Para verificar apenas a base HTTP e a interface, sem contas ou marcações:

```bash
# Terminal 1, na raiz do projeto
(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=bootstrap)

# Terminal 2, na raiz do projeto
cd frontend
npm ci
npm start
```

Nesse modo, abra http://localhost:4200. O perfil `bootstrap` não oferece contas de demonstração, persistência ou operações de jornada; `/api/status` informa `journeyAvailable=false`. A exceção local de cookie sem Secure é restrita a esse perfil de diagnóstico.

## Testes e evidências

Execute cada bloco a partir da raiz do projeto:

```bash
(cd backend && ./mvnw verify)
```

```bash
(cd frontend && npm ci && npm run build && npm test -- --watch=false)
```

A suíte backend padrão não exige banco e inclui testes de domínio e HTTP/segurança com dependências simuladas. **`./mvnw verify` sozinho não executa os testes `*IT` de SQL Server.** O workflow [SQL Server e navegador](.github/workflows/auth-integration.yml) provisiona um banco descartável, verifica migrations/upgrade, permissões e concorrência, repete o seed e executa Playwright contra o backend real e uma origem HTTPS. O script desse workflow é exclusivo da CI, não um instalador local.

Evidências do código integrado pela [PR #7](https://github.com/Juliarezende521/work-journey-platform/pull/7): [builds/testes](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38657348945) e [SQL Server/HTTPS/Chromium](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38657349129), com 66 testes backend, 50 Angular, 38 de integração SQL Server, 1 de upgrade e 4 execuções E2E aprovados. Reexecuções não são somadas como testes distintos. Para alterações posteriores, consulte os checks do respectivo commit.

## Premissas assumidas

- Eventos de entrada, início de pausa, retorno e saída como recorte inicial; as regras assumidas estão em [requisitos](docs/requisitos.md) e [proposta](docs/proposta.md).
- Identidade e instante das marcações definidos pelo servidor, sem aceitar colaborador ou horário arbitrários do cliente.
- Contas provisionadas e um único perfil de colaborador, com acesso aos próprios registros.
- Jornada vinculada à data de entrada no fuso de referência; mudar o fuso de visualização não reclassifica o histórico.

## Trabalho remoto e viagens

O instante de cada marcação é definido pelo servidor, em UTC. O navegador informa o **fuso IANA** do dispositivo, como `Europe/Lisbon`, que o servidor valida e guarda no evento (`recordedTimeZoneId`). Sem `timeZoneId`, usa-se o fuso de referência atual do colaborador.

O `referenceTimeZoneId` e o `workDate` da sessão continuam definidos pelo fuso de referência do colaborador na entrada. Por exemplo, às **01h30 UTC de 15/10/2026**, uma entrada com fuso de registro Lisboa e referência São Paulo pertence à jornada de **14/10/2026**. Os filtros e o agrupamento permanecem estáveis.

Quando o fuso do evento difere do da sessão, a última marcação da jornada atual e o detalhe do histórico indicam essa diferença. Repetições da mesma tentativa preservam o fuso original e o mesmo payload idempotente.

O fuso é uma informação declarada pelo navegador: **não comprova localização física nem identifica se o trabalho é presencial, remoto ou em viagem**.

## Fora desta versão e próximos passos

- **Regularização de saída esquecida**, com solicitação e decisão auditada: planejada no recorte P0, mas não disponível na `main`. Existe trabalho separado em `preserve/exit-regularization-p1`; essa branch não faz parte desta entrega.
- **Resumo consolidado por período**, para acompanhamento de fechamento mensal.
- Visão de gestor por equipe, administração e recuperação de contas.
- Tipo de local de trabalho e observações por marcação.
- Folha de pagamento, cálculo legal de horas extras, geolocalização e operação offline não fazem parte do escopo inicial.

## Limitações conhecidas

- Os testes em viewport móvel não substituem teste em celular físico. Safari, dispositivos físicos e pesquisa de usabilidade com participantes continuam pendentes.
- O provisionamento local do SQL Server é manual; não há Docker Compose. Use um host compatível com a imagem utilizada. Em equipamento ARM, não presumir suporte nativo ou emulação validada: a conexão pode apontar para uma instância SQL Server externa compatível.
- As etapas locais acima foram revisadas contra o código e o provisionamento da CI; a reprodução integral desse roteiro em uma máquina nova ainda precisa ser validada. A CI usa um proxy HTTPS próprio.
- TLS publicado, gestão de segredos, permissões e configuração operacional de produção não foram validados como um ambiente de produção completo.

## Documentação

| Documento | Finalidade |
|---|---|
| [Requisitos](docs/requisitos.md) | Prioridades, regras e critérios de aceite |
| [Arquitetura](docs/arquitetura.md) | Modelo, segurança e trade-offs |
| [Autenticação](docs/autenticacao.md) | Contas, sessão, CSRF e provisionamento |
| [API de jornada](docs/api-jornada.md) | Contratos de marcação, recibo e jornada atual |
| [Marcações e idempotência](docs/marcacoes-idempotencia.md) | Identidade, instante e integridade das gravações |
| [Consultas](docs/consultas-jornada.md) | Histórico e detalhes; contém registros de etapas anteriores |
| [Experiência do usuário/IHC](docs/experiencia-usuario.md) | Fluxos, acessibilidade e plano de avaliação |
| [Validação](docs/validacao.md) | Cenários e riscos |
| [Checklist de revisão](docs/checklist-revisoes.md) | Critérios de revisão por etapa |

Os documentos de etapas preservam registros históricos e podem descrever planejamento ou recursos que não estão na versão atual. Confira o código da `main`, o escopo acima e os checks do commit para distinguir implementado, validado e planejado. Outros materiais estão em [docs/](docs/).
