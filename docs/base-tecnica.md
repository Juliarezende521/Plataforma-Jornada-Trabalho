# Etapa 1 — Base técnica

Data: 10/10/2026. Esta etapa não entrega o sistema de jornada completo.

## Implementado

- Backend Java 17/Spring Boot 3.5.16, com Maven Wrapper 3.3.4/Maven 3.9.11.
- Frontend Angular/CLI 22.2, TypeScript estrito, lockfile e Node 24.19.0.
- GET /api/status para diagnóstico, sem dados pessoais ou detalhes de infraestrutura.
- Tela responsiva inicial com estados de carregamento, sucesso e erro; timeout de 5 segundos na consulta.
- Rotas não públicas exigem autenticação; login HTTP Basic/formulário padrão e conta padrão desabilitados. Não existem contas ou fluxo de login real.
- CSRF mantido habilitado. Usuário sintético é usado apenas em teste; não há credenciais de acesso publicadas.
- Configuração SQL Server/JPA/Flyway com ddl-auto=validate, open-in-view=false, clean desabilitado e credenciais de migration separadas.
- Perfil bootstrap explícito, local e sem banco, para diagnóstico. Não é um modo de produção nem alternativa à persistência.
- Workflow de builds/testes com permissões somente de leitura e sem SQL Server.

## Evidências locais

| Verificação | Resultado | Limite |
|---|---|---|
| Build/package backend com Maven 3.9.11 | Aprovado | Sem validação de schema SQL Server |
| Testes backend | 3 aprovados, 0 falhas/erros | Status público, consulta sem sessão e CSRF; não autenticam contas reais |
| npm ci seguido de npm run build | Aprovado | Bundle da tela inicial, não produto completo |
| npm test -- --watch=false | 4 aprovados | Comunicação HTTP simulada; não teste de navegador E2E |
| npm audit --omit=dev --audit-level=high | Nenhuma vulnerabilidade reportada naquela execução | Não é auditoria completa nem garantia de ausência de vulnerabilidades |
| Maven Wrapper --version | Maven 3.9.11 confirmado | Download inicial depende de internet |
| Inicialização do JAR e requisições HTTP reais | /api/status retornou 200 FOUNDATION; rota de negócio retornou 401 | Smoke test local sem banco, não E2E da interface |

Ambiente: Linux, OpenJDK 17.0.20, Node 24.19.0 e npm 11.9.0. A obtenção de dependências Maven exigiu configuração temporária do proxy deste ambiente, não incluída no repositório. Os comandos publicados são os comandos normais para o ambiente do avaliador.

O primeiro teste backend falhou por indisponibilidade de anexação dinâmica de agente Mockito à JVM. Configurou-se o mock-maker-subclass somente nos recursos de teste; ele não permite mocks de métodos/classes finais ou estáticos. Os testes desta etapa não precisam desses recursos. A suite foi repetida e aprovada, sem desligar CSRF nem pular testes.

## Relação com a avaliação

| Critério | Evidência nesta etapa |
|---|---|
| Interpretação de cenário | Nenhuma funcionalidade falsa de jornada; persistência separada do diagnóstico |
| Organização | Diretórios backend/frontend/docs e configuração por perfil |
| Qualidade de código | DTO de status, TypeScript estrito, feedback de erro e testes |
| Modelagem | Modelo de negócio permanece proposto; entidades e migrations ainda pendentes |
| Decisões técnicas | Stack/versionamento e limites do modo bootstrap documentados |
| Comunicação | README executável com distinção entre implementado e planejado |
| Experiência de uso | Tela inicial com mensagem clara, botão e feedback acessível |
| Boas práticas | Bloqueio por padrão, CSRF, segredos externos e builds automatizados |

## Não concluído

Autenticação de contas reais; persistência SQL Server; migrations/entidades/seed; eventos e cálculo de jornada; idempotência/concorrência; histórico; autorização por recurso; integração temporal JDBC; validação de navegador/dispositivos reais; gestor/RH e correções.

## Evidência de CI

O workflow [Foundation checks — execução 38086503464](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38086503464), associado ao commit `38d74a8`, concluiu com sucesso os jobs backend e frontend. Os jobs repetem builds e testes da base, sem SQL Server. O envio inicial omitiu o lockfile e falhou no setup do frontend; o commit seguinte adicionou o arquivo e a nova execução foi aprovada. A árvore publicada foi comparada com os arquivos locais revisados, incluindo o lockfile.

Próxima etapa: modelagem detalhada de contas/colaboradores, migrations e autenticação, com teste em SQL Server real antes de declarar a persistência pronta.
