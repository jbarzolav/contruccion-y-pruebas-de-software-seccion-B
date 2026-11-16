# HU60 — Filtros de pedidos, métricas de ventas y umbral de stock

> **HU60.1** — *Documentar los filtros `estado` / `desde` / `hasta` / `productoId`
> y las métricas/umbral que consumen web y Android.*
> **HU60.2** — *Verificar en Spring los filtros combinados, el rango invertido,
> el producto ajeno, `ingresosConfirmados`/conteos y el cruce de umbral.*

Todo lo que sigue se documentó leyendo el código vigente y se verificó contra
el backend real (`localhost:9000`, perfil `mysql`, BD `BD_Pulgatec`) el
**2026-11-16**. Las rutas de archivo son relativas a la raíz del repo.

---

## 1. Filtros de pedidos del vendedor (HU 48 / HU 56)

### 1.1 Endpoint

`GET /api/vendedores/me/pedidos`
(`backend-user-api/src/main/java/com/ejemplo/backenduserapi/controller/PedidosVendedorController.java` L27–L41)

| Query param | Tipo | Requerido | Significado |
|---|---|---|---|
| `estado` | `String` | no | `REGISTRADO`, `EN_PROCESO`, `ENTREGADO`, `CANCELADO` (se normaliza con `trim().toUpperCase()`; vacío = sin filtro). Cualquier otro valor → `400`. |
| `desde` | `LocalDate` (`AAAA-MM-DD`) | no | Inclusivo: el pedido cuenta si `fecha.toLocalDate() >= desde`. |
| `hasta` | `LocalDate` (`AAAA-MM-DD`) | no | Inclusivo: `fecha.toLocalDate() <= hasta`. |
| `productoId` | `Long` | no | Filtra pedidos que contengan **al menos una línea** de ese producto **propio** del vendedor. ⚠️ El nombre real del parámetro es **`productoId`** (no `productId`: ese literal no existe en el backend). |
| `page` | `Integer` | no | Página, desde `0`. **Default `0`.** |
| `size` | `Integer` | no | Tamaño `1..100`. **Default `100`.** |

- **Sin ningún parámetro** se ignora la paginación y se devuelve la lista
  completa (`pedidos.listarPedidos(vendedor)`).
- **Con al menos un parámetro** se aplica `filtrarPedidos(...)` con los
  defaults de paginación anteriores.

### 1.2 Semántica

- Los filtros se encadenan con **AND** (`PedidosVendedorService.java` L59–L65):
  estado ∧ rango de fechas ∧ pertenencia al `productoId`.
- El **rango es por día completo e inclusivo** en ambos extremos
  (`service/FiltroPedidos.java` L44–L48).
- **Rango invertido (`desde > hasta`) NO devuelve lista vacía: devuelve `400`.**
- El `total` de cada pedido devuelto es **solo la suma de las líneas del
  vendedor** (`PedidosVendedorService.java` L81–L85); las líneas también se
  recortan a las propias.
- Quién puede llamarlo: la ruta exige el permiso `GESTIONAR_PRODUCTOS`
  (`config/AutorizacionInterceptor.java` L31–L32) → solo rol `VENDEDOR`;
  además el servicio vuelve a exigir el rol (L69–L74).

### 1.3 Errores (cuerpo `{"status","error","messages"}`)

| Caso | HTTP | `messages[0]` exacto | Origen |
|---|---|---|---|
| `desde > hasta` | `400` | `La fecha inicial no puede ser posterior a la fecha final.` | `service/FiltroPedidos.java` L30–L32 |
| `estado` desconocido | `400` | `El estado indicado no es válido.` | `FiltroPedidos.java` L19–L26 |
| `productoId < 1` | `400` | `El producto indicado no es válido.` | `PedidosVendedorService.java` L50 |
| `productoId` inexistente | `404` | `Producto no encontrado.` | `PedidosVendedorService.java` L53 |
| **`productoId` de otro vendedor** | **`403`** | **`Solo puedes filtrar por tus propios productos.`** | `PedidosVendedorService.java` L55–L56 |
| `page`/`size` fuera de rango | `400` | `La página debe ser mayor o igual a cero y el tamaño debe estar entre 1 y 100.` | `FiltroPedidos.java` L39–L41 |
| Fecha con formato distinto de `AAAA-MM-DD` | `400` | `Los parámetros de consulta deben ser válidos.` (`error: "Parámetro inválido"`) | `GlobalExceptionHandler.java` L197–L200 |
| Rol que no es vendedor | `403` | `No tienes permiso para realizar esta acción.` | `AutorizacionService.java` L19–L22 |
| Sin Bearer | `401` | `Debes iniciar sesión.` | interceptor de sesión |

> El filtro del **cliente** (`GET /api/pedidos/me`, HU 55) usa la misma clase
> `FiltroPedidos` (mismos mensajes) pero agrega `montoMin`/`montoMax` y **no**
> tiene `productoId` (`ExperienciaCompraService.java` L65–L79).

### 1.4 Quién lo consume

| Cliente | Archivo | Qué manda |
|---|---|---|
| Web | `frontend-user-web/src/api/pedidosVendedor.js` L16–L21 | `estado`, `desde`, `hasta`, `productoId` (vacío = omitido) + `page/size` 0/100 por defecto; estado inicial en `components/PedidosRecibidos.jsx` L8. |
| Android | `mobile-android/.../model/remote/ProductoApi.kt` L250–L251 | El móvil llama **sin query params** y filtra **en cliente** con `FiltrosVentas` (`viewmodel/PedidosRecibidosViewModel.kt` L40–L67, `view/PedidosRecibidosScreen.kt` L76–L85). Por eso en Android no se ven los `400/403/404` de esta tabla: ocurren solo si se usan los parámetros desde web o con `curl`. |

---

## 2. Métricas de ventas (HU 57)

### 2.1 Endpoint y parámetros

`GET /api/vendedores/me/metricas/ventas`
(`controller/MetricasVendedorController.java` L39–L47 → `service/MetricasVentasService.java`)

| Query param | Tipo | Default | Regla |
|---|---|---|---|
| `desde` | `AAAA-MM-DD` (`@DateTimeFormat(iso=DATE)`) | `hoy − 29 días` | Ambos extremos **inclusivos por día completo**. |
| `hasta` | `AAAA-MM-DD` | `hoy` | — |
| `compararPeriodoAnterior` | boolean | **`true`** | Si es `false`, `periodoAnterior`, `desdeAnterior`, `hastaAnterior` y las 3 comparativas llegan en **`null`**. |

Errores:

| Caso | HTTP | `messages[0]` |
|---|---|---|
| Solo `desde` o solo `hasta` | `400` | `Indica las dos fechas del periodo: desde y hasta.` |
| `desde > hasta` | `400` | `La fecha inicial no puede ser posterior a la fecha final.` |
| Sin sesión | `401` | `Sesion requerida.` |
| Rol ≠ vendedor | `403` | `Solo un vendedor puede consultar sus metricas de ventas.` |

### 2.2 Período anterior (HU 60.1 — cómo se calcula)

```
duración      = (hasta − desde) + 1 días        // inclusive, ej. 30
desdeAnterior = desde  − duración
hastaAnterior = hasta  − duración               // termina el día anterior al inicio
```

Ejemplo real (2026-11-16): periodo actual `2026-10-18 … 2026-11-16` (30 días),
anterior `2026-09-18 … 2026-10-17` (30 días, **sin solape**).
Verificado por el test `MetricasVentasServiceTest.elPeriodoAnteriorTieneLaMismaDuracionYNoSeSolapa`
(L89: 1–15 mar → anterior 14–28 feb) y en vivo.

### 2.3 Qué cuenta cada métrica

| Métrica | Regla exacta | Código |
|---|---|---|
| **`ingresosConfirmados`** | `SUM(cantidad × precioUnitario)` **solo de las líneas propias del vendedor** cuyo pedido esté en **`EN_PROCESO` o `ENTREGADO`** (`ESTADOS_CONFIRMADOS`, L52). **`REGISTRADO` y `CANCELADO` NO cuentan.** El precio es el congelado al comprar. | `DetallePedidoRepository.sumarIngresosConfirmados` L31–L42 |
| **`cantidadPedidos`** | `COUNT(DISTINCT idPedido)` con la misma regla de estados (pedidos distintos, no líneas). | `DetallePedidoRepository.contarPedidosConfirmados` L48–L59 |
| **`promedioPorPedido`** | `ingresos / cantidadPedidos` (escala 2, `HALF_UP`); **`0.00` cuando no hay pedidos** (nunca divide entre cero). | `MetricasVentasService` L116–L134 |
| **`productosMasVendidos`** | Top **10** por `SUM(cantidad)` (unidades) dentro de los mismos estados, con `importeAtribuido = Σ cantidad × precioUnitario` de esas líneas. | `DetallePedidoRepository.productosMasVendidos` L66–L84 |
| **`comparativa*`** | `diferenciaAbsoluta = actual − anterior`; `variacionPorcentual = (diff × 100) / anterior` **o `null` si el anterior fue 0**; `tendencia ∈ AUMENTO / DISMINUCION / IGUAL`. | `MetricasVentasService` L161–L173 |

### 2.4 Forma de la respuesta (`MetricasVentasResponse`)

```jsonc
{
  "desde": "2026-10-18", "hasta": "2026-11-16",
  "desdeAnterior": "2026-09-18", "hastaAnterior": "2026-10-17",
  "compararPeriodoAnterior": true,
  "periodoActual":     { "ingresosConfirmados": 150.00, "cantidadPedidos": 1, "promedioPorPedido": 150.00 },
  "periodoAnterior":   { "ingresosConfirmados": 0.00,   "cantidadPedidos": 0, "promedioPorPedido": 0.00 },
  "comparativaIngresos": { "diferenciaAbsoluta": 150.00, "variacionPorcentual": null, "tendencia": "AUMENTO" },
  "comparativaPedidos":  { "diferenciaAbsoluta": 1.00,  "variacionPorcentual": null, "tendencia": "AUMENTO" },
  "comparativaPromedio": { ... },
  "productosMasVendidos": [ { "idProducto": 57, "nombre": "…", "imagenUrl": "…",
                              "unidadesVendidas": 3, "importeAtribuido": 150.00 } ]
}
```

DTOs: `dto/MetricasVentasResponse.java`, `MetricaValoresResponse.java`,
`ComparativaMetricaResponse.java`, `ProductoVendidoResponse.java`.

### 2.5 Qué consume cada cliente

| Campo | Web (`components/MetricasVentas.jsx`) | Android (`view/MetricasVentasScreen.kt`) |
|---|---|---|
| `desde`/`hasta`, `desdeAnterior`/`hastaAnterior` | ✔ (L196–L198) | ✔ "Periodo analizado: …" (L116–L118) |
| `periodoActual.ingresosConfirmados` | ✔ "Ingresos confirmados" (L207) | ✔ (L131–L135) |
| `periodoActual.cantidadPedidos` | ✔ "Pedidos" (L210) | ✔ "N.º de pedidos" (L138–L142) |
| `periodoActual.promedioPorPedido` + `comparativaPromedio` | ✘ **no lo muestra** | ✔ "Promedio por pedido" (L145–L149) |
| `comparativaIngresos` / `comparativaPedidos` (`variacionPorcentual`, `tendencia`) | ✔ (L208, L211, L65–L79) | ✔ (L183–L228) |
| `productosMasVendidos` (`nombre`, `unidadesVendidas`, `importeAtribuido`) | ✔ tabla (L222–L228) | ✔ filas (L204) |

Presets de periodo: web `Últimos 7/30 días, Mes actual, Mes anterior,
Personalizado` (`MetricasVentas.jsx` L12–L18); Android `7/30/mes/mes-anterior/
personalizado` (`MetricasVentasViewModel.kt` L145–L159). Ambos validan en
cliente *"La fecha inicial no puede ser posterior a la fecha final."* antes de
llamar.

---

## 3. Umbral de stock y avisos (HU 58)

### 3.1 Endpoints

| Método + ruta | Quién | Devuelve |
|---|---|---|
| `GET /api/productos/{idProducto}/umbral-stock` | vendedor dueño | `UmbralStockResponse` (**`umbral: null` si no está configurado**, no es error) |
| `PUT /api/productos/{idProducto}/umbral-stock` | vendedor dueño | Idempotente (crea/actualiza) |
| `GET /api/vendedores/me/umbrales-stock` | vendedor | Panel: todas sus publicaciones con `stock`, `umbral`, `stockBajo`, `alertaNotificada` |
| `GET /api/vendedores/me/avisos-stock-bajo` | vendedor | Avisos `STOCK_BAJO` enriquecidos con `stockActual`/`umbral` actuales |

### 3.2 Reglas de negocio (`service/AlertaStockService.java` L29–L45)

1. **Condición de stock bajo:** `stock <= umbral` (`stockBajo`, L87/L232) —
   *en o por debajo* del umbral.
2. **El aviso se emite SOLO en el cruce descendente** (`!bajoAntes && bajoAhora`,
   L192–L196): mientras siga en el mismo lado **no se repite**, aunque el stock
   siga bajando.
3. **La reposición rearma** (`bajoAntes && !bajoAhora`, L204–L209):
   `alertaDisparada = false` y **sin notificación**; el siguiente cruce vuelve a
   avisar.
4. **Configurar el umbral con el stock ya por debajo NO emite aviso** (HU
   58.2.D, L38–L41 y L87): responde `stockBajo=true` y `alertaNotificada=true`
   (queda "gastado" hasta que haya reposición).
5. Umbral entero **≥ 0**; detección en **un solo punto**, invocado desde la
   venta (`PedidoService.java` L136) y la edición manual
   (`ProductoService.java` L180).
6. El aviso es **idempotente por clave** `stock:{idProducto}:{numeroAlerta}`
   (`NotificacionService.notificarStockBajo` L103–L112 y `guardarSiNuevo`
   L236–L240): el contador `alertasEmitidas` solo avanza tras un rearme, así
   que dos actualizaciones del mismo cruce no crean dos avisos.

**Mensaje exacto del aviso:**

```
Stock bajo en "{nombre}": quedan {stockActual} unidades y el umbral configurado es {umbral}.
```

Enlace: `/mis-publicaciones#producto-{id}`; tipo `STOCK_BAJO`; estado inicial
`PENDIENTE`.

### 3.3 Errores

| Caso | HTTP | `messages[0]` |
|---|---|---|
| Umbral negativo (Jakarta) | `400` | `El umbral no puede ser negativo` |
| Umbral ausente | `400` | `El umbral es obligatorio` |
| Umbral negativo (servicio) | `400` | `El umbral debe ser un numero entero mayor o igual a cero.` |
| Publicación inexistente | `404` | `Producto no encontrado con id {id}` |
| Publicación ajena | `403` | `El producto no pertenece al vendedor` |
| Sin sesión | `401` | `Sesion requerida.` |
| Rol ≠ vendedor | `403` | `Solo un vendedor puede gestionar los umbrales de stock.` |

### 3.4 Qué consume cada cliente

| Elemento | Web | Android |
|---|---|---|
| Panel `GET /api/vendedores/me/umbrales-stock` | `components/UmbralesStock.jsx` (L41–L45): `idProducto`, `nombre`, `stock`, `umbral`, `stockBajo` | `UmbralesStockViewModel` + `UmbralesStockScreen` (L139–L166): además `alertaNotificada` → badge *"Aviso ya notificado (espera una reposición para volver a avisar)"* |
| `PUT …/umbral-stock` | `api/umbralesStock.js` L33–L36 (en línea) | `UmbralStockRepositorio.encolarUmbral` L127–L137 (**cola persistente** con `claveUnica = "umbral:{id}:{umbral}"`) |
| Avisos `GET …/avisos-stock-bajo` | lista con `stockActual`, `umbral`, `fecha` (L212–L217) | `observarAvisos` (Room + notificaciones) |
| Texto de ayuda | *"Define el mínimo de unidades para recibir un aviso cuando el stock baje hasta ese nivel o menos."* (L138) | *"…Te avisaremos cuando el stock cruce ese umbral (una sola vez por cada bajada); si repones el stock, la alerta se rearma para el siguiente cruce."* (L57–L59) |

---

## 4. Validación automatizada (HU 60.2 — pruebas existentes)

`mvn test` en `backend-user-api` → **441 tests, 0 fallos, 0 errores** (2026-11-16).
Las suites que cubren lo documentado:

| Suite | Cubre |
|---|---|
| `service/PedidosVendedorServiceTest.java` | **Filtros combinados** (`filtraPedidosPorEstadoFechaYProductoPropio`, L68: estado+desde+hasta+productoId → 1 resultado), **producto ajeno** (`rechazaFiltrarPorProductoDeOtroVendedor`, L87 → 403), total solo con líneas propias, rol. |
| `controller/PedidosVendedorControllerTest.java` | Listado sin filtros (200). |
| `service/ExperienciaCompraServiceTest.java` | Filtros del cliente (HU 55) y **rango invertido de fechas** (`rechazaRangoDeFechasYMontosInvalidosEnHistorial`, L158 → 400). |
| `service/MetricasVentasServiceTest.java` | `ingresosConfirmados`/pedidos/promedio/top, **período anterior de igual duración sin solape** (L89), comparativas (`AUMENTO/IGUAL`, `variacionPorcentual=null` si anterior=0), rango invertido (L145 → 400), solo una fecha (L155 → 400), rol (L165), sesión (L177). |
| `controller/MetricasVentasControllerTest.java` | **Exclusión de `REGISTRADO`/`CANCELADO`** (L120), atribución por vendedor en pedidos compartidos (L134), fecha inclusiva por día (L200), cruce de año (L211), rango invertido con mensaje exacto (L224), 403/401. |
| `service/AlertaStockServiceTest.java` | **Cruce exacto en `<=`** (L54), no repetir mientras siga bajo (L67), **rearme al reponer** (L82), **segundo cruce tras reposición** (L95: 2 avisos), configurar con stock ya bajo sin aviso (L138), umbral 0/negativo/ajeno. |
| `controller/UmbralStockControllerTest.java` | CRUD del umbral (400/403/404/401), **cruce por venta y por edición manual** (L202, L220), no repetir (L232), **reposición + segundo cruce → 2 avisos** (L245), configurar con stock bajo → 0 avisos (L258), integración en la bandeja con `enlace` (L275), aislamiento entre vendedores (L292). |
| `service/PedidoNotificacionServiceTest.java` | `delegaEnAlertasElCruceDelUmbralCuandoLaVentaReduceElStock` (L79). |

**Huecos detectados (sin cubrir por pruebas; no se agregaron nuevas por
indicación):** no existe prueba HTTP de rango invertido ni de estado inválido
en `GET /api/vendedores/me/pedidos` (la de rango invertido es de servicio y
del filtro del cliente); ningún test aserta el mensaje exacto del aviso de
stock ni la clave `stock:{id}:{numero}`.

---

## 5. Verificación en vivo (HU 60.2, backend `:9000` + `BD_Pulgatec`, 2026-11-16)

**Datos creados para la verificación** (no tocan datos de otros historiales):
vendedor de prueba `prueba-hu60-…@test.com` (id 20) con productos
**57** "Teclado pruebas HU60" (S/50, stock 10) y **58** "Audifonos pruebas HU60"
(S/20, stock 5→4); cliente `prueba-hu594-…` compró 3 teclados (**pedido 24**,
S/150) que se llevó a `EN_PROCESO → ENTREGADO`, y 1 audífono (**pedido 25**,
S/20) que quedó en `REGISTRADO`.

### 5.1 Filtros combinados (`GET /api/vendedores/me/pedidos`)

| Caso | Resultado |
|---|---|
| Sin parámetros | `200` con 2 pedidos: `25:REGISTRADO`, `24:ENTREGADO` |
| **Combinado** `estado=ENTREGADO&desde=2026-11-16&hasta=2026-11-16&productoId=57` | `200` → **1 resultado: `24:ENTREGADO`** (AND de los 4 filtros) |
| `estado=en_proceso` (minúsculas) + `productoId=57` | `200` → 0 (normalización sin error; el 24 está ENTREGADO) |
| `estado=REGISTRADO` | `200` → `25:REGISTRADO` |
| `estado=ENTREGADO&productoId=58` (combinación sin coincidencia) | `200` → `[]` |
| `desde=hasta=2026-11-01` (fuera del rango) | `200` → `[]` |

### 5.2 Errores de filtro

| Caso | Resultado |
|---|---|
| **Rango invertido** `desde=2026-11-16&hasta=2026-11-01` | **`400`** — `La fecha inicial no puede ser posterior a la fecha final.` |
| `estado=VOLANDO` | **`400`** — `El estado indicado no es válido.` |
| **Producto ajeno** `productoId=49` (pertenece al vendedor 17) | **`403`** — `Solo puedes filtrar por tus propios productos.` |
| `productoId=0` | **`400`** — `El producto indicado no es válido.` |
| `productoId=999999` | **`404`** — `Producto no encontrado.` |
| `desde=manana` | **`400`** — `Los parámetros de consulta deben ser válidos.` (`Parámetro inválido`) |
| Llamada con sesión de **cliente** | **`403`** — `No tienes permiso para realizar esta acción.` |
| Sin Bearer | **`401`** — `Debes iniciar sesión.` |

### 5.3 Métricas (`GET /api/vendedores/me/metricas/ventas`)

| Caso | Resultado |
|---|---|
| Por defecto (sin parámetros) | `200` — `desde=2026-10-18`, `hasta=2026-11-16`, `desdeAnterior=2026-09-18`, `hastaAnterior=2026-10-17` (**30 días y 30 días, sin solape**) |
| **`ingresosConfirmados`** | **`150.00`** = 3 × S/50 del pedido 24 (solo líneas propias, precio congelado) |
| **`cantidadPedidos`** | **`1`** — el pedido 25 (`REGISTRADO`, S/20) **no cuenta**; `promedioPorPedido = 150.00` |
| `productosMasVendidos` | `Teclado pruebas HU60 — unidadesVendidas=3, importeAtribuido=150.00` |
| Período anterior | `ingresos=0.00`, `pedidos=0`, `promedio=0.00`; `comparativaIngresos.diferenciaAbsoluta=150.00`, `variacionPorcentual=null` (base 0), `tendencia=AUMENTO`; `comparativaPedidos.diferenciaAbsoluta=1.00`, `AUMENTO` |
| `compararPeriodoAnterior=false` | `periodoAnterior` y las 3 comparativas en **`null`** |
| **Rango invertido** | **`400`** — `La fecha inicial no puede ser posterior a la fecha final.` |
| Solo `desde` | **`400`** — `Indica las dos fechas del periodo: desde y hasta.` |

### 5.4 Umbral de stock (cruce, no repetición, reposición, segundo cruce)

| Paso | Acción | Resultado |
|---|---|---|
| 1 | `PUT /api/productos/58/umbral-stock {umbral:3}` (stock 4) | `200` `stockBajo=false`, `alertaNotificada=false`; **0 avisos** |
| 2 | `PUT /api/productos/57/umbral-stock {umbral:100}` (stock 10, ya por debajo) | `200` `stockBajo=true`, `alertaNotificada=true` y **0 avisos** (no aviso al configurar con stock ya bajo) |
| 3 | **Cruce** edición manual stock `4 → 2` | **1 aviso** (`idNotificacion=47`): `Stock bajo en "Audifonos pruebas HU60": quedan 2 unidades y el umbral configurado es 3.` (`stockActual=2`, `umbral=3`, `stockBajo=true`, `PENDIENTE`, enlace `/mis-publicaciones#producto-58`) |
| 4 | Sigue bajando `2 → 1` | **sigue 1 aviso** (no repite) |
| 5 | **Reposición** `1 → 6` | `stockBajo=false`, `alertaNotificada=false` (**rearmado**); **sigue 1 aviso** y no se creó ninguno nuevo |
| 6 | **Segundo cruce** `6 → 3` | **2 avisos** (`idNotificacion=48`): `… quedan 3 unidades y el umbral configurado es 3.` |
| 7 | `GET /api/vendedores/me/avisos-stock-bajo` | 2 avisos, ambos del producto 58; bandeja `?tipo=STOCK_BAJO` → `totalElementos=2` con los mensajes anteriores |
| 8 | `PUT` umbral del producto **49** (ajeno) | **`403`** — `El producto no pertenece al vendedor` |
| 9 | `PUT {umbral:-1}` | **`400`** — `El umbral no puede ser negativo` |
| 10 | `PUT` sin Bearer | **`401`** — `Debes iniciar sesión.` |

---

## 6. Resumen para QA

- Los cuatro filtros del vendedor (`estado`, `desde`, `hasta`, `productoId`)
  son **combinables (AND)** y se validan en el servidor con mensajes exactos;
  **rango invertido = `400`** (no lista vacía) y **producto ajeno = `403`**.
- `ingresosConfirmados` y `cantidadPedidos` cuentan **solo** pedidos en
  `EN_PROCESO`/`ENTREGADO`, solo las **líneas del vendedor**, con el precio
  congelado; el período anterior es de **misma duración sin solape**.
- El umbral usa **`stock <= umbral`**, avisa **una sola vez por cruce
  descendente**, **se rearma al reponer** y **configurar con stock ya bajo no
  avisa**.
- Web y Android consumen los mismos DTO; Android muestra además
  `promedioPorPedido`/`comparativaPromedio` y filtra los pedidos en cliente.
