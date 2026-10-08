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

// Servicio del carrito de compras (HU 11 / HU 12) - vive en /api/carrito
const apiCarrito = axios.create({
  baseURL: 'http://localhost:8080/api/carrito',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// Servicio de pedidos (HU 15 - Confirmar mi compra)
const apiPedidos = axios.create({
  baseURL: 'http://localhost:8080/api/pedidos',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// Servicio del chatbot (HU 16 - recomendaciones)
const apiChatbot = axios.create({
  baseURL: 'http://localhost:8080/api/chatbot',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
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

// HU 05 - GET /api/vendedores/me/productos?vendedorId=1
export async function obtenerMisProductos(vendedorId = 1) {
  const { data } = await axios.get('http://localhost:8080/api/vendedores/me/productos', {
    params: { vendedorId },
  })
  return data
}

// HU 06 / HU 09 - GET /api/productos?nombre={texto}&sort={criterio}
export async function buscarProductosPorNombre(nombre, sort = '') {
  const params = { nombre: nombre.trim() }

  if (sort) {
    params.sort = sort
  }

  const { data } = await api.get('', { params })
  return data
}

// HU 07 - GET /api/productos/{id} (detalle completo)
export async function obtenerProducto(idProducto) {
  const { data } = await api.get(`/${idProducto}`)
  return data
}

// HU 07 - GET /api/productos/catalogo (solo DISPONIBLE)
export async function obtenerCatalogo() {
  const { data } = await api.get('/catalogo')
  return data
}

// HU 07 - ruta de la imagen: las locales van contra el backend (:8080)
export function urlImagen(imagenUrl) {
  if (!imagenUrl) return null
  if (imagenUrl.startsWith('http')) return imagenUrl
  return `http://localhost:8080${imagenUrl}`
}

// HU 10 - GET /api/productos/{idProducto}/disponibilidad
export async function obtenerDisponibilidadProducto(idProducto) {
  const { data } = await api.get(`/${idProducto}/disponibilidad`)
  return data
}

// HU 11 - POST /api/carrito/items  { productoId, cantidad } → 201 (400 si excede stock)
export async function agregarAlCarrito(productoId, cantidad) {
  const { data } = await apiCarrito.post('/items', { productoId, cantidad })
  return data
}

// HU 12 - GET /api/carrito/items → lista de ítems con su subtotal
export async function obtenerCarrito() {
  const { data } = await apiCarrito.get('/items')
  return data
}

// HU 12 - PUT /api/carrito/items/{idItemCarrito} { cantidad } → 200 con subtotal recalculado
export async function actualizarCantidadItem(idItemCarrito, cantidad) {
  const { data } = await apiCarrito.put(`/items/${idItemCarrito}`, { cantidad })
  return data
}

// HU 13 - DELETE /api/carrito/items/{idItemCarrito}
export async function eliminarItemCarrito(idItemCarrito) {
  await apiCarrito.delete(`/items/${idItemCarrito}`)
}

// HU 14 - GET /api/carrito/total
export async function obtenerTotalCarrito() {
  const { data } = await apiCarrito.get('/total')
  return data
}

// HU 15 - POST /api/pedidos → 201 con el comprobante (idPedido, total, detalles)
export async function confirmarCompra() {
  const { data } = await apiPedidos.post('')
  return data
}

// HU 16 - POST /api/chatbot/recomendaciones → 200 {mensaje, productos}
export async function consultarChatbot(consulta) {
  const { data } = await apiChatbot.post('/recomendaciones', { consulta })
  return data
}
