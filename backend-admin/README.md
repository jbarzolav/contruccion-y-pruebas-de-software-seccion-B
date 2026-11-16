# PulgaTec — backend administrativo

El panel de PulgaTec está en `/admin/`. Permite consultar usuarios y auditoría, cambiar roles y gestionar listas de revisión con sus ítems. Django lee la base compartida y delega los cambios de rol a Spring.

## Ejecutar

```powershell
py -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:USER_API_BASE_URL = "http://localhost:9000"
$env:DJANGO_DB_ENGINE = "mysql"
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3307"
$env:DB_NAME = "BD_Pulgatec"
$env:DJANGO_DB_USER = "pulgatec_admin_ro"
$env:DJANGO_DB_PASSWORD = "<contraseña desde el entorno/secret manager>"
$env:INTERNAL_ADMIN_API_KEY = "<secreto compartido desde el entorno/secret manager>"
python manage.py migrate
python manage.py createsuperuser
python manage.py runserver 8001
```

Abre [Administración de PulgaTec](http://127.0.0.1:8001/admin/) e ingresa con el superusuario de Django. Para confirmar un cambio de rol, usa una cuenta administradora de PulgaTec. La sesión de Spring abierta para ese cambio se cierra al terminar la solicitud.

## Contrato

- `GET /api/admin/usuarios` lista solo DTOs sin credenciales.
- `PATCH /api/admin/usuarios/{idUsuario}/rol` valida el ADMIN en Spring y delega a `/_internal/admin/usuarios/{idUsuario}/rol`.
- `GET /api/admin/auditoria?page=0&size=20` consulta directamente `registros_auditoria` en MariaDB.

Django guarda sus superusuarios, sesiones y listas de revisión en SQLite. Las cuentas del marketplace y sus roles pertenecen a Spring. Spring Boot es dueño de `usuarios`, `tokens_recuperacion` y `registros_auditoria`; Django usa modelos `managed = False` para leer `usuarios` y `registros_auditoria`. Antes de cada operación, Django valida el Bearer opaco llamando a `/api/auth/me` y exige ADMINISTRADOR. La escritura de rol usa una ruta interna no consumible por los clientes públicos, protegida además por `INTERNAL_ADMIN_API_KEY` y por la autorización de Spring sobre el Bearer original; Spring registra `CAMBIO_ROL` en la misma operación.

El usuario MySQL de Django (`pulgatec_admin_ro`) tiene solo `SELECT` sobre `BD_Pulgatec.usuarios` y `BD_Pulgatec.registros_auditoria`; Spring usa `pulgatec_app` con permisos acotados al esquema para sus escrituras. Las migraciones de Django se ejecutan en SQLite, que es la conexión `default`. La conexión `shared` se usa para consultas. No guardar tokens, claves internas ni credenciales en logs.

Las sesiones Spring siguen en memoria: al reiniciar Spring los tokens opacos dejan de ser válidos; la información de usuarios, productos, recuperación y auditoría sí queda persistida en MySQL. La vigencia/flujo HU25 no cambia.
