# Backend-user-api — persistencia y perfiles

## H2 y pruebas

El perfil por defecto conserva H2 en memoria. Maven activa `test` durante `mvn test`, usando una base H2 aislada; la suite no requiere MySQL.

## MySQL de integración/desarrollo

La base `BD_Pulgatec` debe crearse manualmente antes del primer arranque. No se elimina ni recrea. Configurar externamente:

```powershell
$env:SPRING_PROFILES_ACTIVE = "mysql"
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3307"
$env:DB_NAME = "BD_Pulgatec"
$env:DB_USER = "<usuario de aplicación>"
$env:DB_PASSWORD = "<contraseña desde el entorno/secret manager>"
```

Arranque desde este directorio: `mvn spring-boot:run`. El perfil `mysql` usa `ddl-auto=update`, sin `create`/`create-drop`, y desactiva la cuenta demo conocida. Solo ejecutar contra un esquema confirmado vacío o revisado; Hibernate creará/adaptará tablas según las entidades actuales. El administrador inicial es opcional y solo se crea si se proporcionan `APP_ADMIN_CORREO` y `APP_ADMIN_PASSWORD`; el alta es idempotente.

Tablas esperadas: `usuarios`, `productos`, `registros_auditoria`, `tokens_recuperacion`, `carritos`, `items_carrito`, `pedidos`, `detalles_pedido`, `vendedores_plan`, `perfiles_vendedor`. Relaciones FK: recuperación → usuario; ítem carrito → carrito/producto; detalle pedido → pedido/producto. `clienteId` y `vendedorId` son columnas escalares sin relación JPA declarada. HU 28 añade las columnas personales `nombre` y `telefono` (nullable) a `usuarios`; HU 29 crea `perfiles_vendedor` con FK única `usuario_id` (1:1 con usuario, un solo perfil por vendedor) sin relación JPA declarada, siguiendo el mismo estilo.

La API pública Spring no expone administración de usuarios ni consulta de auditoría (`/api/admin/usuarios` y `/api/admin/auditoria`). Django valida sesiones con `/api/auth/me`, consulta directamente las tablas compartidas y, para conservar la regla transaccional de rol y el evento `CAMBIO_ROL`, invoca la ruta interna `/_internal/admin/usuarios/{id}/rol`. Configurar `ADMIN_INTERNAL_API_KEY` fuera del repositorio con el mismo secreto que `INTERNAL_ADMIN_API_KEY` de Django; la ruta interna exige ese secreto y el Bearer opaco de un ADMINISTRADOR.

El perfil usa MariaDB Connector/J y `MariaDBDialect`. El usuario de aplicación tiene `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CREATE`, `ALTER`, `INDEX` y `REFERENCES` solo en `BD_Pulgatec.*`; no tiene `DROP` ni privilegios globales.

Propiedad del esquema compartido: Spring es dueño de usuarios, roles, recuperación y auditoría, además del resto de entidades. Django lee usuarios/auditoría mediante modelos no gestionados y no ejecuta migraciones sobre esas tablas. Sesiones Bearer opacas permanecen en memoria; reiniciar Spring invalida sesiones activas, aunque los demás registros persisten. HU20–HU25 mantienen sus servicios y contratos; HU26+ no forman parte de esta configuración.
