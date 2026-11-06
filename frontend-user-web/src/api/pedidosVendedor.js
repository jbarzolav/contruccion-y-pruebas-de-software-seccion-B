import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

const apiPedidosVendedor = configurarSesion(axios.create({
  baseURL: `${API_URL}/api/vendedores/me/pedidos`,
  timeout: 10000,
}))

// HU 49 - El estado del pedido se actualiza sobre /api/pedidos/{id}/estado.
const apiPedidos = configurarSesion(axios.create({
  baseURL: `${API_URL}/api/pedidos`,
  timeout: 10000,
}))

// HU 48 - El backend deriva al vendedor de la sesión.
export async function listarPedidosRecibidos(filtros = {}) {
  const params = Object.fromEntries(Object.entries({ page: 0, size: 100, ...filtros })
    .filter(([, valor]) => valor !== '' && valor !== null && valor !== undefined))
  const { data } = await apiPedidosVendedor.get('', { params })
  return data
}

// HU 49 - El vendedor propone el siguiente estado del pedido.
// El backend valida la transición y devuelve el estado vigente con su historial.
export async function actualizarEstadoPedido(idPedido, estado) {
  const { data } = await apiPedidos.patch(`/${idPedido}/estado`, { estado })
  return data
}
