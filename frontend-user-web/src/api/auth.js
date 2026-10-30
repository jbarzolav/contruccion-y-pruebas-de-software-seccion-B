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

// HU 26 - POST /api/auth/registro → { mensaje }
// 201 si la cuenta se crea; 409 si el correo ya está registrado.
export async function registrar(nombre, correo, password, confirmacionPassword) {
  const { data } = await apiAuth.post('/registro', { nombre, correo, password, confirmacionPassword })
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

// HU 27 - Perfil del usuario autenticado: GET /api/usuarios/me
export async function obtenerPerfil() {
  const { data } = await apiPermisos.get('/usuarios/me')
  return data
}

// HU 30 - Sube la foto del usuario de la sesión (límite 2 MB, JPG/PNG).
export async function actualizarFotoPerfil(archivo) {
  const formulario = new FormData()
  formulario.append('file', archivo)
  const { data } = await apiPermisos.post('/usuarios/me/foto', formulario)
  return data
}

export function urlFotoPerfil(ruta) {
  if (!ruta) return null
  return ruta.startsWith('http') ? ruta : `${API_URL}${ruta}`
}

// HU 28 - Edición de datos personales: PATCH /api/usuarios/me
// La identidad la decide el backend desde la sesión; el body solo envía
// los campos habilitados (nombre, correo, teléfono).
export async function actualizarPerfil(datos) {
  const { data } = await apiPermisos.patch('/usuarios/me', datos)
  return data
}

// HU 29 - Perfil comercial del vendedor autenticado (solo rol VENDEDOR).
export async function obtenerPerfilVendedor() {
  const { data } = await apiPermisos.get('/vendedores/me/perfil')
  return data
}

export async function guardarPerfilVendedor(datos) {
  const { data } = await apiPermisos.patch('/vendedores/me/perfil', datos)
  return data
}
// HU25 - El token capturado se envía aunque la interfaz borre la sesión local inmediatamente.
export async function cerrarSesion(token) {
  const { data } = await apiPermisos.post('/auth/logout', {}, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return data
}
