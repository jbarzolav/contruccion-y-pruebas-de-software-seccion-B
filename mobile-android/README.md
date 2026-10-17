# mobile-android

Aplicación PulgaTec Android — Jetpack Compose + Retrofit.

## Requisitos

- Android Studio (Ladybug o posterior)
- JDK 17 (lo trae Android Studio)
- `backend-user-api` Spring en el puerto **9000**
- `backend-admin` Django/DRF en el puerto **8001**

## URL del backend

| Entorno         | URL                      |
|-----------------|--------------------------|
| API user (Spring), emulador | `http://10.0.2.2:9000/` |
| API admin (Django), emulador | `http://10.0.2.2:8001/` |
| Dispositivo físico | Reemplazar `10.0.2.2` en `RetrofitClient.kt` y `AdminApi.kt` por la IP LAN del equipo |

> `10.0.2.2` es el alias del `localhost` de la PC desde el emulador de Android. Android mantiene una única `SessionManager`: el Bearer opaco emitido por Spring se adjunta tanto al `UserApiClient` como al `AdminApiClient`; Django verifica la identidad/rol contra Spring.
> El permiso de tráfico claro (`usesCleartextTraffic="true"`) ya está habilitado en el manifest.

## Estructura

```
app/src/main/java/com/ejemplo/publicarproducto/
├── MainActivity.kt
├── model/ProductoRequest.kt      # DTO enviado al backend
├── model/ProductoResponse.kt     # Respuesta y errores del backend
├── network/ProductoApi.kt        # API de usuario Spring
├── network/AuthApi.kt            # Login, sesión, recuperación y logout Spring
├── network/AdminApi.kt           # HU23/HU24 Django + cliente Retrofit admin
├── network/RetrofitClient.kt     # UserApiClient Spring y Bearer compartido
├── ui/PublicarProductoViewModel.kt   # Validaciones + estados carga/éxito/error
└── ui/PublicarProductoScreen.kt      # UI Jetpack Compose
```

## Validaciones (cliente)

- nombre y descripción requeridos
- precio > 0
- stock >= 0
- categoría requerida

## Estados manejados

`Idle` → `Loading` (botón "Publicando…") → `Success` (mensaje verde con ID) / `Error` (mensaje rojo del backend o de red).

## Cómo ejecutar

1. Abrir la carpeta `mobile-android/` en Android Studio.
2. Dejar corriendo Spring (`mvn spring-boot:run`) y Django (`python manage.py runserver 8001`).
3. Ejecutar en el emulador.
