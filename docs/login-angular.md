# Etapa 3 — Login no Angular

## Aderência e estrutura

RF01/RF02: tela de acesso, consulta de identidade e logout; cadastro público não implementado. RNF04: sessão no servidor, CSRF por endpoint e renovação após login. RNF01/RNF12: formulário fluido, labels, autocomplete, teclado com foco visível, erros vinculados aos campos e feedback anunciado. Validação manual de navegador/celular continua pendente. A implementação usa Reactive Forms tipados, AuthService separado da apresentação e signals para estado, sem novas dependências.

## Fluxo implementado

Ao abrir: GET /api/me. 200 mostra identidade; 401 mostra formulário; falha de rede/5xx/timeout mostra indisponibilidade e ação para verificar novamente. Login: GET /api/auth/csrf → POST /api/auth/login em form-urlencoded → GET /api/auth/csrf para renovar → GET /api/me. Só exibe identidade após confirmação pela API. Logout: obtém token atualizado → POST /api/auth/logout; só limpa identidade após sucesso. Uma falha de saída mantém o estado até a pessoa verificar a sessão.

Credenciais, token e identidade não são gravados em localStorage/sessionStorage. Cookie de sessão é gerido pelo navegador em origem única; Angular usa URLs relativas /api. Senha preserva espaços e caracteres no envio e é limpa do formulário ao terminar a tentativa. Email é validado e aparado; servidor mantém autoridade sobre validação/autorização. Limite de senha acompanha 72 bytes UTF-8 do backend sem truncar silenciosamente.

Requisições têm timeout de 8 segundos. Botões/campos bloqueados durante operação impedem envios duplicados na tela. Não há retry automático de POST nem reenvio silencioso após 403. Erros 401 de login são genéricos; 403 orienta verificar sessão; 429 orienta aguardar; falha incerta orienta consultar sessão antes de repetir. Mensagens vindas do backend não são injetadas como HTML. Nenhuma senha/token é registrada em log.

A ação Verificar sessão consulta o servidor e recupera estado inclusive após uma resposta perdida. Expiração é detectada na abertura/consulta de sessão; não existe polling ou contagem local que substitua o timeout do servidor. Futuras APIs de jornada precisarão de tratamento central de 401/403 e envio de CSRF, ainda não implementado. Não há guards de rotas porque ainda não existem páginas protegidas separadas; autorização continua no servidor.

## Execução e limites

Mesmos comandos do README: backend bootstrap e npm start permitem carregar a interface, consultar sessão e observar rejeição de login, sem SQL Server/contas. Para login real, executar backend com SQL Server e contas de avaliação, e disponibilizar interface/API na mesma origem HTTPS. Cookie Secure permanece habilitado fora de bootstrap; não considerar um teste em HTTP com sessão não preservada como prova de falha da autenticação. O proxy local é configuração de desenvolvimento, não publicação.

Não há dados falsos de login em modo de execução, botão de bypass ou senha demonstrativa embutida. Conta conectada exibe apenas nome/email/fuso retornados pela API e informa que jornada/histórico ainda estão indisponíveis.

## Evidências

Suite frontend: 14 testes sobre inicialização, formulário, limite UTF-8, bloqueio durante pedido, limpeza da senha, login/logout, recuperação, 429, encoding, fluxo/renovação CSRF, ausência de reenvio, sessão expirada, indisponibilidade e timeout. HttpTestingController simula HTTP; não é teste de navegador nem prova de cookie Secure/HttpOnly.

Em 10/10/2026, npm run build e npm test -- --watch=false concluíram com sucesso: **14 testes aprovados, 2 arquivos de teste**, bundle inicial de aproximadamente 278 KB (74 KB estimados em transferência). git diff --check também aprovado. CI backend da etapa anterior (commit bfb5cad) concluiu com sucesso: [execução 38087132126](https://github.com/Juliarezende521/work-journey-platform/actions/runs/38087132126). Essa execução não valida as alterações desta etapa. Browser Chromium não está instalado neste ambiente; a revisão visual e funcional real ainda não foi executada.

Pendências de aceite: fluxo completo com SQL Server/seed; HTTPS/cookies/renovação e expiração reais; desktop e viewport 360 px; uso de teclado/leitor de tela; falha/perda de resposta real e duas contas sem vazamento; clone limpo. Essas pendências impedem declarar RF01/RF02/RNF01/RNF04 completamente aceitos.
