import axios from 'axios'

// Servicio JSON (HU 01 / HU 03) - Spring Boot en el puerto 8080
const api = axios.create({
  baseURL: 'http://localhost:8080/api/productos',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// Servicio para subir archivos (HU 02).
// NO se fija Content-Type: el navegador debe poner el boundary del multipart.
const apiArchivos = axios.create({
  baseURL: 'http://localhost:8080/api/productos',
  timeout: 20000,
})

// HU 01 - POST /api/productos
export async function crearProducto(producto) {
  const { data } = await api.post('', producto)
  return data
}

// HU 02 - POST /api/productos/{id}/imagenes (multipart/form-data con el archivo real)
export async function subirImagenProducto(idProducto, file, vendedorId) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('vendedorId', String(vendedorId))

  const { data } = await apiArchivos.post(`/${idProducto}/imagenes`, formData)
  return data
}

// HU 03 - PUT /api/productos/{id}?vendedorId=1
export async function actualizarProducto(idProducto, producto, vendedorId = 1) {
  const { data } = await api.put(`/${idProducto}`, producto, {
    params: { vendedorId },
  })
  return data
}

// HU 04 - PATCH /api/productos/{id}/estado (baja lógica, sin eliminar)
export async function retirarProducto(idProducto, vendedorId = 1) {
  const { data } = await api.patch(
      `/${idProducto}/estado`,
      { estado: 'RETIRADO' },
      { params: { vendedorId } }
  )
  return data
}

// HU 05 - GET /api/productos/mis-productos?vendedorId=1
export async function obtenerMisProductos(vendedorId = 1) {
  const { data } = await api.get('/mis-productos', {
    params: { vendedorId },
  })
  return data
}

export default api