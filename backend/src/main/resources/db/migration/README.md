# Migrations

V1 cria dbo.app_user e dbo.employee, com unicidade de email, vínculo único de colaborador e FK. Nomes usam nvarchar para caracteres internacionais; fuso IANA é validado em Java. Seed não contém senhas no SQL: é executado somente pelo perfil evaluation. A migration foi aplicada em SQL Server 2022 CU20 vazio, com Hibernate validate e testes de integração aprovados na execução 38090975052. Ver docs/validacao-integrada.md.

Usar arquivos V1__descricao.sql, V2__descricao.sql etc. Não editar scripts já aplicados. Validar criação desde SQL Server vazio antes de considerar a persistência pronta.

V2 adiciona jornadas/eventos online, UTC datetimeoffset(7), autoria composta, sequência única e índice de uma jornada aberta por colaborador. Migration/testes inicialmente escritos em 13/10/2026; status e evidências em docs/persistencia-jornada.md. Não inclui JPA de jornada, idempotência ou regularização.
