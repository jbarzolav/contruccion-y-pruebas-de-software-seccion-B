import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenerMetricasVentas } from '../api/metricasVendedor.js'

/**
 * HU 57 - Panel de métricas de ventas del vendedor.
 *
 * Estados contemplados: cargando, con datos, sin ventas en el periodo,
 * error de conexión y error de permisos (o cualquier error del servidor).
 */

const PERIODOS = [
  { valor: '7', etiqueta: 'Últimos 7 días' },
  { valor: '30', etiqueta: 'Últimos 30 días' },
  { valor: 'mes', etiqueta: 'Mes actual' },
  { valor: 'mes-anterior', etiqueta: 'Mes anterior' },
  { valor: 'personalizado', etiqueta: 'Personalizado' },
]

/** Formatea una fecha local como AAAA-MM-DD (sin desfase de zona horaria). */
function formatoIso(fecha) {
  const anio = fecha.getFullYear()
  const mes = String(fecha.getMonth() + 1).padStart(2, '0')
  const dia = String(fecha.getDate()).padStart(2, '0')
  return `${anio}-${mes}-${dia}`
}

/** Calcula el rango (desde, hasta) del preset elegido. */
function rangoDelPeriodo(preset) {
  const hoy = new Date()
  if (preset === 'mes') {
    const desde = new Date(hoy.getFullYear(), hoy.getMonth(), 1)
    return { desde: formatoIso(desde), hasta: formatoIso(hoy) }
  }
  if (preset === 'mes-anterior') {
    const primeroAnterior = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1)
    const ultimoAnterior = new Date(hoy.getFullYear(), hoy.getMonth(), 0)
    return { desde: formatoIso(primeroAnterior), hasta: formatoIso(ultimoAnterior) }
  }
  const dias = Number(preset) || 30
  const inicio = new Date(hoy.getTime() - (dias - 1) * 86400000)
  return { desde: formatoIso(inicio), hasta: formatoIso(hoy) }
}

function moneda(valor) {
  const numero = Number(valor)
  if (!Number.isFinite(numero)) return 'S/ 0.00'
  return `S/ ${numero.toFixed(2)}`
}

function entero(valor) {
  const numero = Number(valor)
  return Number.isFinite(numero) ? String(numero) : '0'
}

/** Texto de la variación porcentual; null cuando el periodo anterior fue 0. */
function variacionVisible(comparativa) {
  if (!comparativa || comparativa.variacionPorcentual === null || comparativa.variacionPorcentual === undefined) {
    return 'Sin base de comparación'
  }
  const valor = Number(comparativa.variacionPorcentual)
  const signo = valor > 0 ? '+' : ''
  return `${signo}${valor.toFixed(2)} %`
}

function tendenciaVisible(comparativa) {
  if (!comparativa) return ''
  if (comparativa.tendencia === 'AUMENTO') return 'Aumento'
  if (comparativa.tendencia === 'DISMINUCION') return 'Disminución'
  return 'Sin cambios'
}

function claseTendencia(comparativa) {
  if (!comparativa) return 'metrica-tendencia neutra'
  if (comparativa.tendencia === 'AUMENTO') return 'metrica-tendencia aumento'
  if (comparativa.tendencia === 'DISMINUCION') return 'metrica-tendencia disminucion'
  return 'metrica-tendencia neutra'
}

export default function MetricasVentas() {
  const [preset, setPreset] = useState('30')
  const [personalizado, setPersonalizado] = useState({ desde: '', hasta: '' })
  const [comparar, setComparar] = useState(true)
  const [criterios, setCriterios] = useState(() => rangoDelPeriodo('30'))
  const [metricas, setMetricas] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [tipoError, setTipoError] = useState('')

  // Aplica el preset o el rango personalizado al consultar.
  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError('')
    setTipoError('')
    obtenerMetricasVentas({
      desde: criterios.desde || undefined,
      hasta: criterios.hasta || undefined,
      compararPeriodoAnterior: comparar,
    })
      .then((data) => { if (vigente) setMetricas(data) })
      .catch((e) => {
        if (!vigente) return
        const estado = e?.response?.status
        const mensaje = e?.response?.data?.messages?.[0]
        if (!e?.response) {
          setTipoError('conexion')
          setError('No se pudo conectar con el servidor. Revisa tu conexión a internet.')
        } else if (estado === 403) {
          setTipoError('permisos')
          setError(mensaje || 'No tienes permiso para consultar las métricas de ventas.')
        } else if (estado === 401) {
          setTipoError('permisos')
          setError('Tu sesión venció. Inicia sesión nuevamente.')
        } else {
          setTipoError('servidor')
          setError(mensaje || 'No se pudieron cargar las métricas de ventas.')
        }
      })
      .finally(() => { if (vigente) setCargando(false) })
    return () => { vigente = false }
  }, [criterios, comparar])

  function cambiarPreset(evento) {
    const valor = evento.target.value
    setPreset(valor)
    if (valor !== 'personalizado') setCriterios(rangoDelPeriodo(valor))
  }

  function aplicarPersonalizado(evento) {
    evento.preventDefault()
    if (!personalizado.desde || !personalizado.hasta) {
      setTipoError('validacion')
      setError('Indica las dos fechas del periodo.')
      return
    }
    if (personalizado.desde > personalizado.hasta) {
      setTipoError('validacion')
      setError('La fecha inicial no puede ser posterior a la fecha final.')
      return
    }
    setError('')
    setTipoError('')
    setCriterios({ desde: personalizado.desde, hasta: personalizado.hasta })
  }

  const periodo = metricas?.periodoActual
  const hayVentas = useMemo(
    () => !!periodo && Number(periodo.cantidadPedidos) > 0,
    [periodo],
  )

  if (cargando) {
    return <section className="tarjeta metricas-ventas"><h2>Métricas de ventas</h2>
      <p role="status">Cargando métricas de ventas…</p>
    </section>
  }

  return <section className="tarjeta metricas-ventas">
    <h2>Métricas de ventas</h2>

    <form className="filtros-pedidos metricas-filtros" onSubmit={aplicarPersonalizado}>
      <label className="campo">Periodo
        <select value={preset} onChange={cambiarPreset}>
          {PERIODOS.map((opcion) => <option key={opcion.valor} value={opcion.valor}>{opcion.etiqueta}</option>)}
        </select>
      </label>
      {preset === 'personalizado' && <>
        <label className="campo">Desde
          <input type="date" value={personalizado.desde}
            onChange={(e) => setPersonalizado({ ...personalizado, desde: e.target.value })} />
        </label>
        <label className="campo">Hasta
          <input type="date" value={personalizado.hasta}
            onChange={(e) => setPersonalizado({ ...personalizado, hasta: e.target.value })} />
        </label>
        <button className="btn-primario" type="submit">Aplicar</button>
      </>}
      <label className="campo metricas-comparar">
        <input type="checkbox" checked={comparar}
          onChange={(e) => setComparar(e.target.checked)} />
        Comparar con el periodo anterior
      </label>
    </form>

    {error && <div className="alerta error" role="alert">{error}</div>}

    {!error && metricas && <>
      <p className="metricas-periodo">
        Periodo analizado: <strong>{metricas.desde}</strong> al <strong>{metricas.hasta}</strong>
        {comparar && metricas.desdeAnterior && (
          <> · Periodo anterior: {metricas.desdeAnterior} al {metricas.hastaAnterior}</>
        )}
      </p>

      {!hayVentas && <div className="alerta" role="status">
        No hay ventas confirmadas en el periodo seleccionado.
      </div>}

      <div className="metricas-tarjetas">
        <article className="metrica">
          <h3>Ingresos confirmados</h3>
          <p className="metrica-valor">{moneda(periodo?.ingresosConfirmados)}</p>
          {comparar && metricas.comparativaIngresos && <p className={claseTendencia(metricas.comparativaIngresos)}>
            {tendenciaVisible(metricas.comparativaIngresos)} · {variacionVisible(metricas.comparativaIngresos)} ·
            {' '}diferencia {moneda(metricas.comparativaIngresos.diferenciaAbsoluta)}
          </p>}
          {comparar && metricas.periodoAnterior && <p className="metrica-valor-anterior">
            Periodo anterior: {moneda(metricas.periodoAnterior.ingresosConfirmados)}
          </p>}
        </article>

        <article className="metrica">
          <h3>N.º de pedidos</h3>
          <p className="metrica-valor">{entero(periodo?.cantidadPedidos)}</p>
          {comparar && metricas.comparativaPedidos && <p className={claseTendencia(metricas.comparativaPedidos)}>
            {tendenciaVisible(metricas.comparativaPedidos)} · {variacionVisible(metricas.comparativaPedidos)} ·
            {' '}diferencia {entero(metricas.comparativaPedidos.diferenciaAbsoluta)}
          </p>}
          {comparar && metricas.periodoAnterior && <p className="metrica-valor-anterior">
            Periodo anterior: {entero(metricas.periodoAnterior.cantidadPedidos)}
          </p>}
        </article>

        <article className="metrica">
          <h3>Promedio por pedido</h3>
          <p className="metrica-valor">{moneda(periodo?.promedioPorPedido)}</p>
          {comparar && metricas.comparativaPromedio && <p className={claseTendencia(metricas.comparativaPromedio)}>
            {tendenciaVisible(metricas.comparativaPromedio)} · {variacionVisible(metricas.comparativaPromedio)} ·
            {' '}diferencia {moneda(metricas.comparativaPromedio.diferenciaAbsoluta)}
          </p>}
          {comparar && metricas.periodoAnterior && <p className="metrica-valor-anterior">
            Periodo anterior: {moneda(metricas.periodoAnterior.promedioPorPedido)}
          </p>}
        </article>
      </div>

      <h3>Productos más vendidos</h3>
      {(!metricas.productosMasVendidos || metricas.productosMasVendidos.length === 0) && (
        <p>No hay productos vendidos en el periodo seleccionado.</p>
      )}
      {metricas.productosMasVendidos?.length > 0 && <ul className="metricas-productos">
        {metricas.productosMasVendidos.map((producto) => <li key={producto.idProducto}>
          <Link to={`/producto/${producto.idProducto}`}>{producto.nombre}</Link>
          {' · '}{producto.unidadesVendidas} unidad(es) vendida(s) · {moneda(producto.importeAtribuido)}
        </li>)}
      </ul>}
    </>}
  </section>
}
