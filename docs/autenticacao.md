# Etapa 2 — Contas, colaboradores e autenticação

Registro da implementação backend. A interface foi implementada na etapa seguinte, descrita em [login-angular.md](login-angular.md). Migrations, persistência e fluxo integrado de autenticação foram depois aprovados em SQL Server real/Chromium; ver [validacao-integrada.md](validacao-integrada.md).

## Revisão de aderência

RF01/RF02: backend implementa login/logout por sessão, aprovados com interface e SQL Server real na CI 38090975052. RNF03: identidade deriva do principal autenticado, não de ID recebido; isolamento de jornadas continua pendente. RNF04: BCrypt custo 12, CSRF inclusive no login/logout, troca de ID de sessão e resposta genérica. RNF02/RNF09: migration/entidades/seed validados em checkout limpo da CI; schema de jornada e reprodução local do avaliador permanecem pendentes.

Conta de acesso e colaborador são entidades diferentes, em relação 1:1. Não existe cadastro público nem endpoint administrativo. Usuário sem colaborador ou inativo não autentica. A única autoridade implementada é EMPLOYEE; gestor/RH permanecem P1. Email é normalizado com strip e lowercase Locale.ROOT (decisão de identificador case-insensitive). UUIDs são atribuídos em Java. Nome Unicode e fuso IANA são preservados, sem inferir país pelo fuso.

## Contrato HTTP implementado

| Rota | Entrada | Resultado |
|---|---|---|
| GET /api/auth/csrf | Cookie da sessão, se existente | 200: headerName e token; cria sessão quando necessário |
| POST /api/auth/login | application/x-www-form-urlencoded: email/password e header CSRF | 204; 401 genérico; 403 CSRF; 429 limite |
| GET /api/me | Cookie autenticado | 200: userId, employeeId, email, name, referenceTimeZoneId; sem hash |
| POST /api/auth/logout | Cookie e header CSRF atualizado | 204, invalida sessão; CSRF incorreto: 403 |

Cliente deve obter token, preservar cookie, autenticar, obter **novo** token e então realizar mutações. Tokens são fornecidos por endpoint e mantidos em memória; não há cookie XSRF-TOKEN automático nesta etapa. O interceptor padrão Angular sozinho não implementa este contrato. Login usa o filtro padrão de Spring Security, não controller manual que esqueça persistência do SecurityContext ou proteção contra session fixation. Não há redirect no sucesso/erro/logout. GET não faz logout. Respostas de segurança têm Problem Details; correlação de erros ainda pendente.

## Proteções e limites

- Sessão HttpOnly, Secure e SameSite=Lax; timeout por inatividade de 30 minutos. Bootstrap é local, sem banco e sem contas. Nenhum perfil de SQL Server desabilita Secure: para testar login no navegador é preciso servir via HTTPS ou definir uma configuração local explícita, não publicar cookie inseguro.
- Senhas não passam por trim; BCrypt aceita até 72 bytes UTF-8. Login rejeita valores além do limite, email acima de 254 caracteres e senha ausente/vazia. Formulário Tomcat limitado a 8 KB/10 parâmetros; esses limites precisam de smoke test HTTP no contêiner real.
- Limite de 10 tentativas por endereço remoto por janela de 60 segundos; sucesso também conta. Estado em memória, no máximo 4096 endereços ativos, demais rejeitados até liberar espaço. Headers X-Forwarded-For não são confiados. NAT pode reunir usuários e proxy pode concentrar todas as tentativas: rever configuração de proxy confiável e limiter distribuído antes de produção. Reinício zera limite.
- Principal guarda apenas snapshot imutável da identidade, não entidade JPA; credencial é apagada pelo fluxo de autenticação. Alterar active/nome/fuso no banco não revoga/atualiza sessões já emitidas. Não há administração de contas; revogação imediata deverá ser implementada antes de oferecer esse fluxo.
- Não existem recuperação de senha, MFA, SSO ou garantia de conformidade trabalhista/legal. Seed usa domínio reservado example.invalid e dados fictícios. Nenhum segredo deve aparecer em logs/Git.

## SQL Server e contas de avaliação

Usar banco exclusivo e vazio para o projeto, com TLS válido. Disponibilizar DB_URL, DB_USERNAME, DB_PASSWORD, DB_MIGRATION_USERNAME e DB_MIGRATION_PASSWORD no processo (ver .env.example). A conta Flyway precisa criar objetos em dbo e gerenciar flyway_schema_history; a conta de execução precisa SELECT/INSERT nas tabelas de contas para seed, sem DDL. A fatia de jornada na PR #3 define SELECT/INSERT/UPDATE em work_session e somente SELECT/INSERT em work_event/idempotency_request; conferir [grants e evidências](marcacoes-idempotencia.md). Não usar sa, nem banco de terceiros. Provisionamento da CI é descartável e não configura automaticamente o computador da avaliadora/desenvolvedora.

No diretório backend, com variáveis definidas:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=evaluation
```

Definir EVALUATION_PASSWORD externa com pelo menos 12 caracteres e no máximo 72 bytes UTF-8. Seed transacional cria brasil@example.invalid (America/Sao_Paulo) e europa@example.invalid (Europe/Amsterdam) apenas se ausentes. Mesma senha local de avaliação para ambas; nunca publicar seu valor. Reiniciar não altera senhas existentes. Não habilitar evaluation em produção; não executar seed simultaneamente em múltiplas instâncias. Sem esse perfil não são criadas contas.

Teste opt-in, contra banco de teste real, com DB_* configuradas:

```bash
./mvnw -Dtest=SqlServerIT test
```

Esse teste inicializa Flyway e Hibernate validate, verifica persistência/releitura de conta com nome Unicode/fuso IANA e rejeição de email duplicado/colaborador órfão. Inserts são revertidos, mas a migration permanece. Não é executado na suite padrão por usar sufixo IT; seus dois testes foram aprovados contra SQL Server na CI 38090975052. Seed repetido, reinício e login real foram validados separadamente pelo script de CI e Playwright.

## Evidências e pendências

Execução local de ./mvnw -o --batch-mode verify em 10/10/2026: **12 testes aprovados, zero falhas/erros/ignorados; build e empacotamento aprovados**. Testes padrão cobrem status/proteção, modelo, principal de conta inativa/sem colaborador, login válido, senha errada/conta inexistente, troca de sessão, CSRF real obtido por endpoint/renovação, logout e limite por endereço/expiração. O serviço de contas é simulado nos testes HTTP, não o filtro de senha ou o PasswordEncoder. O teste de integração opt-in não faz parte desses 12; não há evidência SQL Server nem CI desta etapa nesta contagem.

Não declarar autenticação ponta a ponta ou persistência prontas sem: executar SqlServerIT; validar constraints/seed e privilégios; verificar integração Angular/backend em navegador, cookies e expiração reais; testar duas identidades sem vazamento; repetir instruções em clone limpo. Frontend/status continuam honestamente indicando jornada indisponível.

Esta etapa contribui para modelagem, organização por domínio, decisões documentadas, testes e segurança. Experiência de uso da autenticação ainda depende da próxima interface; aprovação do desafio depende do produto completo, não apenas destes testes.
