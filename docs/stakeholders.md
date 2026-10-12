# Stakeholders e contexto de uso

Status: levantamento derivado do enunciado, não pesquisa de campo. Não houve entrevistas, observação de trabalho ou aprovação por colaboradores/RH. Necessidades inferidas são hipóteses a validar. Não criar personas com idades, cargos específicos ou hábitos inventados.

## Mapa de interessados

| ID | Interessado | Relação com o produto | Interesse/necessidade | Participação e limite |
|---|---|---|---|---|
| ST01 | Colaboradores Brasil/Europa | Usuários diretos P0 | Registrar e consultar a própria jornada com clareza de horários e confirmação | Validar linguagem, rotina, erros e uso no celular; sem acesso a colegas |
| ST02 | Gestores | Usuários diretos P1 | Acompanhar colaboradores vinculados, sem depender de mensagens dispersas | Validar vínculo de equipe e consultas; não marcar por colaboradores |
| ST03 | RH | Aprovador mínimo P0 planejado; consulta geral P1 | Decidir saída esquecida no escopo autorizado; consulta operacional ampla futura | Validar regras e responsabilidade de aprovação; não editar originais nem autoaprovar |
| ST04 | Diretoria | Patrocinadora no cenário fictício | Centralização e apoio ao crescimento internacional | Validar objetivos e prioridades; não implica perfil administrativo na aplicação |
| ST05 | Equipe técnica/manutenção | Interessada operacional, inferida | Execução reproduzível, evolução e segurança | Validar configurações, permissões e documentação; não implica painel de administração |
| ST06 | Técnico avaliador da empresa | Avaliador externo da entrega | Executar a solução e verificar os critérios do desafio | Código, evidências e contas fictícias; sem acesso privilegiado a dados reais |
| ST07 | Desenvolvedora | Responsável pela entrega | Implementar escopo viável e comunicar decisões | Julia desenvolve e registra hipóteses; revisão assistida não substitui aprovação da empresa |

Interesse não equivale a autorização. A aplicação implementa somente autoridade EMPLOYEE nesta etapa; gestor/RH continuam planejados. Não há interface da diretoria nem administração pública de contas. ST05 é uma necessidade operacional inferida, não equipe identificada em entrevista.

## Contextos de uso a considerar

| Contexto | Possível dificuldade (hipótese) | Resposta de projeto | Rastreabilidade |
|---|---|---|---|
| Trabalho no celular | Tela pequena, toque impreciso, interrupção | Ações claras, alvos confortáveis, estado visível e retorno ao contexto | RNF01/RNF12; HU03–HU07 |
| Trabalho remoto | Conexão instável e resposta perdida | Não anunciar marcação sem confirmação; reconciliar tentativa | RF14/RNF05; HU14 |
| Viagem entre países | Confundir fuso de visualização com fuso do registro | Mostrar fuso e data de referência; não reclassificar histórico | RF11/RNF07; HU11 |
| Jornada noturna | Entender um dia como limite obrigatório | Mostrar datas completas dos eventos que atravessam meia-noite | RF12; HU12 |
| Uso por teclado/tecnologia assistiva | Perder foco, não perceber erro ou atualização | Labels, ordem de foco, mensagens textuais e testes específicos | RNF12; plano UX |
| Fechamento operacional | Confundir total provisório com consolidado | Separar registros encerrados e pendências; sem cálculo legal implícito | RF10/RF13; HU10/HU13 |

Remoto, viajante e usuário de celular são contextos que podem se sobrepor; não representam três perfis de autorização ou grupos mutuamente exclusivos.

## Perguntas para validação com interessados

1. ST01/ST03: quais pausas existem e quais devem ser descontadas? RN04 é hipótese do MVP, não regra trabalhista.
2. ST01/ST03: encerrar diretamente durante pausa atende à rotina? RN05 revisada em 11/10/2026 permite saída sem retorno artificial e desconta a pausa final; decisão assumida, ainda sem validação dos interessados.
3. ST01/ST03: quem atende pedidos de saída esquecida e em quanto tempo? Recorte mínimo RF17/RF18 antecipado para P0 em 11/10/2026 pela desenvolvedora; ainda não implementado nem validado com RH. Até aprovação, nova entrada segue bloqueada.
4. ST01/ST03: como escolher/alterar o fuso de referência e explicar a data de início no resumo mensal?
5. ST01: português atende aos usuários da Europa? Inglês/i18n é hipótese de evolução, não funcionalidade entregue.
6. ST02/ST03: como determinar equipes, escopo de consulta e responsabilidade pela aprovação de correções?
7. ST01/ST05: qual canal interno de suporte/provisionamento pode ser mostrado? Não publicar contato inventado.
8. ST04/ST06: qual prazo, recorte mínimo e ambiente de execução serão usados na avaliação?

## Gestão das decisões

Registrar resposta, origem, data, regra afetada e impacto em histórias/testes. Sem resposta, manter decisão como hipótese e limitação explícita. Questões 2 e 3 devem ser revistas antes de codificar o fluxo de jornada que depende delas. Não alterar regras silenciosamente para resolver um problema visual.

Este mapa orienta participação e validação; não existe matriz de poder formal, RACI corporativo ou aprovação dos interessados, pois essa informação não foi fornecida. Requisitos canônicos: [requisitos.md](requisitos.md); regras: [proposta.md](proposta.md); experiência: [experiencia-usuario.md](experiencia-usuario.md).
