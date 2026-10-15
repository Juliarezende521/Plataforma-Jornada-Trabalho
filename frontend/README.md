# Frontend

Interface Angular da plataforma de jornada. Inclui autenticação, registro de marcações, seleção da modalidade de trabalho, histórico próprio e consultas do RH.

Para executar a aplicação completa, siga o [README principal](../README.md). O script `scripts/local/frontend.sh` instala as dependências quando necessário e inicia o servidor local com HTTPS.

## Comandos isolados

```bash
npm ci
npm run build
npm test -- --watch=false
```

Os testes de unidade usam Vitest. Os percursos de navegador ficam em `e2e/` e são executados com Playwright no workflow de integração.
