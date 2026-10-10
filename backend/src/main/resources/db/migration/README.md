# Migrations

V1 cria dbo.app_user e dbo.employee, com unicidade de email, vínculo único de colaborador e FK. Nomes usam nvarchar para caracteres internacionais; fuso IANA é validado em Java. Seed não contém senhas no SQL: é executado somente pelo perfil evaluation. A migration ainda não foi executada neste ambiente contra SQL Server real.

Usar arquivos V1__descricao.sql, V2__descricao.sql etc. Não editar scripts já aplicados. Validar criação desde SQL Server vazio antes de considerar a persistência pronta.
