# frontend-user-web

Pantalla de publicación de productos (HU 01) — React + Vite + Axios.

## Requisitos

- Node.js 18 o superior
- Backend corriendo en `http://localhost:8080`

## Instalar y ejecutar

```bash
npm install      # instala React, Vite y Axios
npm run dev      # http://localhost:5173
```

## Componente

`src/components/PublicarProducto.jsx`

| Campo        | Validación en el cliente     |
|--------------|------------------------------|
| nombre       | requerido                    |
| descripcion  | requerido                    |
| precio       | requerido y > 0              |
| stock        | requerido y >= 0             |
| categoria    | requerido                    |
| estado       | select (DISPONIBLE/AGOTADO/INACTIVO) |

- Botón **Publicar** → `POST http://localhost:8080/api/productos` (Axios)
- Botón **Cancelar** → limpia el formulario
- Mensajes de éxito (`Producto registrado correctamente`) y de error (validaciones del backend o fallo de conexión).
