import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

const api = configurarSesion(axios.create({ baseURL: `${API_URL}/api/notificaciones`, timeout: 10000 }))

export async function listarNotificaciones() {
  const { data } = await api.get('')
  return data
}

/** HU 53 - Página de la bandeja unificada con filtros combinables. */
export async function listarBandeja({ tipo, estado, desde, hasta, page } = {}) {
  const params = {}
  if (tipo) params.tipo = tipo
  if (estado) params.estado = estado
  if (desde) params.desde = desde
  if (hasta) params.hasta = hasta
  if (page !== undefined && page !== null) params.page = page
  const { data } = await api.get('/bandeja', { params })
  return data
}

/** HU 54 - Marca una notificación propia como leída (idempotente). */
export async function marcarNotificacionLeida(idNotificacion) {
  const { data } = await api.patch(`/${idNotificacion}/leida`)
  return data
}

/** HU 54 - Marca todas las notificaciones pendientes del usuario como leídas. */
export async function marcarTodasComoLeidas() {
  const { data } = await api.patch('/leidas')
  return data
}
