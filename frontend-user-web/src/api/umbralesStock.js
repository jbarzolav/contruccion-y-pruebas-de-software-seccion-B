import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

/**
 * HU 58 - Umbrales de stock y avisos de stock bajo del vendedor.
 *
 * El backend deriva al vendedor de la sesión: esta capa nunca envía un
 * identificador de vendedor.
 */
const apiVendedor = configurarSesion(axios.create({
  baseURL: `${API_URL}/api/vendedores/me`,
  timeout: 10000,
}))

const apiUmbralProducto = configurarSesion(axios.create({
  baseURL: `${API_URL}/api/productos`,
  timeout: 10000,
}))

/** Panel: publicaciones propias con su umbral y estado de stock. */
export async function listarUmbralesStock() {
  const { data } = await apiVendedor.get('/umbrales-stock')
  return data
}

/** Avisos de stock bajo del vendedor (se alimentan de la bandeja existente). */
export async function listarAvisosStockBajo() {
  const { data } = await apiVendedor.get('/avisos-stock-bajo')
  return data
}

/** Crea o actualiza el umbral de una publicación propia. */
export async function guardarUmbralStock(idProducto, umbral) {
  const { data } = await apiUmbralProducto.put(`/${idProducto}/umbral-stock`, { umbral })
  return data
}
