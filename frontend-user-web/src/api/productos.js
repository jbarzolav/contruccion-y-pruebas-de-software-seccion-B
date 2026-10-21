import axios from 'axios'
import { API_URL, configurarSesion, usuarioActualId } from './sesion.js'

// Servicio JSON (HU 01 / HU 03) - Spring Boot en el puerto 9000
const api = axios.create({
  baseURL: `${API_URL}/api/productos`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// Servicio para subir archivos (HU 02).
// NO se fija Content-Type: el navegador debe poner el boundary del multipart.
const apiArchivos = axios.create({
  baseURL: `${API_URL}/api/productos`,
  timeout: 20000,
})

// Servicio del carrito de compras (HU 11 / HU 12) - vive en /api/carrito
const apiCarrito = axios.create({
  baseURL: `${API_URL}/api/carrito`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// Servicio de pedidos (HU 15 - Confirmar mi compra)
const apiPedidos = axios.create({
  baseURL: `${API_URL}/api/pedidos`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// Servicio del chatbot (HU 16 - recomendaciones)
const apiChatbot = axios.create({
  baseURL: `${API_URL}/api/chatbot`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// Servicio de recomendaciones por búsqueda (HU 20)
const apiRecomendaciones = axios.create({
  baseURL: `${API_URL}/api/recomendaciones`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// HU 31 - Favoritos personales; usa la sesión para limitar cada lista al cliente autenticado.
const apiFavoritos = configurarSesion(axios.create({
  baseURL: `${API_URL}/api/usuarios/me/favoritos`,
  timeout: 10000,
}))

// HU 23 - Autenticación en publicaciones, imágenes, carrito y pedidos.
for (const servicio of [api, apiArchivos, apiCarrito, apiPedidos]) configurarSesion(servicio)
const apiVendedor = configurarSesion(axios.create({ baseURL: `${API_URL}/api/vendedores`, timeout: 10000 }))

// HU 01 - POST /api/productos
export async function crearProducto(producto) {
  const { data } = await api.post('', { ...producto, vendedorId: usuarioActualId() })
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
export async function actualizarProducto(idProducto, producto, vendedorId = usuarioActualId()) {
  const { data } = await api.put(`/${idProducto}`, producto, {
    params: { vendedorId },
  })
  return data
}

// HU 04 - PATCH /api/productos/{id}/estado (baja lógica, sin eliminar)
export async function retirarProducto(idProducto, vendedorId = usuarioActualId()) {
  const { data } = await api.patch(
      `/${idProducto}/estado`,
      { estado: 'RETIRADO' },
      { params: { vendedorId } }
  )
  return data
}

// HU 05 - GET /api/vendedores/me/productos?vendedorId=1
export async function obtenerMisProductos(vendedorId = usuarioActualId()) {
  const { data } = await apiVendedor.get('/me/productos', {
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

// HU 31 - GET /api/usuarios/me/favoritos
export async function obtenerFavoritos() {
  const { data } = await apiFavoritos.get('')
  return data
}

// HU 35 - Consulta el estado de un producto favorito sin descargar la lista.
export async function consultarEstadoFavorito(idProducto) {
  const { data } = await apiFavoritos.get(`/${idProducto}/estado`)
  return data
}

// HU 31 - POST /api/usuarios/me/favoritos/{idProducto} (idempotente)
export async function agregarFavorito(idProducto) {
  const { data } = await apiFavoritos.post(`/${idProducto}`)
  return data
}

// HU 33 - elimina únicamente la asociación del usuario autenticado.
export async function quitarFavorito(idProducto) {
  await apiFavoritos.delete(`/${idProducto}`)
}

// HU 07 - ruta de la imagen: las locales van contra el backend (:9000)
export function urlImagen(imagenUrl) {
  if (!imagenUrl) return null
  if (imagenUrl.startsWith('http')) return imagenUrl
  return `${API_URL}${imagenUrl}`
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

// HU 17 - POST /api/chatbot/consultas-tecnicas
// Responde preguntas técnicas sobre componentes electrónicos.
export async function consultarChatbotTecnico(consulta) {
  const { data } = await apiChatbot.post('/consultas-tecnicas', { consulta })
  return data
}

// HU 18 - GET /api/planes/premium
export async function consultarPlanPremium() {
  const { data } = await axios.get(
    `${API_URL}/api/planes/premium`,
    { timeout: 10000 }
  )
  return data
}

// HU 19 - PATCH /api/productos/{id}/destacado (solo vendedores con plan Premium)
// 200 con el producto actualizado; 403 si el vendedor no tiene el beneficio.
//   destacado = true  -> activar · false -> desactivar · null -> alternar
export async function gestionarDestacado(idProducto, vendedorId = usuarioActualId(), destacado = null) {
  const cuerpo = destacado === null ? null : { destacado }
  const { data } = await api.patch(`/${idProducto}/destacado`, cuerpo, {
    params: { vendedorId },
  })
  return data
}

// HU 20 - GET /api/recomendaciones?termino={texto}
// Devuelve productos DISPONIBLES relacionados por nombre o categoría,
// sin duplicados; lista vacía si el término no coincide.
export async function obtenerRecomendaciones(termino) {
  const { data } = await apiRecomendaciones.get('', { params: { termino } })
  return data
}
