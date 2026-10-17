# PulgaTec — panel administrativo

Aplicación React + Vite separada de `frontend-user-web`. Inicia sesión con la API de Spring existente; no hay cuentas ni credenciales administrativas paralelas.

## Desarrollo

```powershell
npm install
npm run dev
```

Por defecto usa Django en `http://localhost:8001` y login/validación de sesión Spring en `http://localhost:9000`. Ajustar `VITE_ADMIN_API_URL` y `VITE_USER_API_URL` según el despliegue.

El token opaco se guarda únicamente bajo una clave propia de esta aplicación y solo después de validar que `/api/auth/me` devuelve `ADMINISTRADOR`. La autorización real de cada operación sigue siendo Spring.
