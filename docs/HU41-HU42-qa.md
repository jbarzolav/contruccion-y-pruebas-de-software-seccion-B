# QA HU41/HU42

## Validación automatizada

- `backend-user-api`: 283 tests exitosos, 0 fallos.
- `frontend-user-web`: `npm run build` exitoso.
- `mobile-android`: `assembleDebug` exitoso.

## Validación manual pendiente

La validación E2E contra MariaDB y el cruce Web ↔ Android requiere un entorno
con MariaDB `BD_Pulgatec` disponible en `localhost:3306`, credenciales QA y un
emulador Android accesible mediante `adb`. En la ejecución local, MariaDB y
`adb` no estuvieron disponibles; por eso no se simularon resultados manuales.

El arranque con el perfil `mysql` confirmó que Spring usa el driver MariaDB,
pero terminó con `Connection refused` al no existir un servicio escuchando en
el puerto 3306.
