import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarBandeja, marcarNotificacionLeida, marcarTodasComoLeidas } from '../api/notificaciones.js'
import { fechaHoraPedidoVisible, resumenNotificacionVisible } from '../utils/formatoPedidos.js'

/** HU 53 - Bandeja unificada de avisos con filtros combinables y paginación. */
const OPCIONES_TIPO = [
  { valor: '', etiqueta: 'Todos los tipos' },
  { valor: 'NUEVO_MENSAJE', etiqueta: 'Mensajes' },
  { valor: 'CAMBIO_ESTADO_PEDIDO', etiqueta: 'Cambios de estado' },
  { valor: 'NUEVA_COMPRA', etiqueta: 'Nuevas compras' },
]

const OPCIONES_ESTADO = [
  { valor: '', etiqueta: 'Todos los estados' },
  { valor: 'PENDIENTE', etiqueta: 'Pendientes' },
  { valor: 'LEIDA', etiqueta: 'Leídas' },
]

const OPCIONES_FECHA = [
  { valor: '', etiqueta: 'Todas las fechas' },
  { valor: 'hoy', etiqueta: 'Hoy' },
  { valor: '7', etiqueta: 'Últimos 7 días' },
  { valor: '30', etiqueta: 'Últimos 30 días' },
]

/** Convierte el preset en un rango desde/hasta en formato AAAA-MM-DD. */
function rangoFechas(preset) {
  if (!preset) return {}
  const hoy = new Date()
  const inicio = preset === 'hoy'
    ? hoy
    : new Date(hoy.getTime() - (Number(preset) - 1) * 86400000)
  const formato = (fecha) => `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`
  return { desde: formato(inicio), hasta: formato(hoy) }
}

export default function BandejaNotificaciones() {
  const [tipo, setTipo] = useState('')
  const [estado, setEstado] = useState('')
  const [fecha, setFecha] = useState('')
  const [pagina, setPagina] = useState(0)
  const [version, setVersion] = useState(0)
  const [bandeja, setBandeja] = useState({
    notificaciones: [], pagina: 0, tamano: 20, totalElementos: 0,
    totalPaginas: 0, tieneSiguiente: false, pendientes: 0,
  })
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [accionando, setAccionando] = useState(null)

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError('')
    listarBandeja({ tipo, estado, ...rangoFechas(fecha), page: pagina })
      .then((data) => { if (vigente) setBandeja(data) })
      .catch((err) => { if (vigente) setError(err?.response?.data?.messages?.[0] || 'No se pudieron cargar las notificaciones.') })
      .finally(() => { if (vigente) setCargando(false) })
    return () => { vigente = false }
  }, [tipo, estado, fecha, pagina, version])

  // Cambiar un filtro vuelve a la primera página (HU 53).
  function cambiarFiltro(setter) {
    return (evento) => { setter(evento.target.value); setPagina(0) }
  }

  async function marcarLeida(aviso) {
    if (accionando) return
    try {
      setAccionando(aviso.idNotificacion)
      setError('')
      await marcarNotificacionLeida(aviso.idNotificacion)
      setVersion((valor) => valor + 1)
    } catch (err) {
      setError(err?.response?.data?.messages?.[0] || 'No se pudo marcar la notificación como leída.')
    } finally {
      setAccionando(null)
    }
  }

  async function marcarTodas() {
    if (accionando || !bandeja.pendientes) return
    try {
      setAccionando('todas')
      setError('')
      await marcarTodasComoLeidas()
      setVersion((valor) => valor + 1)
    } catch (err) {
      setError(err?.response?.data?.messages?.[0] || 'No se pudieron marcar las notificaciones como leídas.')
    } finally {
      setAccionando(null)
    }
  }

  const { notificaciones, pendientes, totalElementos, totalPaginas, tieneSiguiente } = bandeja

  return <section>
    <h2>Notificaciones</h2>
    <p>
      {pendientes > 0
        ? <><strong>{pendientes}</strong> notificación(es) pendiente(s) en toda tu bandeja.</>
        : 'No tienes notificaciones pendientes.'}
    </p>
    <button className="btn-primario" type="button" disabled={accionando !== null || !pendientes}
      onClick={marcarTodas}>
      {accionando === 'todas' ? 'Marcando…' : 'Marcar todas como leídas'}
    </button>

    {/* HU 53 - Filtros combinables de tipo, estado y rango de fechas. */}
    <div className="campo" style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem', alignItems: 'flex-end' }}>
      <label>Tipo
        <select value={tipo} onChange={cambiarFiltro(setTipo)}>
          {OPCIONES_TIPO.map((opcion) => <option key={opcion.valor} value={opcion.valor}>{opcion.etiqueta}</option>)}
        </select>
      </label>
      <label>Estado
        <select value={estado} onChange={cambiarFiltro(setEstado)}>
          {OPCIONES_ESTADO.map((opcion) => <option key={opcion.valor} value={opcion.valor}>{opcion.etiqueta}</option>)}
        </select>
      </label>
      <label>Fecha
        <select value={fecha} onChange={cambiarFiltro(setFecha)}>
          {OPCIONES_FECHA.map((opcion) => <option key={opcion.valor} value={opcion.valor}>{opcion.etiqueta}</option>)}
        </select>
      </label>
      <button className="btn-secundario" type="button" disabled={cargando}
        onClick={() => setVersion((valor) => valor + 1)}>Actualizar</button>
    </div>

    {error && <div className="alerta error" role="alert">{error}</div>}
    {cargando && <p role="status">Cargando notificaciones…</p>}
    {!cargando && !error && !notificaciones.length &&
      <p>No hay notificaciones con los filtros seleccionados.</p>}

    {!cargando && notificaciones.map((aviso) => <article className="tarjeta" key={aviso.idNotificacion}>
      <p>
        <span className="badge">{aviso.estado === 'LEIDA' ? 'Leída' : 'Pendiente'}</span>{' '}
        {resumenNotificacionVisible(aviso)} · {fechaHoraPedidoVisible(aviso.fecha)}
      </p>
      {aviso.fechaLectura && <p>Leída el {fechaHoraPedidoVisible(aviso.fechaLectura)}</p>}
      <Link className="btn-secundario" to={aviso.enlace}>Ver detalle</Link>
      {aviso.estado === 'PENDIENTE' && <button className="btn-secundario" type="button"
        disabled={accionando !== null}
        onClick={() => marcarLeida(aviso)}>
        {accionando === aviso.idNotificacion ? 'Marcando…' : 'Marcar como leída'}
      </button>}
    </article>)}

    {totalElementos > 0 && <nav aria-label="Paginación de notificaciones"
      style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
      <button className="btn-secundario" type="button" disabled={cargando || pagina === 0}
        onClick={() => setPagina((valor) => Math.max(0, valor - 1))}>Anterior</button>
      <span>Página {pagina + 1} de {Math.max(totalPaginas, 1)} · {totalElementos} notificación(es)</span>
      <button className="btn-secundario" type="button" disabled={cargando || !tieneSiguiente}
        onClick={() => setPagina((valor) => valor + 1)}>Siguiente</button>
    </nav>}
  </section>
}
