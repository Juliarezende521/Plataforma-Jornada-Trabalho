# Work Journey Platform

Plataforma web de registro e consulta de jornada para a empresa fictícia **Dungeons & Dragons Group**, com colaboradores no Brasil e Europa, trabalho remoto e viagens internacionais.

**Status: planejamento inicial.** Este repositório contém a especificação da solução. Backend, frontend, migrations e testes ainda não foram implementados. As funcionalidades e medidas de segurança descritas são propostas a validar durante o desenvolvimento.

## Tecnologias escolhidas

| Parte | Tecnologia |
|---|---|
| Backend | Java, Spring Boot e Spring Web MVC |
| Interface web | Angular e TypeScript |
| Banco de dados | Microsoft SQL Server |
| Persistência | Spring Data JPA/Hibernate |
| Segurança | Spring Security, sessão e proteção CSRF |
| Evolução do banco | Flyway |

Java foi escolhido pela experiência da desenvolvedora. Angular segue o direcionamento do desafio. SQL Server combina familiaridade acadêmica e a informação recebida sobre sua utilização pela empresa. O backend difere do ecossistema Microsoft predominante; esse trade-off está documentado. Versões compatíveis serão fixadas antes da implementação.

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

Ainda não há aplicação executável. Os comandos de instalação, execução, migrations, seed e testes serão adicionados e verificados a partir de um clone limpo conforme o código for implementado. Não há contas demonstrativas, URL de aplicação ou testes aprovados nesta etapa.

O SQL Server deverá executar em ambiente suportado. A conexão será configurável para permitir instância local ou externa; a compatibilidade da arquitetura do equipamento será verificada antes de definir Docker Compose e Testcontainers.

## Próximas entregas

1. Verificar o ambiente e fixar versões.
2. Implementar estrutura, migrations e autenticação.
3. Implementar registro, cálculo, autorização e consultas.
4. Construir interface e executar os testes críticos.
5. Registrar evidências, revisar documentação e validar execução limpa.

O repositório é público, conforme solicitado no desafio. Todas as informações demonstrativas serão fictícias; senhas, tokens e configurações privadas devem permanecer fora do Git.
