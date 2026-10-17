# PulgaTec — backend administrativo

API Django + Django REST Framework propietaria de las interfaces/consultas administrativas. Valida la sesión opaca con Spring, consulta MariaDB compartida y delega únicamente la mutación de rol a una ruta interna Spring que conserva las reglas de negocio y la auditoría.

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
python manage.py runserver 8001
```

El frontend debe configurar `VITE_ADMIN_API_URL=http://localhost:8001`.

## Contrato

- `GET /api/admin/usuarios` lista solo DTOs sin credenciales.
- `PATCH /api/admin/usuarios/{idUsuario}/rol` valida el ADMIN en Spring y delega a `/_internal/admin/usuarios/{idUsuario}/rol`.
- `GET /api/admin/auditoria?page=0&size=20` consulta directamente `registros_auditoria` en MariaDB.

Django no mantiene usuarios, roles ni sesiones duplicadas. Spring Boot es dueño de `usuarios`, `tokens_recuperacion` y `registros_auditoria`; Django usa modelos `managed = False` para leer `usuarios` y `registros_auditoria`. Antes de cada operación, Django valida el Bearer opaco llamando a `/api/auth/me` y exige ADMINISTRADOR. La escritura de rol usa una ruta interna no consumible por los clientes públicos, protegida además por `INTERNAL_ADMIN_API_KEY` y por la autorización de Spring sobre el Bearer original; Spring registra `CAMBIO_ROL` en la misma operación.

El usuario MySQL de Django (`pulgatec_admin_ro`) tiene solo `SELECT` sobre `BD_Pulgatec.usuarios` y `BD_Pulgatec.registros_auditoria`; Spring usa `pulgatec_app` con permisos acotados al esquema para sus escrituras. No ejecutar `manage.py migrate` contra `BD_Pulgatec`: no hay tablas Django propias requeridas. Django conserva SQLite para tests locales. No guardar tokens, claves internas ni credenciales en logs.

Las sesiones Spring siguen en memoria: al reiniciar Spring los tokens opacos dejan de ser válidos; la información de usuarios, productos, recuperación y auditoría sí queda persistida en MySQL. La vigencia/flujo HU25 no cambia.
