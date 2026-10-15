# Migrations do banco

Flyway é a fonte de evolução do schema. Migrations já aplicadas não devem ser editadas; qualquer mudança deve receber uma nova versão e ser validada desde um SQL Server vazio.

| Versão | Conteúdo |
|---|---|
| V1 | Contas e colaboradores |
| V2 | Jornadas e eventos |
| V3 | Recibos idempotentes |
| V4 | Fuso IANA registrado no evento |
| V5 | Permissão de leitura do RH |
| V6 | Modalidade da marcação: escritório, remoto ou viagem |

O Hibernate usa `ddl-auto=validate`: ele confere o mapeamento, mas não cria nem atualiza o schema. O perfil de avaliação provisiona contas fictícias depois que as migrations foram aplicadas.
