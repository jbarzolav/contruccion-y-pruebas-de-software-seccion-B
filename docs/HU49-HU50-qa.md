# QA HU49/HU50

## Alcance

- **HU49** — "Como vendedor, quiero actualizar el estado de un pedido, a fin de
  informar al cliente sobre el avance de su compra."
- **HU50** — "Como cliente, quiero visualizar el detalle de un pedido, a fin de
  revisar los productos y datos de la compra."

## Validación automatizada

- `backend-user-api`: **327 tests exitosos, 0 fallos** (`mvn test`).
  - `EstadoPedidoServiceTest` (7): transición válida con historial, transición no
    permitida, estado desconocido, estado repetido, vendedor sin productos del
    pedido, pedido inexistente, cliente sin permiso.
  - `PedidoEstadoActualizacionControllerTest` (6): 200 con estado/historial, 400
    transición no permitida, 403 vendedor ajeno, 404 pedido inexistente, 401 sin
    sesión, 403 cliente.
  - `PedidoDetalleControllerTest` (7): detalle exacto con líneas y total
    persistido, identificador inválido (`abc` → 400), identificador no encontrado
    (`0` → 404), pedido inexistente (404), 403 compra de otro cliente, 401 sin
    sesión, total persistido sin recalcular con precios de catálogo.
  - `ExperienciaCompraServiceTest`: detalle con precio congelado y total
    persistido (el catálogo cambia a `999.00` y el detalle sigue devolviendo el
    precio registrado); detalle de pedido ajeno rechazado.
- `frontend-user-web`: `npm run build` exitoso (125 módulos).
- `mobile-android`: `assembleDebug` exitoso (Gradle offline).

## Validación manual E2E (MariaDB `BD_Pulgatec`, backend en `localhost:9000`)

Credenciales QA: `qa-hu45-seller-…` (vendedor, id 13), `qa-hu45-a-…` (cliente
id 10) y `qa-hu46-b-…` (cliente id 11).

### HU49

| Caso | Resultado |
|------|-----------|
| `GET /api/vendedores/me/pedidos` | 200 con `estadosPermitidos: [EN_PROCESO, CANCELADO]` por pedido |
| `PATCH /api/pedidos/3/estado` → `EN_PROCESO` | 200, `estadoAnterior: REGISTRADO`, `fechaActualizacion` y `historial[0]` con `usuarioId: 13` |
| `PATCH /api/pedidos/3/estado` → `ENTREGADO` | 200, `estadosPermitidos: []` (estado final) y 2 filas de historial |
| `PATCH` `ENTREGADO → CANCELADO` | 400 "No se permite pasar de ENTREGADO a CANCELADO." |
| `PATCH` con estado `VOLANDO` | 400 "El estado solicitado no es válido…" |
| `PATCH` como cliente | 403 "No tienes permiso para realizar esta acción." |
| `PATCH` de pedido inexistente | 404 "Pedido no encontrado." |
| `PATCH` sin Bearer | 401 "Debes iniciar sesión." |
| Historial en BD | tabla `historial_estados_pedido` creada por `ddl-auto=update` con 2 filas (REGISTRADO→EN_PROCESO, EN_PROCESO→ENTREGADO) |

### HU50

| Caso | Resultado |
|------|-----------|
| `GET /api/pedidos/1` (cliente A) | 200 con cabecera (`fecha`, `estado`), línea `2 × 125.50 = 251.00` y total `251.00` |
| `GET /api/pedidos/2` (cliente B) | 200 con su propia compra |
| `GET /api/pedidos/1` (cliente B) | 403 "No puedes operar con una compra de otro cliente." |
| `GET /api/pedidos/987654` | 404 "Pedido no encontrado." |
| `GET /api/pedidos/1` sin Bearer | 401 "Debes iniciar sesión." |
| `GET /api/pedidos/3/estado` (cliente A) | 200 con `estado: ENTREGADO` → el cliente ve el avance informado por el vendedor |

## Verificación final por criterio (backend + MariaDB, código actual)

Reejecutada tras reiniciar el backend con el código vigente sobre
`BD_Pulgatec`. Se usó el pedido 2 (`REGISTRADO`) para la transición válida y
se restauró después (estado `NULL` y su fila de historial eliminadas) para que
el caso pueda repetirse.

| Criterio | Resultado |
|----------|-----------|
| 49.1 web | Selector con `estadosPermitidos` + **Guardar estado** + estado actual + aviso *"Pedido #1: estado actualizado a …"* (recorrido en Chromium) |
| 49.2 validaciones | `PATCH` `EN_PROCESO → REGISTRADO` → **400** "No se permite pasar de EN_PROCESO a REGISTRADO."; `VOLANDO` → **400**; vendedor sin productos en el pedido → **403** "El pedido no incluye productos de tus publicaciones." |
| 49.3 backend | `PATCH` `REGISTRADO → EN_PROCESO` → **200** con `fechaActualizacion` y fila en `historial_estados_pedido` (`usuario_id = 13`) |
| 49.4 API | `PATCH /api/pedidos/{idPedido}/estado` con estados, respuesta, errores y auditoría documentados en `backend-user-api/README.md` |
| 49.5 Android | Desplegable solo con transiciones válidas, **Guardar estado**, aviso de éxito y "Este pedido ya no admite cambios de estado." (emulador) |
| 49.6 pruebas | 13 tests de HU49 en verde: transición válida, no permitida, vendedor ajeno, pedido inexistente, sin permisos (401/403) |
| 50.1 web | `/pedidos/:idPedido` con fecha, estado, productos, cantidades, precio unitario, subtotales, total y "Volver a mis compras" |
| 50.2 validaciones | `GET` sin token → **401**; de otro cliente → **403**; `abc` → **400**; `0` y `987654` → **404** |
| 50.3 backend | `GET /api/pedidos/1` → **200** con `2 × 125.50 = 251.00` y total `251.00` persistidos; prueba `devuelveElDetalleConElPrecioRegistradoYElTotalPersistido` cambia el catálogo a `999.00` y el detalle sigue devolviendo el precio registrado |
| 50.4 API | `GET /api/pedidos/{idPedido}` con campos de cabecera y detalle documentados en `backend-user-api/README.md` |
| 50.5 Android | Pantalla *Detalle del pedido* con productos, cantidades, precios, subtotales, estado y total (emulador) |
| 50.6 pruebas | 7 tests de `PedidoDetalleControllerTest` + 2 de `ExperienciaCompraServiceTest` en verde: líneas exactas, total persistido, inexistente, ajeno y sin autenticación |

## Transiciones de estado definidas

| Estado actual | Estados permitidos |
|---------------|--------------------|
| `REGISTRADO` | `EN_PROCESO`, `CANCELADO` |
| `EN_PROCESO` | `ENTREGADO`, `CANCELADO` |
| `ENTREGADO` | — (estado final) |
| `CANCELADO` | — (estado final) |

## Endpoints

| Método | Ruta | Rol | Descripción |
|--------|------|-----|-------------|
| `PATCH` | `/api/pedidos/{idPedido}/estado` | VENDEDOR | Actualiza el estado y registra el cambio en el historial. Cuerpo: `{"estado":"EN_PROCESO"}` |
| `GET` | `/api/pedidos/{idPedido}` | CLIENTE (propietario) | Detalle del pedido con líneas, precios registrados y total |
| `GET` | `/api/vendedores/me/pedidos` | VENDEDOR | Pedidos recibidos; ahora incluye `estadosPermitidos` |

## Validación manual en la web (`http://localhost:3000`)

Recorrido hecho en Chromium sobre el frontend de desarrollo (Vite) con el
backend en `localhost:9000` y MariaDB real:

1. **Vendedor** (`qa-hu45-seller-…`) → *Pedidos recibidos*:
   - Cada pedido muestra su estado actual, total y la lista de estados válidos
     (`EN_PROCESO`, `CANCELADO`).
   - Se eligió `EN_PROCESO` en el pedido #1 y se pulsó **Guardar estado**:
     apareció el aviso *"Pedido #1: estado actualizado a EN_PROCESO."*, el estado
     pasó a `EN_PROCESO` y "Última actualización" se actualizó.
   - El selector pasó a ofrecer solo `ENTREGADO` y `CANCELADO` (transiciones
     válidas desde `EN_PROCESO`).
2. **Cliente** (`qa-hu45-a-…`) → *Mis compras*:
   - El pedido #1 aparece con estado `EN_PROCESO`, es decir, el cliente ve el
     avance informado por el vendedor.
   - El botón **Ver detalle** abre `/pedidos/1`: cabecera con fecha y estado,
     tabla `Producto | Cantidad | Precio unitario | Subtotal`
     (`2 × S/ 125.50 = S/ 251.00`) y total `S/ 251.00`, más el enlace
     "Volver a mis compras".
3. Consola del navegador: sin errores de la aplicación (solo fallan las
   imágenes externas de los productos QA, `https://example.test/...`).

## Validación manual en Android (emulador `emulator-5554`, API `http://10.0.2.2:9000`)

APK `app-debug.apk` instalado y recorrido con `adb`:

1. **Vendedor** (`qa-hu45-seller-…`) → pestaña *Ventas* (Pedidos recibidos):
   - El pedido #1 mostraba `Estado: EN_PROCESO` (actualizado previamente desde
     la web) y el botón **Seleccionar estado**.
   - El desplegable ofreció solo las transiciones válidas: `ENTREGADO` y
     `CANCELADO`.
   - Se eligió `ENTREGADO` y se pulsó **Guardar estado**: apareció el aviso
     *"Pedido #1: estado actualizado a ENTREGADO."*, el estado pasó a
     `ENTREGADO` y el selector fue sustituido por
     *"Este pedido ya no admite cambios de estado."* (estado final).
2. **Cliente** (`qa-hu45-a-…`) → pestaña *Pedidos*:
   - El pedido #1 aparece con `Estado: ENTREGADO` y la fecha de actualización
     registrada desde Android.
   - El botón **Detalle del pedido** abre la pantalla nueva: `Pedido #1`,
     fecha, estado, última actualización, línea
     `qa-hu45-hu46-product-… × 2`, `Precio: S/ 125.50`,
     `Subtotal: S/ 251.00`, `Total: S/ 251.00` y **Volver a mis pedidos**.
3. Historial en BD tras el recorrido completo (4 filas, todas con
   `usuario_id = 13`, el vendedor autenticado):

   | id | pedido | anterior → nuevo | fecha |
   |----|--------|------------------|-------|
   | 1 | 3 | REGISTRADO → EN_PROCESO | 2026-11-03 17:26:13 |
   | 2 | 3 | EN_PROCESO → ENTREGADO | 2026-11-03 17:26:13 |
   | 3 | 1 | REGISTRADO → EN_PROCESO | 2026-11-03 17:34:36 |
   | 4 | 1 | EN_PROCESO → ENTREGADO | 2026-11-03 18:15:43 |

## Notas

- La tabla `historial_estados_pedido` se crea automáticamente con
  `spring.jpa.hibernate.ddl-auto=update` (perfil `mysql`); en H2 se regenera en
  cada arranque de pruebas.
- El estado previo de los pedidos de QA era `NULL`; el backend lo interpreta
  como `REGISTRADO` (estado inicial) tanto en la API como en el historial.
- El pedido 3 de QA quedó en `ENTREGADO` como evidencia de la prueba E2E.
- Los pedidos 1 y 3 de QA quedaron en `ENTREGADO` tras el recorrido web y
  Android; para repetir la prueba desde `REGISTRADO` basta con reiniciar sus
  estados en la tabla `pedidos`.
- Los campos de respuesta y los errores de `PATCH /api/pedidos/{id}/estado` y
  `GET /api/pedidos/{id}` están documentados en `backend-user-api/README.md`
  (sección "API de pedidos — HU49/HU50"); el proyecto no usa OpenAPI/springdoc
  y no se añadieron dependencias para documentarlo.
- La verificación final usó el pedido 2 y lo restauró a su estado original
  (`estado = NULL`, sin filas de historial); los roles de las cuentas QA
  quedaron como al inicio (clientes 10 y 11 `CLIENTE`, vendedor 13 `VENDEDOR`).
