# HU61 · Flujo de roles y auditoría desde Django

Este documento describe el panel integrado de Django (`/admin/`) y las rutas
administrativas que usa. Spring sigue siendo el dueño de los usuarios, roles y
registros de auditoría; Django consulta las tablas compartidas y delega los
cambios de rol.

## Consulta de usuarios

- La interfaz es **Administración de PulgaTec → Usuarios de PulgaTec**.
- La lista muestra `id`, `correo` y `rol`; permite buscar por correo y filtrar
  por rol.
- El endpoint de Django es `GET /api/admin/usuarios`.
- Django valida el Bearer contra `GET /api/auth/me` y exige `ADMINISTRADOR`.
- La lectura de `BD_Pulgatec.usuarios` se realiza con el alias compartido de
  solo lectura. No se devuelven hashes ni credenciales.

## Cambio de rol

1. Desde **Usuarios de PulgaTec**, se abre **Cambiar rol** para el usuario
   seleccionado.
2. El selector parte del rol actual; **Guardar cambio** se habilita al elegir
   un rol distinto.
3. El administrador confirma sus credenciales de PulgaTec. Django inicia una
   sesión temporal en Spring, valida el permiso y llama a
   `PATCH /_internal/admin/usuarios/{idUsuario}/rol` con la clave interna.
4. Spring cambia el rol y registra `CAMBIO_ROL`. Django cierra la sesión
   temporal.
5. La pantalla de confirmación muestra el correo, rol anterior y rol nuevo, y
   ofrece enlaces para revisar la auditoría y volver al listado actualizado.

El endpoint de Django para clientes administrativos es
`PATCH /api/admin/usuarios/{idUsuario}/rol`; acepta solo `CLIENTE`, `VENDEDOR`
o `ADMINISTRADOR`.

### Errores controlados

- `401`: sesión Bearer ausente, inválida o vencida.
- `403`: el usuario autenticado en Spring no tiene rol administrador.
- `400`: rol no permitido o igual al rol actual.
- `404`: identificador de usuario inexistente.
- `502`: respuesta inválida del backend de usuarios.
- `503`: Spring no disponible o integración interna sin configurar.

Ante una respuesta fallida, Django conserva el rol previo en la interfaz y
muestra el error recibido o un mensaje controlado. No presenta la operación
como exitosa.

## Consulta de auditoría

- La interfaz es **Administración de PulgaTec → Registros de auditoría**.
- Se puede filtrar por **Cambio de rol**, acción y fecha; el orden inicial es
  del evento más reciente al más antiguo.
- El endpoint de Django es `GET /api/admin/auditoria?page=0&size=20`.
- Los campos disponibles hoy en la tabla compartida son `accion`, `usuario` y
  `fecha`. `usuario` identifica al actor que realizó la operación.
- El usuario afectado y los roles anterior/nuevo no están en el esquema actual
  de auditoría de Spring. Para mostrarlos en el registro histórico, Spring debe
  agregarlos al contrato y persistencia; Django no debe inferirlos.

Las rutas de Django requieren autenticación y validan el rol administrador en
Spring antes de leer los datos compartidos.
