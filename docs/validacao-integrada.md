# Validação integrada de autenticação

## Ambiente e execução

O workflow `.github/workflows/auth-integration.yml` executa SQL Server 2022 CU20 Developer em container x86-64 no runner Ubuntu 24.04, Java 17, Node 24.19 e Chromium via Playwright 1.64.0. O digest da imagem e a versão SQL são registrados nos logs. Banco exclusivo work_journey_ci, sem dados reais, removido ao terminar. A licença Developer é usada somente para desenvolvimento/testes.

Comando do workflow: `bash scripts/ci/validate-auth.sh`. O script exige GitHub Actions, gera quatro senhas aleatórias, mascara segredos nos logs, aplica migrations desde banco vazio e executa SqlServerIT. Esse teste de schema usa a conta de migration; depois o servidor inicia com outra conta, que recebe apenas SELECT/INSERT nas tabelas app_user/employee, sem DDL. Credencial sa é exclusiva do provisionamento temporário, não da aplicação.

O backend inicia duas vezes com evaluation para verificar que o seed não duplica contas/colaboradores. Backend e Angular de produção são servidos na mesma origem HTTPS por proxy Node de teste. Cookie Secure continua habilitado. Timeout de sessão reduzido para um minuto somente pelo processo de CI; padrão da aplicação permanece 30 minutos.

Há duas exceções locais de certificado: JDBC encrypt=true/trustServerCertificate=true para o certificado do container e Playwright ignoreHTTPSErrors para o HTTPS efêmero autoassinado. Elas pertencem ao harness de CI; não provam validação de cadeia/certificado de produção e não alteram as configurações seguras padrão do projeto.

## Cenários automatizados

| Camada | Verificações |
|---|---|
| SQL Server | Flyway desde banco vazio; Hibernate validate; UUID, Unicode e fuso IANA persistidos/recarregados; email único e FK rejeitando colaborador órfão |
| Inicialização | Seed sem duplicação após reinício; servidor usando conta diferente da migration |
| HTTPS/Chromium | Formulário anônimo, erro genérico de senha, login real, identidade própria e persistência de sessão após reload |
| Sessão | Cookie Secure/HttpOnly/SameSite=Lax; logout revoga cookie antigo; expiração real por inatividade |
| CSRF | Login/logout sem token rejeitados; token anterior ao login rejeitado no logout; fluxo legítimo renovando token |
| Duas contas | Contextos de navegador separados, identidades Brasil/Europa distintas, nenhuma troca de identidade entre sessões |
| Viewports | Desktop 1280 px e Chromium 360 px; sem overflow horizontal; capturas de login e de conta autenticada com dados fictícios |

Não há mocks de API, UserDetailsService ou banco nesses testes E2E. São dois percursos completos, um por viewport; as verificações internas não devem ser anunciadas como dezenas de testes independentes. Desktop e mobile usam Chromium: viewport móvel não equivale a teste em iPhone/Safari real. Screenshots e HTML report são artefatos temporários por sete dias. Traces/vídeos estão desabilitados para não arquivar requisições com credenciais.

## Estado da evidência

Execução [38090975052](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38090975052), commit `12bc48aede8a373ced66b4e8f862130fca648f7e`, em 10/10/2026: **job authentication concluído com sucesso**. Logs conferidos: Flyway aplicou V1 em schema vazio; SqlServerIT executou 2 testes sem falhas/erros/ignorados; suite backend executou 12 testes sem falhas; Playwright executou 2 percursos aprovados (desktop e mobile-360) em 2,3 minutos. Build de produção aprovado. O workflow [Foundation checks 38090974996](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38090974996) também aprovou backend/frontend, incluindo os 14 testes frontend.

SQL Server observado: 2022 RTM-CU20, versão 16.0.4205.1 X64. Imagem observada: `mcr.microsoft.com/mssql/server@sha256:7c29dfbac885ad7519e219c7fe4aee0e67283e21a10e9c252d13b0fbde1866f8`. Tag usada é fixa em CU20, mas o digest acima identifica precisamente esta execução.

As quatro capturas foram revisadas visualmente: labels, botões e textos legíveis, sem corte horizontal em 360 px. Evidências preservadas neste repositório:

- [Login mobile](evidencias/autenticacao/mobile-login.png) e [conta mobile](evidencias/autenticacao/mobile-autenticado.png).
- [Login desktop](evidencias/autenticacao/desktop-login.png) e [conta desktop](evidencias/autenticacao/desktop-autenticado.png).

Resultado: RF01/RF02 aprovados no ambiente descrito; T01/T23 aprovados para o fluxo implementado. T02/T18/T20 têm evidências parciais porque autorização de jornadas, restante do schema e acessibilidade completa continuam fora desta etapa. A execução fez checkout limpo e seguiu o script de CI; isso não comprova os passos locais da IDE/Windows nem prontidão de produção.

Não valida registro/cálculo de jornada, histórico, autorização de recursos futuros, legislação, recuperação de senha, MFA/SSO, acessibilidade completa, certificados de produção, escala horizontal ou Safari/dispositivos físicos. Os requisitos gerais do desafio permanecem parcialmente implementados.

## Referências

- [Microsoft — containers SQL Server](https://learn.microsoft.com/en-us/sql/linux/quickstart-install-connect-docker?view=sql-server-ver16)
- [Microsoft — versão CU20 do SQL Server 2022](https://learn.microsoft.com/en-us/troubleshoot/sql/releases/sqlserver-2022/cumulativeupdate20)
- [Playwright — execução em CI](https://playwright.dev/docs/ci)
