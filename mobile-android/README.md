# mobile-android

Pantalla de publicación de productos (HU 01) — Android + Jetpack Compose + Retrofit.

## Requisitos

- Android Studio (Ladybug o posterior)
- JDK 17 (lo trae Android Studio)
- Backend corriendo en el puerto **8080**

## URL del backend

| Entorno         | URL                      |
|-----------------|--------------------------|
| Emulador        | `http://10.0.2.2:8080/`  |
| Dispositivo físico | `http://<IP-de-tu-PC>:8080/` |

> `10.0.2.2` es el alias del `localhost` de la PC desde el emulador de Android.
> El permiso de tráfico claro (`usesCleartextTraffic="true"`) ya está habilitado en el manifest.

## Estructura

```
app/src/main/java/com/ejemplo/publicarproducto/
├── MainActivity.kt
├── model/ProductoRequest.kt      # DTO enviado al backend
├── model/ProductoResponse.kt     # Respuesta y errores del backend
├── network/ProductoApi.kt        # Interfaz Retrofit (POST api/productos)
├── network/RetrofitClient.kt     # Cliente Retrofit + OkHttp (10.0.2.2)
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
2. Dejar corriendo `backend-user-api` (`mvn spring-boot:run`).
3. Ejecutar en el emulador.
