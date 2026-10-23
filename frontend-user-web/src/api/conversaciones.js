import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

const api = configurarSesion(axios.create({ baseURL: `${API_URL}/api/conversaciones`, timeout: 15000 }))
export const configuracionConsultas = () => api.get('/configuracion').then(r => r.data)
// HU38 - El listado trae el último mensaje; el historial se solicita por páginas.
export const listarConversaciones = () => api.get('').then(r => r.data)
export const listarMensajesConversacion = (id, pagina = 0, tamano = 20) =>
  api.get(`/${id}/mensajes`, { params: { pagina, tamano } }).then(r => r.data)
export const enviarConsulta = (productoId, mensaje) => api.post('', { productoId, mensaje }).then(r => r.data)
export const responderConsulta = (id, mensaje) => api.post(`/${id}/mensajes`, { mensaje }).then(r => r.data)
