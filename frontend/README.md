# Frontend — base técnica

Interface Angular gerada com CLI 22.2.2 e adaptada para a etapa FOUNDATION. Não é a interface final de registro de jornada. O botão de diagnóstico consulta /api/status por proxy local e não comprova conexão com SQL Server.

## Execução

Com Node 24.19.0, execute neste diretório:

```bash
npm ci
npm start
```

Acesse http://localhost:4200. Para verificar conexão, o backend precisa estar iniciado em bootstrap, conforme o [README principal](../README.md).

## Verificação

```bash
npm run build
npm test -- --watch=false
```

Os testes verificam renderização, comunicação simulada por HttpTestingController e tratamento de erro. Não são testes end-to-end de navegador nem validação de jornada. Nenhum framework E2E está configurado nesta etapa.
