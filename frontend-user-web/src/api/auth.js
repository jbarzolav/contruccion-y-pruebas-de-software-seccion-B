import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

// Servicio de autenticación (HU 21) - Spring Boot en el puerto 9000
const apiAuth = axios.create({
  baseURL: `${API_URL}/api/auth`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

// HU 21 - POST /api/auth/login → { token, id, correo }
// 401 si las credenciales son incorrectas (el backend usa el mismo
// mensaje para usuario inexistente y contraseña incorrecta).
export async function login(correo, password) {
  const { data } = await apiAuth.post('/login', { correo, password })
  return data
}

// HU 22 - Recuperación mediante enlace temporal de un solo uso.
export async function solicitarRecuperacion(correo) {
  const { data } = await apiAuth.post('/recuperacion', { correo })
  return data
}

export async function restablecerPassword(token, password, confirmacionPassword) {
  const { data } = await apiAuth.post('/restablecer', { token, password, confirmacionPassword })
  return data
}
// Sesión user autenticada contra Spring.
const apiPermisos = configurarSesion(axios.create({ baseURL: `${API_URL}/api`, timeout: 10000 }))
export async function obtenerSesion() {
  const { data } = await apiPermisos.get('/auth/me')
  return data
}
// HU25 - El token capturado se envía aunque la interfaz borre la sesión local inmediatamente.
export async function cerrarSesion(token) {
  const { data } = await apiPermisos.post('/auth/logout', {}, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return data
}
