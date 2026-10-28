# Work Journey Platform

Aplicação web para registro e consulta de jornada de trabalho, desenvolvida para o desafio técnico da Dungeons & Dragons Group.

O cenário envolve pessoas trabalhando no Brasil e na Europa, inclusive de forma remota e durante viagens. Por isso, além do fluxo básico de ponto, a aplicação trata fuso horário, concorrência entre marcações e consultas com escopo de RH e gestor.

![Tela de jornada em desktop](docs/evidencias/jornada/jornada-pausa-1280.png)

## Funcionalidades

- Login e logout com sessão e proteção CSRF.
- Registro de entrada, início de pausa, retorno e saída.
- Modalidade da marcação: escritório, remoto ou viagem.
- Jornada atual com estado, duração provisória e ações disponíveis.
- Histórico próprio por período e detalhe das marcações.
- Resumo do período com tempo efetivo, dias trabalhados, pendências e jornadas ajustadas.
- Escala semanal configurável por colaborador, com versões por data de vigência.
- Exceções de escala por data para feriados, folgas, treinamentos e jornadas especiais.
- Contrato versionado por colaborador, com país, tipo e carga semanal.
- Políticas operacionais versionadas por país, com limites de referência e trilha de auditoria.
- Visão de RH com panorama dos colaboradores e consulta individual.
- Solicitação de marcação esquecida e decisão auditável pelo RH ou gestor direto.
- Consulta paginada da trilha de auditoria pelo RH.
- Linha do tempo efetiva e duração recalculada após ajustes aprovados, sem apagar os originais.
- Fechamento mensal auditável com fotografia de horas previstas, trabalhadas, saldo e tolerância.
- Equipes versionadas por vigência, com consulta de jornadas limitada aos subordinados diretos do gestor.
- Aprovação ou rejeição de ajustes pelo gestor, limitada à equipe direta e registrada na auditoria.
- Exportação CSV do resumo individual pelo RH, com período limitado e proteção para abertura em planilhas.
- Exportação consolidada dos fechamentos mensais do RH, baseada nas fotografias imutáveis da competência.
- Correções aprovadas podem pausar, retomar ou encerrar uma jornada aberta de forma auditável.
- Horários gravados em UTC, mantendo o fuso IANA informado pelo navegador.
- Proteção contra requisições repetidas e concorrentes.
- Layout responsivo e navegação por teclado.

O fluxo de ajustes e o resumo efetivo do período foram acrescentados depois da primeira entrega, preservando sempre as marcações originais.

## Stack

| Camada | Tecnologia |
|---|---|
| Frontend | Angular 22, TypeScript e SCSS |
| Backend | Java 17, Spring Boot e Spring Security |
| Persistência | SQL Server 2022, JPA/Hibernate e Flyway |
| Testes | JUnit, Vitest e Playwright |
| CI | GitHub Actions |

Angular e SQL Server seguem o contexto do desafio. Escolhi Java no backend por ser a linguagem em que consigo demonstrar melhor modelagem, segurança e testes. Em um produto já integrado ao ecossistema Microsoft, ASP.NET seria uma alternativa natural; a separação entre frontend, domínio e persistência permite essa troca sem redesenhar a experiência.

## Decisões principais

- O servidor define a identidade e o instante de cada marcação.
- Os instantes são persistidos em UTC; o fuso IANA é armazenado separadamente para explicar registros feitos durante viagens.
- A data da jornada é calculada no fuso de referência do colaborador e não muda com o fuso de visualização.
- Uma chave de idempotência evita duplicar eventos após timeout ou repetição de uma requisição.
- Marcações do mesmo colaborador são serializadas no banco; constraints e versionamento protegem o estado entre dispositivos.
- Colaboradores consultam somente os próprios registros. A leitura do RH exige permissão específica revalidada no servidor.
- Gestores consultam somente subordinados diretos vigentes; o vínculo é revalidado em cada recurso solicitado.
- Flyway é a fonte de verdade do banco; o Hibernate apenas valida o schema.

Detalhes e trade-offs estão em [Arquitetura](docs/arquitetura.md), [Requisitos](docs/requisitos.md) e [API de jornada](docs/api-jornada.md).

## Como executar

### Pré-requisitos

- JDK 17
- Node 24 (versão exata em `.nvmrc`)
- Docker com Compose
- Git

Os scripts de desenvolvimento usam Bash em macOS ou Linux. As senhas incluídas neles são fictícias e servem somente para o ambiente local.

### 1. Clonar o projeto

```bash
git clone https://github.com/Juliarezende521/Plataforma-Jornada-Trabalho.git
cd Plataforma-Jornada-Trabalho
```

### 2. Preparar o banco

```bash
./scripts/local/setup.sh
```

Esse comando inicia o SQL Server com Docker Compose, aguarda o banco ficar disponível, cria os usuários, aplica as migrations e concede as permissões da aplicação. Ele pode ser executado novamente sem recriar o banco.

Se a porta `1433` já estiver em uso, escolha outra porta nos comandos de setup e backend:

```bash
SQLSERVER_PORT=14330 ./scripts/local/setup.sh
SQLSERVER_PORT=14330 ./scripts/local/backend.sh
```

### 3. Iniciar a aplicação

Terminal 1:

```bash
./scripts/local/backend.sh
```

Terminal 2:

```bash
./scripts/local/frontend.sh
```

Abra `https://localhost:4200`. O navegador pode pedir confirmação para o certificado local de desenvolvimento.

Para encerrar o banco depois da avaliação:

```bash
docker compose down
```

Use `docker compose down -v` somente se também quiser apagar os dados locais.

### Contas de avaliação

Ao executar `./scripts/local/backend.sh` sem configurar outra senha, use:

**Senha:** `AvaliacaoLocal@2026`

| Conta | Perfil |
|---|---|
| `brasil@example.invalid` | Colaborador em São Paulo |
| `europa@example.invalid` | Colaborador em Amsterdã e gestor da conta Brasil |
| `rh@example.invalid` | Consulta do RH |

Para definir outra senha antes da primeira inicialização:

```bash
EVALUATION_PASSWORD='SuaSenhaLocal@2026' ./scripts/local/backend.sh
```

A senha é definida quando as contas são criadas; alterar a variável depois disso não modifica contas existentes.

## Testes

```bash
(cd backend && ./mvnw verify)
(cd frontend && npm ci && npm run build && npm test -- --watch=false)
```

A suíte padrão do backend não inicia o SQL Server. Os testes de migration, persistência e concorrência com banco real, junto dos fluxos Playwright em HTTPS, são executados pelo workflow [SQL Server e navegador](.github/workflows/auth-integration.yml).

## Estrutura

```text
backend/               API, domínio, segurança e migrations
frontend/              aplicação Angular e testes de interface
docs/                  requisitos, arquitetura e evidências
scripts/ci/            validação integrada com SQL Server e navegador
.github/workflows/     pipelines de build e integração
```

## Limitações conhecidas

- Não há cadastro público nem recuperação de senha; as contas são provisionadas para avaliação.
- O resumo compara tempo efetivo e planejado apenas quando existe escala para todo o período e aplica a tolerância operacional cadastrada. Horas extras, banco de horas e regras legais continuam fora do escopo.
- O fechamento mensal exige competência encerrada, escala completa, jornadas fechadas e ausência de ajustes pendentes. Após o fechamento, mudanças retroativas que alterariam o resultado são bloqueadas.
- A localização é declarada pelo fuso e pela modalidade; a aplicação não usa geolocalização.
- A execução local do SQL Server depende de host compatível com a imagem oficial. Em máquinas ARM pode ser necessário usar uma instância externa.
- O perfil de avaliação e as credenciais de exemplo não representam uma configuração de produção.

## Uso de inteligência artificial

Durante o desenvolvimento, utilizei ferramentas de inteligência artificial como apoio para explorar soluções, revisar pontos de segurança e qualidade e agilizar algumas tarefas de implementação e documentação.

As escolhas de arquitetura, a definição das regras de negócio e a composição da solução foram avaliadas por mim de acordo com o contexto do desafio. Revisei o código produzido e validei seu funcionamento por meio de testes locais e dos fluxos automatizados configurados no projeto. Estou preparada para explicar as decisões adotadas, seus benefícios, limitações e possíveis evoluções.

## Próximos passos

1. Validar as políticas operacionais por país com especialistas antes de calcular banco de horas ou horas extras.
2. Integração com o provedor de identidade da empresa.
3. Validação em dispositivos físicos com pessoas de RH e colaboradores.
