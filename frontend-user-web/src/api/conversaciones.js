import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

const api = configurarSesion(axios.create({ baseURL: `${API_URL}/api/conversaciones`, timeout: 15000 }))
export const configuracionConsultas = () => api.get('/configuracion').then(r => r.data)
export const listarConversaciones = () => api.get('').then(r => r.data)
export const enviarConsulta = (productoId, mensaje) => api.post('', { productoId, mensaje }).then(r => r.data)
export const responderConsulta = (id, mensaje) => api.post(`/${id}/mensajes`, { mensaje }).then(r => r.data)
