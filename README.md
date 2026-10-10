# Work Journey Platform

Plataforma web de registro e consulta de jornada para a empresa fictícia **Dungeons & Dragons Group**, com colaboradores no Brasil e Europa, trabalho remoto e viagens internacionais.

**Status: base técnica em desenvolvimento.** Backend Spring Boot e frontend Angular estão estruturados, com uma rota de diagnóstico e uma tela para verificar comunicação. Login, jornada, histórico, entidades e migrations de negócio ainda não estão implementados. Consulte o [registro da etapa](docs/base-tecnica.md) para evidências e limitações. Os requisitos descrevem o escopo planejado, não recursos já entregues.

## Tecnologias escolhidas

| Parte | Tecnologia |
|---|---|
| Backend | Java, Spring Boot e Spring Web MVC |
| Interface web | Angular e TypeScript |
| Banco de dados | Microsoft SQL Server |
| Persistência | Spring Data JPA/Hibernate |
| Segurança | Spring Security, sessão e proteção CSRF |
| Evolução do banco | Flyway |

Java foi escolhido pela experiência da desenvolvedora. Angular segue o direcionamento do desafio. SQL Server combina familiaridade acadêmica e a informação recebida sobre sua utilização pela empresa. O backend difere do ecossistema Microsoft predominante; esse trade-off está documentado.

Versões nesta base: Java 17, Spring Boot 3.5.16, Maven 3.9.11 (Wrapper), Angular/CLI 22.2 e Node 24.19.0. As versões npm resolvidas estão travadas em package-lock.json. A versão de SQL Server será fixada na etapa de integração com banco real.

## Escopo inicial

- Login e logout de contas provisionadas.
- Entrada, início de pausa, retorno e saída.
- Consulta de jornada atual, histórico, detalhes e resumo por período.
- Instantes UTC, fuso de referência estável e visualização em outros fusos.
- Autorização por usuário, transações, idempotência e controle de concorrência.
- Interface responsiva e execução reproduzível.

Consulta por gestor/RH e correções auditadas são complementos priorizados após estabilizar o núcleo. Folha de pagamento, cálculo legal de horas extras, geolocalização e operação offline estão fora do escopo inicial.

## Documentação

| Documento | Finalidade |
|---|---|
| [Requisitos](docs/requisitos.md) | Origem, prioridade e critérios de aceite |
| [Arquitetura e decisões](docs/arquitetura.md) | Modelo, segurança, contratos e trade-offs |
| [EAP](docs/eap.md) | Pacotes de entrega e dependências |
| [Validação e riscos](docs/validacao.md) | Cenários, evidências esperadas e roteiro do avaliador |
| [Proposta inicial](docs/proposta.md) | Contexto, regras e matriz dos oito critérios de avaliação |

## Execução e testes

### Diagnóstico local sem banco

Pré-requisitos: Git, JDK 17 e Node 24.19.0. O Maven Wrapper baixa Maven na primeira execução; não é necessário instalá-lo globalmente. A primeira execução exige internet para baixar dependências. Não existem contas demonstrativas.

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

Abra http://localhost:4200 e clique em “Verificar conexão com a API”. O proxy encaminha /api para http://127.0.0.1:8080. GET /api/status identifica a etapa FOUNDATION e informa journeyAvailable=false. Não retorna dados pessoais nem configuração do ambiente.

### Build e testes

No diretório backend, execute `./mvnw verify` (Windows: `.\mvnw.cmd verify`). No diretório frontend:

```bash
npm ci
npm run build
npm test -- --watch=false
```

Os testes backend usam explicitamente bootstrap, sem banco. Verificam status público, bloqueio de consulta sem autenticação e CSRF com usuário sintético. Não comprovam autenticação real, persistência nem regras de jornada. O workflow de CI repete builds/testes; sua aprovação deve ser conferida no GitHub.

### Configuração futura do SQL Server

.env.example documenta DB_URL, DB_USERNAME, DB_PASSWORD, DB_MIGRATION_USERNAME e DB_MIGRATION_PASSWORD. Spring Boot **não carrega esse arquivo automaticamente**: configure as variáveis no terminal ou na IDE. Sem bootstrap, a configuração exige SQL Server e credenciais. Ainda não há scripts SQL de negócio; migrations e entidades serão implementadas e testadas juntas.

A conexão publicada deve validar TLS/certificado. Não usar sa como conta da aplicação. O usuário de migrations terá DDL, separado do usuário de execução. Secure=false existe somente no perfil bootstrap local, vinculado a 127.0.0.1 para permitir HTTP de desenvolvimento. Não publicar senhas reais.

O SQL Server deverá executar em ambiente suportado. A conexão será configurável para permitir instância local ou externa; a compatibilidade da arquitetura do equipamento será verificada antes de definir Docker Compose e Testcontainers.

## Próximas entregas

1. Detalhar modelo e validar ambiente SQL Server.
2. Implementar migrations, entidades e autenticação.
3. Implementar registro, cálculo, autorização e consultas.
4. Construir interface e executar os testes críticos.
5. Registrar evidências, revisar documentação e validar execução limpa.

O repositório é público, conforme solicitado no desafio. Todas as informações demonstrativas serão fictícias; senhas, tokens e configurações privadas devem permanecer fora do Git.
