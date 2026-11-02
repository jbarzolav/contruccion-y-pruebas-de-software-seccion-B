import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

const apiPedidosVendedor = configurarSesion(axios.create({
  baseURL: `${API_URL}/api/vendedores/me/pedidos`,
  timeout: 10000,
}))

// HU 48 - El backend deriva al vendedor de la sesión.
export async function listarPedidosRecibidos() {
  const { data } = await apiPedidosVendedor.get('')
  return data
}
