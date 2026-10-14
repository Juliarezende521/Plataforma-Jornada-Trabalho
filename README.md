# Work Journey Platform

Plataforma web de registro e consulta de jornada para a empresa fictícia **Dungeons & Dragons Group**, com colaboradores no Brasil e Europa, trabalho remoto e viagens internacionais.

**Status: autenticação integrada validada em CI; jornada online implementada e validada progressivamente na PR #4, ainda sem merge.** Angular, Spring Boot e SQL Server real passaram pelo fluxo de login, sessão, CSRF, logout e expiração em Chromium desktop e viewport de 360 px. Schema/JPA, serviço com identidade/Clock e recibos idempotentes têm validação própria em [persistência](docs/persistencia-jornada.md) e [marcações](docs/marcacoes-idempotencia.md). Endpoints de marcação/recibo/jornada atual implementados e validados em CI, conforme [API de jornada](docs/api-jornada.md). Tela de jornada e diário/reconciliação Angular implementados, com testes locais/CI e evidência progressiva em [tela e IHC](docs/tela-jornada.md). Histórico, regularização e aceite completo permanecem pendentes; journeyAvailable anuncia o recurso online fora do perfil bootstrap, sem representar aceite integral. Consulte também [validação da autenticação](docs/validacao-integrada.md), [contrato](docs/autenticacao.md) e [login Angular](docs/login-angular.md). Celular físico/Safari e produção ainda não validados.

## Tecnologias escolhidas

| Parte | Tecnologia |
|---|---|
| Backend | Java, Spring Boot e Spring Web MVC |
| Interface web | Angular e TypeScript |
| Banco de dados | Microsoft SQL Server |
| Persistência | Spring Data JPA/Hibernate |
| Segurança | Spring Security, sessão e proteção CSRF |
| Versionamento do schema no SQL Server (migrations) | Flyway |

**O banco de dados é o Microsoft SQL Server.** O Flyway executa e registra a versão dos scripts SQL que criam ou alteram tabelas nesse banco. Spring Data JPA/Hibernate faz o acesso aos dados pela aplicação.

Java foi escolhido pela experiência da desenvolvedora. Angular segue o direcionamento do desafio. SQL Server combina familiaridade acadêmica e a informação recebida sobre sua utilização pela empresa. O backend difere do ecossistema Microsoft predominante; esse trade-off está documentado.

Versões nesta base: Java 17, Spring Boot 3.5.16, Maven 3.9.11 (Wrapper), Angular/CLI 22.2 e Node 24.19.0. As versões npm resolvidas estão travadas em package-lock.json. SQL Server validado em CI: 2022 CU20 (16.0.4205.1); Chromium via Playwright 1.64.0.

## Escopo inicial

- Login e logout de contas provisionadas.
- Entrada, início de pausa, retorno e saída.
- Consulta de jornada atual, histórico, detalhes e resumo por período.
- Instantes UTC, fuso de referência estável e visualização em outros fusos.
- Autorização por usuário, transações, idempotência e controle de concorrência.
- Interface responsiva e execução reproduzível.

Regularização mínima de saída esquecida, com solicitação própria e decisão auditada por aprovador autorizado, integra o núcleo planejado (RF17/RF18, recorte P0); ainda não implementada. Consulta geral por gestor/RH e outras correções permanecem P1. Folha de pagamento, cálculo legal de horas extras, geolocalização e operação offline estão fora do escopo inicial.

## Documentação

| Documento | Finalidade |
|---|---|
| [Requisitos](docs/requisitos.md) | Origem, prioridade e critérios de aceite |
| [Stakeholders](docs/stakeholders.md) | Interessados, contextos de uso e hipóteses a validar |
| [Histórias de usuário](docs/historias-usuario.md) | Necessidades e aceite rastreáveis aos requisitos/testes |
| [Experiência do usuário/IHC](docs/experiencia-usuario.md) | Fluxos, acessibilidade, inspeção e plano de teste de usabilidade |
| [Arquitetura e decisões](docs/arquitetura.md) | Modelo, segurança, contratos e trade-offs |
| [Modelo inicial de jornada](docs/modelo-jornada.md) | Domínio online, evidências dos testes e limites da etapa; sem persistência/API de jornada |
| [Persistência de jornada](docs/persistencia-jornada.md) | SQL Server, mapeamento JPA, reconstituição e versão; evidências e limites separados de API |
| [Marcações e idempotência](docs/marcacoes-idempotencia.md) | Identidade/Clock do servidor, recibos e conflitos; validação separada de HTTP/Angular |
| [EAP](docs/eap.md) | Pacotes de entrega e dependências |
| [Validação e riscos](docs/validacao.md) | Cenários, evidências esperadas e roteiro do avaliador |
| [Checklist de revisão por etapa](docs/checklist-revisoes.md) | Qualidade, segurança, desempenho, legibilidade e segunda análise dos achados em todas as próximas etapas |
| [Proposta inicial](docs/proposta.md) | Contexto, regras e matriz dos oito critérios de avaliação |

## Execução e testes

### Diagnóstico local sem banco

Pré-requisitos: Git, JDK 17 e Node 24.19.0. O Maven Wrapper baixa Maven na primeira execução; não é necessário instalá-lo globalmente. A primeira execução exige internet para baixar dependências. No perfil bootstrap não existem contas demonstrativas; elas são provisionadas apenas no perfil evaluation com SQL Server.

Clone o repositório e abra dois terminais. Este modo **não valida SQL Server** e não deve ser utilizado em produção.

Backend (macOS/Linux):

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=bootstrap
```

No Windows/PowerShell, substitua `./mvnw` por `.\mvnw.cmd`.

Interface, no segundo terminal:

```bash
cd frontend
npm ci
npm start
```

Abra http://localhost:4200. A interface consulta /api/me e mostra o formulário quando não há sessão. O proxy encaminha /api para http://127.0.0.1:8080. Bootstrap não tem contas: tentativas de login retornam erro genérico e não demonstram autenticação com banco. GET /api/status continua disponível para diagnóstico da base, com journeyAvailable=false.

### Build e testes

No diretório backend, execute `./mvnw verify` (Windows: `.\mvnw.cmd verify`). No diretório frontend:

```bash
npm ci
npm run build
npm test -- --watch=false
```

Os testes backend padrão usam bootstrap ou testes unitários, sem banco. Exercitam login por senha com serviço de contas simulado, sessão, CSRF, logout, modelo e limite de tentativas. Não comprovam SQL Server nem regras de jornada. O teste opt-in SqlServerIT exige banco real e variáveis de conexão. O workflow de CI repete builds/testes; sua aprovação deve ser conferida no GitHub.

### Configuração do SQL Server

.env.example documenta DB_URL, DB_USERNAME, DB_PASSWORD, DB_MIGRATION_USERNAME e DB_MIGRATION_PASSWORD. Spring Boot **não carrega esse arquivo automaticamente**: configure as variáveis no terminal ou na IDE. Sem bootstrap, a configuração exige SQL Server e credenciais. Nesta branch, Flyway aplica V1–V3 e Hibernate valida o schema; o upgrade foi aprovado em SQL Server 2022 CU20 na CI. Usuários de migration/runtime são separados; conferir os [grants de execução](docs/marcacoes-idempotencia.md) após aplicar migrations. Contas fictícias são provisionadas somente pelo perfil evaluation com EVALUATION_PASSWORD externa; veja [instruções](docs/autenticacao.md).

A conexão publicada deve validar TLS/certificado. Não usar sa como conta da aplicação. O usuário de migrations terá DDL, separado do usuário de execução. Secure=false existe somente no perfil bootstrap local, vinculado a 127.0.0.1 para permitir HTTP de desenvolvimento. Não publicar senhas reais.

O SQL Server deverá executar em ambiente suportado. A conexão será configurável para permitir instância local ou externa; a compatibilidade da arquitetura do equipamento será verificada antes de definir Docker Compose e Testcontainers.

## Próximas entregas

1. Implementar registro, cálculo, autorização e consultas de jornada.
2. Construir interface de jornada e executar os testes críticos de fusos/concorrência.
3. Validar dispositivo físico/Safari, teclado e acessibilidade; certificados do ambiente publicado.
4. Revisar documentação e repetir instruções locais com SQL Server.

O repositório é público, conforme solicitado no desafio. Todas as informações demonstrativas serão fictícias; senhas, tokens e configurações privadas devem permanecer fora do Git.

### Consultas próprias nesta branch

Histórico paginado, detalhe explicável e resumo do período implementados nesta etapa; contrato, IHC, revisão e status de validação em [consultas de jornada](docs/consultas-jornada.md). Soma somente jornadas encerradas, pendências separadas, troca de fuso apenas visual. Regularização mínima e aceite completo continuam pendentes.
