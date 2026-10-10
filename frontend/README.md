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

Testes de componentes/serviço simulam HTTP, incluindo token CSRF, renovação, timeout e logout. Não comprovam SQL Server nem cookies de navegador. Nenhum framework E2E está configurado.
