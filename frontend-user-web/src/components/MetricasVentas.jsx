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

const formatoFecha = new Intl.DateTimeFormat('es-PE', {
  day: 'numeric', month: 'short', year: 'numeric',
})

function rangoVisible(desde, hasta) {
  return formatoFecha.formatRange(new Date(desde + 'T00:00:00'), new Date(hasta + 'T00:00:00'))
}

function Metrica({ titulo, valor, anterior, comparativa }) {
  const variacion = comparativa?.variacionPorcentual
  const tendencia = variacion == null ? 'neutra' : {
    AUMENTO: 'aumento', DISMINUCION: 'disminucion',
  }[comparativa.tendencia] || 'neutra'

  return <article className="metrica">
    <h3>{titulo}</h3>
    <p className="metrica-valor">{valor}</p>
    {comparativa && <p className={`metrica-tendencia ${tendencia}`}>
      {variacion == null
        ? 'Variación porcentual no disponible'
        : new Intl.NumberFormat('es-PE', { signDisplay: 'exceptZero', maximumFractionDigits: 2 })
          .format(variacion) + ' % respecto al período anterior'}
    </p>}
    {anterior != null && <p className="metrica-valor-anterior">Anterior · {anterior}</p>}
  </article>
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
  const anterior = comparar ? metricas?.periodoAnterior : null
  const sinVentasAnteriores = anterior != null && Number(anterior.cantidadPedidos) === 0
  const mostrarVariacion = anterior != null && !sinVentasAnteriores
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
      <div className="metricas-periodo">
        <strong>{rangoVisible(metricas.desde, metricas.hasta)}</strong>
        {comparar && metricas.desdeAnterior && metricas.hastaAnterior &&
          <span>Comparado con {rangoVisible(metricas.desdeAnterior, metricas.hastaAnterior)}</span>}
        {sinVentasAnteriores && <span>Sin ventas confirmadas en el período anterior.</span>}
      </div>

      {!hayVentas && <div className="alerta" role="status">
        No hay ventas confirmadas en el periodo seleccionado.
      </div>}

      <div className="metricas-tarjetas">
        <Metrica titulo="Ingresos confirmados" valor={moneda(periodo?.ingresosConfirmados)}
          comparativa={mostrarVariacion && metricas.comparativaIngresos}
          anterior={mostrarVariacion ? moneda(anterior.ingresosConfirmados) : null} />
        <Metrica titulo="Pedidos" valor={entero(periodo?.cantidadPedidos)}
          comparativa={mostrarVariacion && metricas.comparativaPedidos}
          anterior={mostrarVariacion ? entero(anterior.cantidadPedidos) : null} />
      </div>

      <section className="metricas-productos" aria-labelledby="metricas-productos-titulo">
        <h3 id="metricas-productos-titulo">Productos más vendidos</h3>
        {metricas.productosMasVendidos?.length > 0 ? <table aria-labelledby="metricas-productos-titulo">
          <thead>
            <tr><th scope="col">Producto</th><th scope="col">Unidades</th><th scope="col">Ingresos</th></tr>
          </thead>
          <tbody>
            {metricas.productosMasVendidos.map(producto => <tr key={producto.idProducto}>
              <td><Link className="pedido-enlace-producto" to={`/producto/${producto.idProducto}`}>{producto.nombre}</Link></td>
              <td>{entero(producto.unidadesVendidas)}</td>
              <td>{moneda(producto.importeAtribuido)}</td>
            </tr>)}
          </tbody>
        </table> : <p>No hay productos vendidos en el período seleccionado.</p>}
      </section>
    </>}
  </section>
}
