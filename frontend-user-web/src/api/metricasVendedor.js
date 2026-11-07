import axios from 'axios'
import { API_URL, configurarSesion } from './sesion.js'

/**
 * HU 57 - Panel de métricas de ventas del vendedor.
 *
 * El backend deriva al vendedor de la sesión: esta capa NUNCA envía un
 * identificador de vendedor, solo el periodo y si se quiere comparar con el
 * periodo anterior.
 */
const apiMetricas = configurarSesion(axios.create({
  baseURL: `${API_URL}/api/vendedores/me/metricas`,
  timeout: 10000,
}))

/**
 * @param {object} opciones
 * @param {string} [opciones.desde]  Fecha inicial (AAAA-MM-DD), inclusiva.
 * @param {string} [opciones.hasta]  Fecha final (AAAA-MM-DD), inclusiva.
 * @param {boolean} [opciones.compararPeriodoAnterior]
 */
export async function obtenerMetricasVentas({ desde, hasta, compararPeriodoAnterior } = {}) {
  const params = {}
  if (desde) params.desde = desde
  if (hasta) params.hasta = hasta
  if (compararPeriodoAnterior !== undefined && compararPeriodoAnterior !== null) {
    params.compararPeriodoAnterior = String(Boolean(compararPeriodoAnterior))
  }
  const { data } = await apiMetricas.get('/ventas', { params })
  return data
}
