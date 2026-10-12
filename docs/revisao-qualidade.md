# Revisão de qualidade e refatoração — 12/10/2026

Base analisada: main f88ffe623c4618a9fbd10a0895f583f5dcf2d921. Escopo: fontes Java/Angular, testes unitários/integrados/E2E, SQL V1, configurações, workflows e scripts CI. Código gerado, dependências instaladas e lockfiles não foram tratados como código autoral a refatorar. Revisão assistida, não aprovação da empresa nem auditoria de segurança completa.

## Critérios e impacto

Baixo: problema localizado de clareza/consistência, sem falha relevante do fluxo demonstrada. Médio: manutenção, testabilidade ou confiabilidade de responsabilidade transversal; deve ser tratado antes de ampliar o módulo. Alto: falha confirmada que compromete fluxo crítico/integridade/segurança e impede avanço. Não foi identificado achado alto confirmado no código atual dentro deste escopo; isso não comprova ausência de vulnerabilidades ou prontidão do produto.

| Critério | Avaliação da base |
|---|---|
| Coesão | Boa separação por funcionalidade; filtro de login reunia adaptação HTTP, contagem de tentativas e escrita de erro |
| Duplicação | JSON Problem Details, envio CSRF, precisão/fuso, CSS e asserção do seed tinham repetição concreta |
| Acoplamento | Domínio sem Spring/JPA é adequado; dependências construídas no filtro dificultavam testar responsabilidades isoladas |
| Complexidade | Não há classe que justifique arquitetura distribuída; há densidade localizada em transições, componentes e testes de percurso |
| Refatoração | Extrações pequenas com consumidores reais; sem repositório genérico, framework de comandos ou novas dependências |
| Responsabilidades | Agregado protege regras locais; serviços HTTP e SQL continuam necessários para identidade, concorrência e persistência |

## Achados classificados

| ID | Local e problema observado | Critérios | Impacto | Ação/estado |
|---|---|---|---|---|
| Q01 | SecurityConfiguration/LoginProtectionFilter escreviam JSON de erro separadamente por concatenação | Duplicação, coesão | Médio | ProblemResponseWriter único com Jackson e teste de escaping/UTF-8; títulos/status públicos preservados |
| Q02 | LoginProtectionFilter controlava HTTP, janela/armazenamento e contagem | Coesão, acoplamento, responsabilidade | Médio | LoginAttemptLimiter independente de Servlet, com Clock injetado; filtro somente adapta HTTP e valida credenciais |
| Q03 | Contagem de tentativas rejeitadas continuava incrementando int | Complexidade/confiabilidade | Baixo | Saturação em 10; evita possibilidade teórica de overflow, sem alegar exploração observada |
| Q04 | Validação de fuso repetida em Employee/WorkSession e regra 100 ns repetida em evento/agregado | Duplicação, responsabilidade | Médio | ReferenceTimeZones e UtcInstants compartilhados, sem dependência de banco/framework no domínio |
| Q05 | WorkSession.record misturava decisão da máquina de estados com atualização de intervalos/eventos | Coesão, complexidade | Médio | WorkState.nextFor contém transições; agregado continua responsável pela atualização coerente e duração |
| Q06 | AppUser chamava matches sobre hash possivelmente nulo e recompilava regex na construção | Responsabilidade/consistência | Baixo | Hash nulo rejeitado por IllegalArgumentException; Pattern estático e teste específico; formato/email existente não ampliados |
| Q07 | AuthService repetia obter CSRF, POST, header e timeout | Duplicação | Médio | postWithCsrf e REQUEST_TIMEOUT_MS; preservados token fresco, renovação após login, ausência de retry e confirmação de logout |
| Q08 | App acumulava apresentação do acesso e política de tradução de erros HTTP | Coesão/acoplamento | Médio | authErrorMessage extraído e testado sem efeitos; App conserva coordenação/formulário. Separar páginas quando houver outras telas, não antes por convenção |
| Q09 | .shell declarava max-width duas vezes, com a segunda sobrescrevendo a primeira | Duplicação/clareza | Baixo | Uma declaração com largura efetiva original de 42rem; não há redesign |
| Q10 | TypeScript/templates não ativavam verificações estritas permanentes | Qualidade/prevenção | Médio | strict/strictTemplates ativados; build e testes locais aprovados. Não introduzir casts para silenciar erros |
| Q11 | Script CI repetia contagem de seed no readiness e após reinício | Duplicação/manutenção | Baixo | assert_evaluation_seed reutilizada; mantidos erro, contagens exatas e espera do commit |
| Q12 | Testes HTTP recriavam ObjectMapper e repetiam leitura de resposta | Duplicação/acoplamento | Baixo | Mapper configurado injetado e responseJson; mantidos CSRF/session fixation reais |
| Q13 | Limiter varre até 4096 janelas sob lock e é local à instância | Complexidade/acoplamento operacional | Médio | Mantido com limite/testes; sem evidência de gargalo. Antes de múltiplas instâncias/proxy, medir e redesenhar; não alegar que extração resolve escala |
| Q14 | E2E tem um percurso longo por viewport com muitas verificações | Complexidade/testabilidade | Médio | Mantido para preservar ciclo real da sessão; oportunidade futura de test.step/helpers por fase, sem apagar cobertura ou trocar por mocks |
| Q15 | Proxy CI usa leitura síncrona de arquivos e tabela de MIME local | Complexidade/acoplamento | Baixo | Mantido: fixture loopback, não servidor de produção. Redesenhar somente se seu papel/volume mudar |
| Q16 | SQL/email têm validação mínima; falta teste que comprove efeito da collation real na normalização do CHECK | Responsabilidade/cobertura | Médio | Pendente de teste SQL específico; aplicação normaliza email. Não alterar V1 já aplicada nem afirmar que CHECK garante lowercase em qualquer collation; eventual ajuste exige V2 |

Q13–Q16 não são funcionalidades novas nesta refatoração. Ausência de persistência/API de jornada, aprovação manual, autorização por recurso, recibos e interface completa permanece no backlog; não foi classificada como defeito de qualidade do agregado inicial por si só.

## Responsabilidade após refatoração

| Componente | Responsabilidade e limite |
|---|---|
| AppUser / Employee | Invariantes e mapeamento de contas, não transporte HTTP ou autorização |
| AppUserRepository / EmployeeRepository | Acesso JPA; EntityGraph para identidade, sem regras da jornada |
| AccountDetailsService / AccountPrincipal | Carregar conta e construir snapshot de identidade/autoridade; sem expor entidade JPA |
| EvaluationAccounts | Seed fictício transacional em perfil explícito; não administração pública de contas |
| SecurityConfiguration | Compor política e dependências; filtro construído somente na cadeia de segurança, não registrado duas vezes como servlet |
| LoginProtectionFilter | Selecionar requisição, adaptar limiter/credenciais e retornar erro HTTP |
| LoginAttemptLimiter | Janela, limite e armazenamento limitado por endereço; não conhece cookies, CSRF ou Servlet |
| ProblemResponseWriter | Serializar resposta de falha dos filtros; não decide autenticação ou mensagem de interface |
| AuthController / StatusController | DTOs HTTP mínimos; status não equivale a readiness do seed/banco |
| BootstrapAccounts / WorkJourneyApplication | Perfil diagnóstico sem credenciais e inicialização, respectivamente |
| WorkState / WorkEventType | Estados, tipos e transições online; correção manual não é evento online falso |
| WorkSession / WorkEvent | Estado/duração coerentes e eventos imutáveis; não autorização ou lock entre requisições |
| ReferenceTimeZones / UtcInstants | Invariantes temporais compartilhadas, sem dependência de JPA/HTTP |
| AuthService | Protocolo HTTP/sessão/CSRF e identidade confirmada; sem mensagem de UI, retry automático ou token persistido |
| App / authErrorMessage | Formulário e coordenação da tela / tradução de falhas para linguagem de uso |
| Scripts/workflows/testes | Ambiente descartável e evidências; TLS de CI não comprova certificado de produção |

## Contratos preservados e alterações intencionais

Rotas, status/títulos públicos dos erros, campos de identidade, BCrypt custo 12, sessão/CSRF e limites de login preservados. SameSite/HttpOnly/Secure não foram relaxados. Não foram alteradas migrations, bibliotecas ou regras de jornada. Exceções internas de transição passaram a ter mensagem uniforme; não eram contrato HTTP publicado. Hash nulo agora produz erro de validação previsível, em vez de NullPointerException. JSON de erros usa serializer e UTF-8 explícito; escaping deixa de depender de concatenação.

Oportunidades não adotadas: microserviços, Repository genérico, interfaces para todas as classes, serviço genérico de operações no componente ou nova biblioteca de rate limiting. Esses elementos não resolvem uma necessidade comprovada desta primeira versão e aumentariam o acoplamento/complexidade.

## Verificação desta versão

- Local: npm run build aprovado com strict/strictTemplates; npm test -- --watch=false: 18 testes aprovados (14 existentes + 4 de feedback), 3 arquivos.
- Local: git diff --check, bash -n scripts/ci/validate-auth.sh e node --check scripts/ci/https-proxy.mjs aprovados no diretório raiz do projeto.
- Backend local: mvnw verify bloqueado antes de compilação pelo parent Spring Boot ausente no cache e falha DNS do Maven Central; não declarar testes Java aprovados por execuções antigas.
- Backend esperado nesta refatoração: 29 testes padrão (24 existentes + capacidade do limiter, serialização Problem Details, limite via HTTP/CSRF, hash/fuso nulos e matriz de transições). SQL Server opt-in: 2 testes separados. Browser: 2 percursos de autenticação separados.
- Próxima verificação: CI em branch de refatoração/PR rascunho, sem merge automático. Registrar head testado, runs e logs após resultado; corrigir antes de integrar se houver falhas.

Esta revisão sustenta organização, qualidade, modelagem, decisões, comunicação e boas práticas do desafio. Experiência de uso foi preservada no login, não certificada; interpretação do cenário e todos os critérios globais ainda dependem da jornada completa e do checklist de dispositivos/contextos.
