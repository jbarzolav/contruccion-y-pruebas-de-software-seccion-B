import axios from 'axios'

// Servicio de autenticación (HU 21) - Spring Boot en el puerto 9000
const apiAuth = axios.create({
  baseURL: 'http://localhost:9000/api/auth',
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