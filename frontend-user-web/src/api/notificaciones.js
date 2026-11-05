import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

const api = configurarSesion(axios.create({ baseURL: `${API_URL}/api/notificaciones`, timeout: 10000 }))

export async function listarNotificaciones() {
  const { data } = await api.get('')
  return data
}
