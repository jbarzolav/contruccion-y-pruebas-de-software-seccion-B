import axios from 'axios'

export const USER_API_URL = import.meta.env.VITE_USER_API_URL || 'http://localhost:9000'
export const ADMIN_API_URL = import.meta.env.VITE_ADMIN_API_URL || 'http://localhost:8001'
const TOKEN_KEY = 'pulgatec_admin_token'

const authApi = axios.create({ baseURL: `${USER_API_URL}/api/auth`, timeout: 10000 })
const adminApi = axios.create({ baseURL: `${ADMIN_API_URL}/api/admin`, timeout: 10000 })

adminApi.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export async function iniciarSesion(correo, password) {
  const { data } = await authApi.post('/login', { correo, password })
  const { data: usuario } = await authApi.get('/me', {
    headers: { Authorization: `Bearer ${data.token}` },
  })
  if (usuario.rol !== 'ADMINISTRADOR') {
    throw Object.assign(new Error('Esta cuenta no tiene acceso al panel administrativo.'), {
      response: { status: 403 },
    })
  }
  localStorage.setItem(TOKEN_KEY, data.token)
  return usuario
}

export async function restaurarSesion() {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return null
  try {
    const { data } = await authApi.get('/me', { headers: { Authorization: `Bearer ${token}` } })
    if (data.rol !== 'ADMINISTRADOR') throw Object.assign(new Error('Acceso denegado'), { response: { status: 403 } })
    return data
  } catch (error) {
    if ([401, 403].includes(error.response?.status)) localStorage.removeItem(TOKEN_KEY)
    throw error
  }
}

export async function cerrarSesion() {
  const token = localStorage.getItem(TOKEN_KEY)
  try {
    if (token) await authApi.post('/logout', {}, { headers: { Authorization: `Bearer ${token}` } })
  } finally {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export async function listarUsuarios() {
  return (await adminApi.get('/usuarios')).data
}

export async function cambiarRol(id, rol) {
  return (await adminApi.patch(`/usuarios/${id}/rol`, { rol })).data
}

export async function listarAuditoria(page, size = 20) {
  return (await adminApi.get('/auditoria', { params: { page, size } })).data
}

export function mensajeError(error) {
  const code = error.response?.status
  if (code === 401) return 'La sesión venció o no es válida. Inicia sesión nuevamente.'
  if (code === 403) return 'No tienes permisos de administrador para esta acción.'
  return error.response?.data?.messages?.[0] || error.response?.data?.error || error.message || 'Ocurrió un error inesperado.'
}
