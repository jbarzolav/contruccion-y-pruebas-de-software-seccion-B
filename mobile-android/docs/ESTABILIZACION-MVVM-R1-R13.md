# Estabilización post-migración MVVM — hallazgos R1–R13

Documento de referencia de la intervención de estabilización de
`mobile-android` (no es una migración nueva ni un rediseño de paquetes).

- Alcance: únicamente `mobile-android`.
- Protegido: contratos HTTP, DTOs, Room (entidades, DAOs, converters,
  versión, migraciones y schemas exportados), Offline First, cola de
  operaciones, sincronización, WorkManager y los 58 comportamientos de
  usuario (HU01–HU58).
- Flujo obligatorio: **View → ViewModel → Repository → Room/Retrofit**.

---

## R1 / R3 — Sondeo de HU39: un solo responsable y solo en primer plano

`GET /api/conversaciones` lo sondea **un único ViewModel en cada momento**:

| Pantalla Consultas | App en primer plano | Quién sondea |
|-------------------|----------------------|--------------|
| activa            | sí                   | `ConsultasViewModel` (además refresca la conversación abierta) |
| activa            | no                   | nadie |
| otra              | sí                   | `AvisosConversacionViewModel` (aviso de la barra superior) |
| otra              | no                   | nadie |

### Piezas

- `view/navigation/ArbitrajeSondeo.kt` →
  - `debeSonearAvisosDeConversacion(consultasActiva, appEnPrimerPlano)`: regla
    **pura**, verificable en la JVM sin levantar la interfaz.
  - `consultasSonResponsable(estado)`: regla **pura** que decide si la
    pantalla de Consultas es la responsable del sondeo.
  - `accionAlSalirDeLaComposicion(usuarioActivo)`: regla **pura** que decide
    qué hacer con el sondeo al salir de la pantalla autenticada.
- `view/navigation/ObservadorPrimerPlano.kt` → `ObservadorPrimerPlano`: traduce
  `ON_START` / `ON_STOP` del `Activity` a un booleano y **notifica el cambio de
  forma síncrona**, dentro del propio callback del ciclo de vida. Es una clase
  aparte (y no un bloque dentro del Composable) para poder probarla sin Compose.
  El indicador es un `var` normal, **no** estado de Compose: así no puede
  volver a colgar la decisión de una recomposición.
- `view/navigation/CoordinadorSondeoAvisos.kt` → `CoordinadorSondeoAvisos`:
  mantiene el estado de la decisión y **aplica** la regla. Consulta la
  identidad del usuario y la pantalla visible **en el momento de decidir**
  (no guarda copias), de modo que una sesión caducada en segundo plano no
  pueda reanudarse con una identidad obsoleta.
- `view/navigation/AppNavegacion.kt` → cablea el observador con el coordinador
  y propaga el contexto de navegación.
- `view/ConsultasScreen.kt` → `LifecycleStartEffect(model) { iniciarSondeo();
  onStopOrDispose { detenerSondeo() } }`: el sondeo de la pantalla se liga al
  ciclo de vida STARTED.
- `viewmodel/SondeoPeriodico.kt` → abstracción del sondeo cancelable: una
  ejecución por intervalo, sin duplicados y con cancelación inmediata.

### Por qué la parada NO puede depender de una recomposición

La primera versión de este cierre aplicaba la decisión desde un
`LaunchedEffect(usuario.id, debeSonear)`, es decir, **durante la composición**.
La auditoría independiente lo marcó como **residual D1 (PARCIAL)** por un
motivo concreto: AndroidX documenta, en la versión exacta usada
(`androidx.lifecycle:lifecycle-runtime-compose:2.8.7`), que *«Compose stops
recomposing after receiving a `Lifecycle.Event.ON_STOP`»*, y `compose-ui` avisa
de que la recomposición *«may be throttled or paused while the Lifecycle is
not at least STARTED»*. Es decir: la recomposición que aplicaría `pausar()`
puede no llegar a producirse nunca, y el sondeo del badge seguiría activo con
la aplicación oculta.

La corrección mueve la decisión al **callback del ciclo de vida**:

```
LifecycleRegistry → ObservadorPrimerPlano (callback síncrono)
                  → CoordinadorSondeoAvisos (regla pura)
                  → AvisosConversacionViewModel.pausar() / iniciar()
```

`ON_STOP` → `pausar()` en el mismo instante, sin recomposición de por medio.
`ON_START` → se vuelve a decidir con el estado **actual** (no con una copia),
porque `CoordinadorSondeoAvisos` consulta `consultasActivasAhora()` en el
momento de decidir.

### Consultas activa = "realmente en pantalla"

`consultasActiva` no es solo `pestana == CONSULTAS`: cuando se abre una
subpantalla sobre la pestaña (detalle de producto, edición, publicación…),
la pantalla de Consultas sale de la composición y su `LifecycleStartEffect` se
descarta, con lo que su sondeo se detiene. Si el arbitraje no lo tuviera en
cuenta, en ese instante **no sondearía nadie** y el aviso de la barra superior
quedaría congelado hasta volver a la pestaña (hallazgo **M1** de la auditoría
independiente).

La condición vive en la función pura `consultasSonResponsable(estado)`:

```kotlin
fun consultasSonResponsable(estado: EstadoNavegacion): Boolean =
    estado.pestana == IndicesNavegacion.CONSULTAS && estado.enPantallaPrincipal
```

Se verifica en la JVM sobre un `EstadoNavegacion` **real** (no sobre un
booleano simulado): si alguien volviera a mirar solo la pestaña, la prueba
fallaría.

### Al salir de la pantalla autenticada

`accionAlSalirDeLaComposicion(usuarioActivo)` decide, con una regla pura, qué
se hace con el sondeo al salir de la composición:

| Situación                                 | Acción    | Efecto                                                        |
|-------------------------------------------|-----------|---------------------------------------------------------------|
| Sesión abierta (rotación, recreación)     | `PAUSAR`  | Se cancela el sondeo y **se conserva** la memoria de mensajes vistos y el aviso. |
| Sesión cerrada (cierre o caducidad)        | `DETENER` | Se cancela el sondeo y **se limpia todo**.                     |

### Comportamiento observable conservado

- El intervalo funcional sigue siendo **15 s** (`ConsultasViewModel.INTERVALO_SONDEO_MS`).
- La pantalla de Consultas **carga la lista al entrar** (`LaunchedEffect(Unit)
  { model.cargar(true) }`): el sondeo solo refresca, no sustituye esa carga.
- `ConsultasViewModel` hace su **primera** petición tras el intervalo (delay
  primero), igual que el `LaunchedEffect` original de la pantalla.
- `AvisosConversacionViewModel` hace su **primera** petición **inmediata**, para
  que el aviso de la barra superior no llegue tarde.
- Al pausar **no se borra** la memoria de los mensajes ya vistos: al reanudar se
  siguen detectando los mensajes que llegaron mientras tanto.
- Al **cambiar de usuario** la memoria se reinicia (lo gestiona `iniciar()`).
- En una **recreación de la Activity** (rotación) la sesión sigue abierta: ahí
  solo se pausa el sondeo y **se conserva** la memoria de los mensajes vistos,
  para no perder el aviso de la barra superior ni los mensajes que lleguen
  durante la recreación (hallazgo **M2** de la auditoría independiente). La
  limpieza total (`detener()`) se reserva para la salida de la sesión.

---

## R11 — Ruido de diff en los DTOs de `model/` (falsos positivos de Git)

**Diagnóstico.** Git marca como modificados **24** archivos de `model/`. De
ellos, **23 son falsos positivos**: el índice y el archivo del disco coinciden
byte a byte una vez normalizados los finales de línea. Lo que ocurre es que el
repositorio guarda LF y el directorio de trabajo de Windows guarda CRLF (por
`core.autocrlf=true`), de modo que `git status` compara el contenido
normalizado del disco con el contenido crudo del índice y lo marca como
modificado.

El **24º** archivo (`model/NotificacionResponse.kt`) sí tiene una diferencia
real, y es intencionada: las 10 líneas añadidas son el KDoc de trazabilidad
HU51/HU52/HU53/HU54/HU58 de **R12**. Los **10 campos `val` del `data class`
son idénticos a HEAD**: no hay ningún cambio de contrato HTTP.

**Causa concreta (medida).** `git ls-files --eol` sobre el repositorio:

| Índice | Disco de trabajo | Archivos |
|--------|------------------|----------|
| `i/lf` | `w/crlf` | 293 |
| `i/lf` | `w/lf`   | 70  |
| `-text`| `-text`       | 16 (binarios) |
| `i/lf` | `w/mixed`     | 1 (`mobile-android/app/build.gradle.kts`) |
| `i/none`| `w/none`     | 1 (`backend-admin/admin_api/__init__.py`, archivo vacío) |

**Decisión.** No se reescribe ni se restaura ningún DTO: hacerlo sería
"normalizar" el ruido solo en esta máquina y volvería a aparecer en la
siguiente. La corrección real es la de **R13** (`.gitattributes`), que hace
que el índice contenga siempre LF con independencia de la configuración
local del equipo.

**Verificación.** `git diff --numstat -- mobile-android/app/src/main/java/com/ejemplo/publicarproducto/model/`
devuelve **una sola línea**: `10 0 …/NotificacionResponse.kt` (el KDoc de R12).
Los otros 23 archivos no aparecen: su contenido estructural no se ha tocado
en esta intervención. La comprobación byte a byte (`git hash-object` frente a
`git rev-parse HEAD:<ruta>`) da **23/23 idénticos** para esos 23.

> **Nota operativa.** Mientras el índice no se renormalice, `git status`
> seguirá mostrando los 24 archivos como modificados aunque `git diff` esté
> vacío. Es ruido de la caché de estadísticas de Git, no contenido modificado:
> no afecta a lo que se versiona.

---

## R12 — Trazabilidad HU01–HU58

Huellas verificadas por etiqueta `HU <n>` en el código de `mobile-android`.

### Historias que ya tenían etiqueta y se mantienen

HU01–HU31, HU33–HU35, HU38, HU39, HU41–HU51, HU53, HU54, HU57, HU58.

### Historias a las que se ha añadido etiqueta en esta intervención

| HU    | Dónde                                                        | Qué aporta                                                    |
|-------|--------------------------------------------------------------|---------------------------------------------------------------|
| HU 32 | `model/repository/FavoritosRepositorio.kt`                   | Lista y eliminación de favoritos del cliente autenticado.     |
| HU 52 | `model/NotificacionResponse.kt`, `model/repository/NotificacionesRepositorio.kt`, `viewmodel/PedidosRecibidosViewModel.kt` | Aviso de nueva compra para cada vendedor con productos en el pedido. |
| HU 55 | `domain/FiltrosPedidos.kt`, `viewmodel/MisPedidosViewModel.kt` | Filtro y paginación de las compras del cliente autenticado.   |
| HU 56 | `domain/FiltrosVentas.kt`, `viewmodel/PedidosRecibidosViewModel.kt` | Filtro de pedidos y productos del vendedor autenticado.   |

Las etiquetas se deduced del contrato ya existente en los otros módulos del
repositorio (`backend-user-api` y `frontend-user-web`), de modo que la
trazabilidad es consistente entre los tres clientes:

- `backend-user-api/.../FavoritoController.java` → `HU31/HU32/HU33`.
- `backend-user-api/.../NotificacionService.java` → `HU 52`.
- `backend-user-api/.../ExperienciaCompraService.java` → `HU 55`.
- `backend-user-api/.../PedidosVendedorService.java` → `HU 56`.

### Historias SIN implementación en este repositorio

**HU36, HU37 y HU40** no tienen implementación ni referencia en ninguno de los
cinco módulos del repositorio:

```
git grep -n -E "HU[- ]?(36|37|40)\b" HEAD -- .   →  0 resultados
```

No hay endpoint en `backend-user-api`, ni componente en `frontend-user-web`,
ni pantalla, ViewModel, repositorio o prueba en `mobile-android`. **No se
inventa funcionalidad ni etiqueta**: quedan documentadas aquí como historias
sin cobertura en el alcance actual. Si el equipo de producto confirma su
alcance, se planifican como historias nuevas.

---

## R13 — `core.autocrlf` sin `.gitattributes`

**Problema.** El repositorio no fijaba la normalización de finales de línea,
de modo que el comportamiento dependía de la configuración local de cada
equipo (`core.autocrlf=true` en Windows). Eso es lo que produce el ruido de
diff descrito en R11.

**Solución aplicada.** `.gitattributes` en la raíz del repositorio:

- `* text=auto` — normalización automática; Git decide qué es texto.
- Tipos de texto frecuentes declarados explícitamente (`.kt`, `.kts`,
  `.java`, `.xml`, `.json`, `.md`, …).
- Tipos binarios declarados `-text` (`.png`, `.jpg`, `.jar`, `.aar`, …).

**Lo que NO se hace y por qué.** No se fija `eol=lf` (ni `eol=crlf`): con
`eol=` Git reescribe el directorio de trabajo y los ~293 archivos que hoy
tienen CRLF pasarían a tener LF, generando un diff masivo sin ningún
beneficio funcional. La normalización se aplica en el **índice**, que es
donde se decide el contenido que se versiona.

**Comprobación de seguridad.** Tras crear el archivo, `git status --porcelain`
no mostró ninguna modificación nueva: los 88 borrados, los 28 modificados y
los 21 no rastreados preexistentes se mantienen, y solo se añade el propio
`.gitattributes` como no rastreado. Los schemas exportados de Room
(`app/schemas/**.json`) **no se han modificado**: su contenido estructural es
intocable.

**Estado final del árbol de trabajo** (139 rutas): 88 ` D` + 28 ` M` + 23 `??`.
Los 23 no rastreados son los 21 preexistentes más `.gitattributes` (R13) y
`mobile-android/docs/` (este documento). Los 4 módulos ajenos
(`backend-user-api`, `backend-admin`, `frontend-user-web`, `frontend-admin-web`)
no tienen **ninguna** ruta modificada.

---

## Decisiones de alcance de ViewModel (R7)

| Pantalla                     | Alcance                          | Motivo                                                                 |
|------------------------------|----------------------------------|------------------------------------------------------------------------|
| 20 pantallas restantes       | `Activity` (`viewModel()`)       | Criterio mayoritario: estado compartido solo cuando hace falta.        |
| `ConsultasScreen`            | Propio (`ViewModelStore`)        | `ConsultaProducto` (detalle de producto) necesita SU propio `ConsultasViewModel`: compartirlo con la pestaña haría que enviar un mensaje desde el detalle cambiara el estado de la pestaña. |
| `BandejaNotificacionesScreen` | `Activity` (`viewModel()`)       | Antes tenía store propio y perdía filtros y paginación al salir de la pestaña. |

`MainActivity` conserva dos ViewModels de alcance de `Activity` porque su
estado es deliberadamente compartido: `SesionViewModel` (el estado de
sesión de toda la aplicación) y `AvisosConversacionViewModel` (el aviso de
mensaje nuevo de la barra superior).

---

### Incidente: corrupción detectada y reparada en `model/local/Daos.kt`

Durante la última iteración, el build falló con
`[ksp] [MissingType]: Element 'com.ejemplo.publicarproducto.model.local.PulgaTecDatabase' references a type that is not present`.

La causa no era el código de la intervención: el archivo
`app/src/main/java/com/ejemplo/publicarproducto/model/local/Daos.kt` tenía un
byte espurio (`0x7C`, `|`) **prependido** al inicio del archivo. Eso rompía la
sintaxis del archivo, los tipos de los DAO dejaban de existir y Room no
podía resolver las entidades del `@Database`.

Reparación: se eliminó **únicamente** ese byte. El resto del archivo quedó
intacto (563 líneas, los 8 DAO: `NotificacionDao`, `BandejaDao`, `PedidoDao`,
`ProductoDao`, `OperacionPendienteDao`, `MetadatoSincronizacionDao`,
`MetricasDao`, `UmbralStockDao`).

Verificación posterior:

- el build de KSP/Room vuelve a pasar;
- los **3 schemas exportados siguen siendo byte-idénticos a HEAD**
  (`30944272…`, `7f5eaa21…`, `63317164…`), lo que confirma que el
  contenido estructural de la base no cambió;
- toda la batería de pruebas queda en verde.

Un barrido de los archivos `.kt`/`.kts`/`.xml`/`.json` del módulo no encontró
**ningún otro** archivo con un inicio anómalo.

---

## Evidencia de pruebas (R1–R13)

Ningún hallazgo se da por cerrado solo por modificar código: cada uno tiene
una prueba que lo verifica.

### Pruebas unitarias (JVM) — 280 tests, 0 fallos

| Suite nueva                                        | Tests | Qué cubre                                                        |
|----------------------------------------------------|-------|------------------------------------------------------------------|
| `viewmodel/SondeoPeriodicoTest.kt`                  | 6     | R1/R3 — intervalo, una ejecución por intervalo, no duplicación, cancelación inmediata, fallo puntual, propagación de `CancellationException`. |
| `viewmodel/AvisosConversacionSondeoTest.kt`         | 7     | R1/R3/M2 — `pausar()` cancela el trabajo, conserva la memoria de mensajes vistos al reanudar, no duplica el sondeo, reinicia la memoria al cambiar de usuario, y la rotación conserva el aviso mientras que la salida de sesión limpia todo. |
| `view/navigation/ArbitrajeSondeoTest.kt`            | 8     | R1/R3/M1/M2 — la regla de arbitraje en sus cuatro combinaciones, la garantía de un único responsable, `consultasSonResponsable` sobre un `EstadoNavegacion` **real** (cualquier subpantalla abierta libera el sondeo de Consultas) y `accionAlSalirDeLaComposicion` en sus dos ramas. |
| `view/navigation/CoordinadorSondeoAvisosTest.kt`    | 13    | R1/R3/M1/M2 — la decisión se aplica de forma **síncrona**, la pantalla visible y la identidad del usuario se consultan **en el momento** de decidir, el badge cede el sondeo a la pantalla de Consultas, el badge asume el relevo cuando una subpantalla tapa Consultas, la identidad se propaga al cambiar de cuenta y una sesión caducada impide reanudar el sondeo. |
| `model/session/SessionManagerUsuarioReactivoTest.kt`| 7     | R2 — `usuarioIdFlow` publica la identidad guardada, la de `guardarSesion`/`actualizarUsuario`, `0` al invalidar, y solo emite cuando cambia. |
| `model/remote/PoliticaErroresAutorizacionTest.kt`  | 10    | R4 — matriz 401/403, token ausente, token distinto, rutas públicas y códigos no relacionados. |
| `architecture/CapasMvvmTest.kt`                     | 21    | R1–R7, R9, R10 — verificación estática: ningún ViewModel importa `view`, ni vistas ni ViewModels tocan DAO/Room/Retrofit/WorkManager, ninguna vista tiene bucles de sondeo, un único responsable del sondeo, **la parada del sondeo se decide en el callback del ciclo de vida** (y no en una recomposición), **el arbitraje usa la función pura `consultasSonResponsable`**, **la salida usa la decisión centralizada `accionAlSalirDeLaComposicion`**, identidad reactiva, invalidación centralizada, parada del observador y destino seguro. |
| `view/navigation/DestinosNavegacionTest.kt` (ext.)  | +3    | R10 — `indicePantallaPrincipalSeguro` y `IndicesNavegacion.TODOS`. |

### Pruebas instrumentadas (emulador) — 293 tests, 0 fallos

| Suite nueva                                       | Tests | Qué cubre                                                        |
|----------------------------------------------------|-------|------------------------------------------------------------------|
| `viewmodel/SondeoConsultasInstrumentadoTest.kt`    | 2     | R1/R3 — contra HTTP real (MockWebServer): una petición por intervalo, `iniciarSondeo()` dos veces no duplica, `detenerSondeo()` cancela y no queda ninguna petición viva, y el sondeo se cancela al destruirse el ViewModel. |
| `viewmodel/CambioUsuarioOfflineFirstTest.kt`       | 2     | R2 — A → B → cierre de sesión con Room real: se limpian los datos del usuario anterior, se observan los del nuevo y con la sesión cerrada no se muestra información protegida. |
| `viewmodel/ExpiracionSesionCentralizadaTest.kt`    | 4     | R4/R8 — `PerfilViewModel` publica el evento sin invalidar la sesión por su cuenta, `SesionViewModel` invalida al recibirlo, un 401 repetido no duplica aviso ni invalidación, y un 401 sin sesión no hace nada. |
| `viewmodel/SondeoPrimerPlanoTest.kt`               | 4     | R1/M2 — con un `LifecycleRegistry` real y el **cableado real** (`LifecycleRegistry → ObservadorPrimerPlano → CoordinadorSondeoAvisos → AvisosConversacionViewModel`): `ON_START`/`ON_STOP` mueven el indicador y se notifican de forma síncrona, **no hay peticiones HTTP con la app en segundo plano** (sin llamar a `pausar()` a mano), el sondeo se reanuda al volver al primer plano, con la pantalla de Consultas visible el ViewModel de avisos no sondea y una recreación de la Activity conserva la memoria de los mensajes vistos. |

### Batería completa ejecutada

```
clean assembleDebug              BUILD SUCCESSFUL
assembleDebugAndroidTest         BUILD SUCCESSFUL
testDebugUnitTest                BUILD SUCCESSFUL   280 tests, 0 failures
connectedDebugAndroidTest        BUILD SUCCESSFUL   293 tests, 0 failures
```

Baseline de partida: **205** unitarias + **281** instrumentadas = 486.
Cierre de la intervención: **280** unitarias + **293** instrumentadas = 573
(+87 pruebas nuevas, ninguna prueba previa suprimida: `skipped=0` en ambas
suites).

### Prueba de mutación (evidencia de que las pruebas no son tautológicas)

Para descartar que las pruebas sean tautológicas, se introdujeron mutaciones
temporales en producción y se comprobó que las suites **fallan** en cada caso:

| Mutación temporal                                                        | Resultado de la suite                                                        |
|--------------------------------------------------------------------------|------------------------------------------------------------------------------|
| El observador deja de notificar el cambio (`alCambiar` no se llama)      | **FAILED** — `elObservadorReflejaElPrimerPlanoDeLaAplicacion` (`expected:<[false, true, false, true]> but was:<[false]>`) y `noHayPeticionesHttpConLaAplicacionEnSegundoPlano` (`En primer plano debe haber peticiones`). |
| El coordinador no re-evalúa al pasar a segundo plano (simula H1)          | **FAILED** — `noHayPeticionesHttpConLaAplicacionEnSegundoPlano` (`expected:<1> but was:<3>`: 3 peticiones con la app oculta). |
| `consultasSonResponsable` vuelve a mirar solo la pestaña (reintroduce M1) | **FAILED** — 3 pruebas: `la pantalla de Consultas solo es responsable si esta realmente en pantalla`, `cualquier subpantalla libera el sondeo de Consultas` y `elArbitrajeUsaLaFuncionPuraDeConsultasResponsable`. **Antes de esta corrección ninguna prueba lo detectaba.** |
| `accionAlSalirDeLaComposicion` devuelve siempre `DETENER` (reintroduce M2) | **FAILED** — `al salir con la sesion abierta solo se pausa y al cerrarla se limpia todo`. |

Todas las mutaciones se **revirtieron** y el código de producción no contiene
ninguna (`grep MUTACION` en `app/src` → 0 resultados).

### Schemas de Room

`app/schemas/com.ejemplo.publicarproducto.model.local.PulgaTecDatabase/{1,2,3}.json`
tienen exactamente el mismo contenido que el baseline (blob SHA-1 `30944272…`,
`7f5eaa21…`, `63317164…`, comparados con `git hash-object` contra
`git ls-tree HEAD`): no se recreó la base ni se modificaron las migraciones.

### Estado del árbol de trabajo

`HEAD` = `cfc9ddcde41e7114ec5cb37865a14f6f48264e5d`, rama `main`.
`git status --porcelain` = **139 rutas** (88 ` D` + 28 ` M` + 23 `??`).
Los 4 módulos ajenos (`backend-user-api`, `backend-admin`, `frontend-user-web`,
`frontend-admin-web`) no tienen **ninguna** ruta modificada. De los 24 DTOs
marcados como ` M`, solo `NotificacionResponse.kt` tiene diff real
(`git diff --numstat` → `10 0`); los otros 23 son ruido de la caché de
estadísticas de Git (D3).

---

## Riesgos residuales aceptados

| # | Riesgo | Severidad | Por qué se acepta / mitigación |
|---|--------|-----------|-------------------------------|
| RR-1 | Ventana de un frame en el relevo entre el sondeo de la pantalla de Consultas y el del badge: al cambiar de pestaña, la pantalla se descarta y el badge arranca en el siguiente turno de composición. | Baja | En ese intervalo **no** hay dos sondeos a la vez (la pantalla se detiene en el mismo *apply* en que se descarta); lo que puede haber es un frame sin sondeo. El aviso del badge es la primera petición inmediata al volver, así que no se pierde información. |
| RR-2 | La rotación pierde la pestaña y la subpantalla activas: `EstadoNavegacion` usa `remember`, no `rememberSaveable`. | Baja (preexistente) | Fuera del alcance de R1–R13: cambiarlo alteraría el comportamiento de navegación observable. No afecta al sondeo (el badge reanuda con la pestaña por defecto). |
| RR-3 | `idsMensajesVistos` crece de forma monótona durante una sesión larga. | Baja (preexistente) | Limitado al número de mensajes recibidos en una sesión; se limpia al cambiar de usuario y al cerrar sesión. Podría podarse por conversación. |
| RR-4 | El badge solo recuerda el **último** mensaje recibido (`avisoMensaje` es un único `String?`). | Baja (preexistente) | Es el comportamiento original de HU39; no se ha alterado. |
| RR-5 | "Primer plano" se define con `ON_START`/`ON_STOP`: una Activity pausada pero no detenida (pantalla apagada sin `ON_STOP`) sigue sondeando. | Baja | Es la misma semántica que `repeatOnLifecycle(STARTED)`, el estándar de AndroidX. Documentado como límite conocido. |
| RR-6 | No hay pruebas de interfaz (Compose) en el proyecto. | Informativa | Ya estaba registrado en `docs/DEUDA-TECNICA-MVVM.md` (DT-02). La deuda no se ha cerrado, pero el arbitraje se ha extraído a clases puras y testeables para no depender de la UI. |
| RR-7 | `docs/DEUDA-TECNICA-MVVM.md` (no rastreado, fuera de `mobile-android/`) no se ha modificado. | Informativa | Estado preexistente del árbol de trabajo; no forma parte del alcance autorizado. |

