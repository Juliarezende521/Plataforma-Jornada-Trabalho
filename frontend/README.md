# Frontend — autenticação

Angular CLI 22.2.2, com formulário de login, restauração de sessão, consulta de identidade e logout. Jornada/histórico ainda não implementados. Não há cadastro público, login fictício ou credenciais embutidas.

## Execução

Com Node 24.19.0:

```bash
npm ci
npm start
```

Abra http://localhost:4200. /api é encaminhado ao backend local. Bootstrap permite renderizar a tela e testar erro de login, mas não tem contas. Login real exige SQL Server e ambiente HTTPS que preserve o cookie Secure. Consulte [README principal](../README.md), [contrato backend](../docs/autenticacao.md) e [registro da etapa](../docs/login-angular.md).

## Build e testes

```bash
npm run build
npm test -- --watch=false
```

Testes de componentes/serviço simulam HTTP, incluindo token CSRF, renovação, timeout e logout. Separadamente, Playwright executa dois percursos reais em Chromium desktop/360 px na CI com SQL Server e HTTPS. Os dois foram aprovados na execução 38090975052. Consulte [validação integrada](../docs/validacao-integrada.md) para evidências, certificados locais de teste e limitações. O harness é iniciado pelo workflow auth-integration, não por npm test.
