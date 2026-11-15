# HU59 · Contrato de compra en Spring (para web — Huanco)

> **Historia:** *Como cliente, quiero completar una compra y seguir su estado en
> web y Android y cancelarla mientras esté registrada.*
> **Tarea 59.1** — Documentar el contrato de compra: `idPedido`, `fecha`,
> `estado`, `total` y detalles con `idProducto`, `nombreProducto`, `cantidad`,
> `precioUnitario` y `subtotal`. Indicar errores de sesión y stock.
>
> Servicio: **Spring Boot `backend-user-api` en `http://localhost:9000`**
> Autor: Barzola · Consumidor: frontend web (Huanco)

---

## 1. Confirmar la compra — `POST /api/pedidos`

Convierte el **carrito activo del usuario autenticado** en un pedido registrado.
No recibe cuerpo (JSON vacío): todo sale del carrito de la sesión.

| Elemento | Valor |
|---|---|
| Método / ruta | `POST /api/pedidos` |
| Autenticación | **Obligatoria**: `Authorization: Bearer <token>` (rol `CLIENTE`) |
| Cuerpo | ninguno |
| Query opcional | `clienteId` — se **ignora si es el de la sesión**; si es otro → `403` |
| Éxito | `201 Created` con el comprobante del pedido |

### 1.1 Respuesta `201` — comprobante (`PedidoResponse`)

```json
{
  "idPedido": 18,
  "clienteId": 15,
  "fecha": "2026-11-14T17:32:05",
  "estado": "REGISTRADO",
  "total": 399.80,
  "detalles": [
    {
      "idProducto": 46,
      "nombreProducto": "Notebook Lenovo",
      "cantidad": 2,
      "precioUnitario": 199.90,
      "subtotal": 399.80
    }
  ]
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `idPedido` | `number` (long) | Identificador único del pedido. Es el que se usa en todas las rutas siguientes. |
| `clienteId` | `number` (long) | Id del comprador (sale de la sesión, nunca del request). |
| `fecha` | `string` ISO-8601 | Fecha/hora de creación, sin zona (`yyyy-MM-dd'T'HH:mm:ss`). |
| `estado` | `string` | Estado al momento de confirmar. **Siempre nace en `REGISTRADO`.** Ver §3. |
| `total` | `number` (decimal, 2 dec.) | **Lo calcula siempre el servidor**: suma de los `subtotal`. |
| `detalles[]` | `array` | Una línea por producto del carrito. |
| `detalles[].idProducto` | `number` (long) | Id del producto comprado. |
| `detalles[].nombreProducto` | `string` | Nombre al momento de la compra. |
| `detalles[].cantidad` | `number` (int) | Unidades compradas (≥ 1). |
| `detalles[].precioUnitario` | `number` (decimal) | **Precio congelado** del ítem del carrito (no se recalcula con el catálogo). |
| `detalles[].subtotal` | `number` (decimal) | `cantidad × precioUnitario`, calculado por el servidor. |

**Efectos de un `201`** (todo en una transacción):

1. Descuenta el stock de cada producto.
2. Vacía el carrito del cliente (ítems y carrito).
3. Crea la notificación de **nueva compra** para cada vendedor (HU 52).
4. Evalúa el umbral de stock bajo de cada producto (HU 58).

> ⚠️ Si la respuesta se pierde (timeout/red), **reenviar el mismo `POST` no
> debe duplicar el pedido**: envía la cabecera `Idempotency-Key` (ver §5) y
> el servidor responde con el **mismo `idPedido`**.

### 1.2 Errores del `POST /api/pedidos`

Todos los errores usan el mismo formato:

```json
{ "status": 400, "error": "Stock insuficiente", "messages": ["…"] }
```

| HTTP | `error` | `messages[0]` | Cuándo ocurre |
|---|---|---|---|
| `401` | `Autorización` | `Debes iniciar sesión.` | Sin cabecera `Authorization` o sin prefijo `Bearer`. |
| `401` | `Autorización` | `La sesión es inválida o ha vencido.` | Token inválido/expirado o usuario ya no existe. |
| `403` | `Autorización` | `No tienes permiso para realizar esta acción.` | Rol sin permiso `COMPRAR` (p. ej. `ADMINISTRADOR`). |
| `403` | `Autorización` | `No puedes operar con datos de otro usuario.` | `clienteId` distinto del de la sesión. |
| `400` | `Pedido inválido` | `El carrito está vacío` | Sin carrito o sin ítems. **No se registra nada.** |
| `400` | `Pedido inválido` | `El clienteId debe ser mayor a 0` | `clienteId=0` o negativo. |
| `400` | `Stock insuficiente` | `Stock insuficiente para "<nombre>": se solicitan N y hay M disponibles` | Alguna línea pide más unidades que el stock. **No se descuenta nada, no se vacía el carrito.** |
| `409` | `Experiencia de compra inválida` | `La clave de intento ya fue usada por otra cuenta.` | Cabecera `Idempotency-Key` ya usada por un cliente distinto (ver §5). |

> La validación de stock se hace **antes** de descontar cualquier unidad: no hay
> descuentos parciales ni pedidos a medias.

---

## 2. Seguir el estado de la compra — rutas de lectura

Todas exigen `Bearer` y **solo el dueño de la compra** puede leerla
(`403` para otra compra; `404` si no existe).

### 2.1 Detalle del pedido — `GET /api/pedidos/{idPedido}` (HU 50)

```json
{
  "idPedido": 18,
  "clienteId": 15,
  "fecha": "2026-11-14T17:32:05",
  "estado": "EN_PROCESO",
  "fechaActualizacion": "2026-11-14T18:00:00",
  "total": 399.80,
  "detalles": [ { "idProducto": 46, "nombreProducto": "Notebook Lenovo",
                  "cantidad": 2, "precioUnitario": 199.90, "subtotal": 399.80 } ]
}
```

Igual que el comprobante más `estado` vigente y `fechaActualizacion`. El
`total` es el **persistido**: no se recalcula aunque el catálogo cambie.

### 2.2 Estado puntual — `GET /api/pedidos/{idPedido}/estado` (HU 47)

```json
{ "idPedido": 18, "estado": "EN_PROCESO", "fechaActualizacion": "2026-11-14T18:00:00" }
```

### 2.3 Mis compras — `GET /api/pedidos/me` (HU 46 / HU 55)

Query opcional: `estado`, `desde`, `hasta`, `montoMin`, `montoMax`, `page`,
`size`. Sin parámetros devuelve la lista completa; con parámetros pagina.
Cada elemento incluye `idPedido`, `fecha`, `estado`, `total` y `detalles`.

### 2.4 Cambio de estado — `PATCH /api/pedidos/{idPedido}/estado` (HU 49)

Solo el **vendedor** que tiene productos en el pedido. Cuerpo:
`{ "estado": "EN_PROCESO" }`. Responde `200` con el estado nuevo y el
historial; `400` si la transición no está permitida; `403` si el pedido es
ajeno; `404` si no existe.

---

## 3. Catálogo de estados (HU 49)

| Estado | Significado | Siguientes permitidos |
|---|---|---|
| `REGISTRADO` | Pedido creado (estado inicial de la compra). | `EN_PROCESO`, `CANCELADO` |
| `EN_PROCESO` | El vendedor lo está preparando. | `ENTREGADO`, `CANCELADO` |
| `ENTREGADO` | Compra terminada. **Estado final.** | — (ninguno) |
| `CANCELADO` | Compra cancelada. **Estado final.** | — (ninguno) |

- La transición se valida **siempre en el servidor** (`400` si no corresponde).
- `ENTREGADO` y `CANCELADO` no admiten cambios posteriores.
- **Cancelación del comprador (HU 59.5 / HU 65):** mientras el pedido esté en
  `REGISTRADO` el comprador puede cancelar su propio pedido; sobre su pedido
  ajeno → `403`.

---

## 4. Resumen de códigos HTTP (toda la historia)

| Código | Caso |
|---|---|
| `201` | Compra confirmada (comprobante). |
| `200` | Lectura de detalle/estado/lista o cambio de estado válido. |
| `400` | Carrito vacío, stock insuficiente, transición inválida, parámetro inválido. |
| `401` | Sin sesión, token vencido o inválido. |
| `403` | Rol sin permiso, pedido/carrito de otro usuario. |
| `404` | Pedido o ítem inexistente. |
| `409` | Conflicto de integridad (correo duplicado en registro). |

---

## 5. Identificador de intento (idempotencia) — tarea 59.2 ✅ implementado

**Problema:** si el `201` se pierde en la red, el cliente reenvía el `POST` y,
sin protección, respondería `400 carrito vacío` (el carrito ya se vació en la
primer compra): el cliente no sabe si su compra existió.

**Contrato:** el cliente genera un `uuid` por compra y lo envía como cabecera:

| Elemento | Valor |
|---|---|
| Cabecera | `Idempotency-Key: <uuid>` (opcional; una clave por compra) |
| Primer envío | `201` con el comprobante; la clave queda asociada al `idPedido` (misma transacción que el pedido). |
| Reenvío con la **misma** clave (mismo cliente) | `201` con **el mismo `idPedido`** y el mismo cuerpo: **no** se repite el pedido, **no** se descuenta stock otra vez, **no** se emiten avisos nuevos. |
| Reenvío con clave distinta | Se trata como una compra nueva (comportamiento normal). |
| Sin cabecera | Comportamiento actual (sin protección), para compatibilidad con clientes viejos. |
| Clave ya usada por **otra** cuenta | `409` → `{"status":409,"error":"Experiencia de compra inválida","messages":["La clave de intento ya fue usada por otra cuenta."]}` (no revela el pedido ajeno). |

Ejemplo de reenvío (segunda petición idéntica a la primera):

```http
POST /api/pedidos
Authorization: Bearer <token>
Idempotency-Key: 8f1b2c3d-4e5f-4a6b-8c9d-0e1f2a3b4c5d
```

```json
201
{ "idPedido": 20, "clienteId": 15, "fecha": "2026-11-14T19:29:36.1209853",
  "estado": "REGISTRADO", "total": 1899.90, "detalles": [ "…" ] }
```

> La tabla `intentos_compra` guarda `clave` (única), `clienteId`, `idPedido`
> y `fecha` en la **misma transacción** que el pedido. Si dos peticiones con
> la misma clave llegan en paralelo, la restricción única de `clave` impide
> crear dos pedidos: la segunda recibe `409`.

**Recomendación para el web (59.3):** si la confirmación queda incierta por
red, reenviar con la **misma** `Idempotency-Key`; si no se generó clave,
consultar `GET /api/pedidos/me` antes de reenviar.

---

## 6. Notas para el frontend web (Huanco)

1. **Mostrar en la confirmación** (59.3): `idPedido` (número de compra),
   `nombreProducto`, `cantidad`, `precioUnitario`, `subtotal`, `total`,
   `estado` legible (`REGISTRADO` → "Registrado") y `fecha` con hora y minuto.
2. `fecha` llega en ISO-8601 sin zona: formatear en el cliente
   (`new Date(fecha)` o `Intl.DateTimeFormat('es-PE')`).
3. Si el `POST` falla por red (sin respuesta), **no** reenviar a ciegas:
   reenviar con la **misma** `Idempotency-Key` (el servidor devuelve el mismo
   `idPedido`) o consultar `GET /api/pedidos/me` para ver si el pedido ya
   existe.
4. Un `400` de stock **no** modifica el carrito: se puede mostrar el mensaje
   tal cual (`messages[0]`) y dejar al usuario corregir cantidades.
5. Errores de sesión (`401`) → redirigir a login y conservar la ruta actual.
