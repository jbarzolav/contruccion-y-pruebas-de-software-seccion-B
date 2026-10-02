import axios from 'axios'

// Servicio del backend - Spring Boot en el puerto 8080
const api = axios.create({
  baseURL: 'http://localhost:8080/api/productos',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

export async function crearProducto(producto) {
  const { data } = await api.post('', producto)
  return data
}

export async function agregarImagenProducto(idProducto, imagen) {
  const { data } = await api.post(`/${idProducto}/imagenes`, imagen)
  return data
}

export default api