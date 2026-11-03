# QA HU49/HU50

## Alcance

- **HU49** — "Como vendedor, quiero actualizar el estado de un pedido, a fin de
  informar al cliente sobre el avance de su compra."
- **HU50** — "Como cliente, quiero visualizar el detalle de un pedido, a fin de
  revisar los productos y datos de la compra."

## Validación automatizada

- `backend-user-api`: **326 tests exitosos, 0 fallos** (`mvn test`).
  - `EstadoPedidoServiceTest` (8): transición válida con historial, transición no
    permitida, estado desconocido, estado repetido, vendedor sin productos del
    pedido, pedido inexistente, cliente sin permiso, estados permitidos.
  - `PedidoEstadoActualizacionControllerTest` (6): 200 con estado/historial, 400
    transición no permitida, 403 vendedor ajeno, 404 pedido inexistente, 401 sin
    sesión, 403 cliente.
  - `PedidoDetalleControllerTest` (5): detalle exacto con líneas y total
    persistido, 404 inexistente, 403 compra de otro cliente, 401 sin sesión,
    total persistido sin recalcular con precios de catálogo.
  - `ExperienciaCompraServiceTest`: detalle con precio congelado y total
    persistido; detalle de pedido ajeno rechazado.
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

## Notas

- La tabla `historial_estados_pedido` se crea automáticamente con
  `spring.jpa.hibernate.ddl-auto=update` (perfil `mysql`); en H2 se regenera en
  cada arranque de pruebas.
- El estado previo de los pedidos de QA era `NULL`; el backend lo interpreta
  como `REGISTRADO` (estado inicial) tanto en la API como en el historial.
- El pedido 3 de QA quedó en `ENTREGADO` como evidencia de la prueba E2E.
