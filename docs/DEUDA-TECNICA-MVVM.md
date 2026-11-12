# Deuda técnica — Refactorización MVVM (HU01–HU58)

Registro de las deudas técnicas detectadas durante la refactorización a MVVM
con capa Repository. **Ninguna de estas deudas está implementada**: se
documentan aquí para que puedan planificarse en un ciclo posterior.

El documento no modifica ni sustituye la documentación de las historias de
usuario: solo deja constancia de los riesgos técnicos conocidos.

| ID    | Ámbito                  | Descripción                                                                                         | Estado      |
|-------|-------------------------|-----------------------------------------------------------------------------------------------------|-------------|
| DT-01 | Imágenes de producto    | Las imágenes se leen y decodifican en memoria en el hilo principal; no hay caché ni carga diferida.   | Pendiente  |
| DT-02 | Pruebas de interfaz     | No hay pruebas Compose (UI) para las pantallas; la cobertura es de ViewModel y repositorios.         | Pendiente  |
| DT-03 | Favoritos               | Los favoritos no tienen persistencia Offline First: solo se consultan en línea contra el backend.     | Pendiente  |
| DT-04 | Carrito de compras      | El carrito no tiene persistencia Offline First: no se guarda copia local ni se encola.               | Pendiente  |
| DT-05 | `POST /api/pedidos`     | El registro de pedido no es idempotente: no se puede encolar sin riesgo de duplicar el pedido.       | Pendiente  |
| DT-06 | Calificación y reseña   | La calificación y la reseña post-compra se guardan en el servidor, sin copia local persistida.       | Pendiente  |

## Contexto de DT-04 y DT-05

- El carrito (`GET/POST/PUT/DELETE /api/carrito/items`, `GET /api/carrito/total`)
  es una operación **en línea**: no existe entidad de carrito en Room, ni DAO,
  ni tipo de operación en la cola persistente. Por eso el carrito no aparece
  en `TiposOperacion` y WorkManager nunca lo reintenta.
- `POST /api/pedidos` **no** se encola a propósito. No es idempotente: un
  reintento automático (por ejemplo desde `SincronizacionWorker`) podría
  registrar el pedido dos veces y descontar el stock dos veces. El checkout
  sigue siendo bloqueante y en línea, igual que antes de la refactorización.
- Por el mismo motivo, la calificación (`POST /api/pedidos/{id}/calificacion`)
  y la reseña (`POST /api/pedidos/{id}/resena`) tampoco se encolan.

Cuando estas operaciones se automaticen, habrá que añadir antes un
mecanismo de idempotencia en el backend (por ejemplo, una clave de
idempotencia por intento de compra) y una entidad local que permita
reintentar sin duplicar efectos.
