import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listarBandeja, marcarNotificacionLeida, marcarTodasComoLeidas } from '../api/notificaciones.js'
import { fechaHoraPedidoVisible, resumenNotificacionVisible } from '../utils/formatoPedidos.js'

/** HU 53 - Bandeja unificada de avisos con filtros combinables y paginación. */
const OPCIONES_TIPO = [
  { valor: '', etiqueta: 'Todos los tipos' },
  { valor: 'NUEVO_MENSAJE', etiqueta: 'Mensajes' },
  { valor: 'CAMBIO_ESTADO_PEDIDO', etiqueta: 'Cambios de estado' },
  { valor: 'NUEVA_COMPRA', etiqueta: 'Nuevas compras' },
  // HU 58 - Avisos de stock bajo de las publicaciones del vendedor.
  { valor: 'STOCK_BAJO', etiqueta: 'Stock bajo' },
]

const ACCIONES_POR_TIPO = {
  NUEVO_MENSAJE: 'Abrir conversación',
  CAMBIO_ESTADO_PEDIDO: 'Ver compra',
  NUEVA_COMPRA: 'Ver compra',
  STOCK_BAJO: 'Ver producto',
}

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
  const navegar = useNavigate()
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

  async function marcarLeida(aviso, destino) {
    if (accionando) return
    try {
      setAccionando(aviso.idNotificacion)
      setError('')
      await marcarNotificacionLeida(aviso.idNotificacion)
      if (destino) navegar(destino)
      else setVersion((valor) => valor + 1)
    } catch (err) {
      setError(err?.response?.data?.messages?.[0] || 'No se pudo marcar la notificación como leída.')
    } finally {
      setAccionando(null)
    }
  }

  function abrirDetalle(evento, aviso) {
    if (evento.defaultPrevented || evento.button !== 0 || evento.metaKey || evento.ctrlKey
      || evento.shiftKey || evento.altKey || aviso.estado !== 'PENDIENTE') return

    evento.preventDefault()
    marcarLeida(aviso, aviso.enlace)
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

  const cantidadAvisos = `${totalElementos} ${totalElementos === 1 ? 'aviso' : 'avisos'}`

  return <section className="bandeja-notificaciones">
    <header className="bandeja-cabecera">
      <div>
        <h2>Notificaciones</h2>
        <p aria-live="polite">{pendientes === 0
          ? 'Estás al día'
          : pendientes === 1 ? '1 aviso sin leer' : `${pendientes} avisos sin leer`}</p>
      </div>
      <button className="btn-secundario" type="button" disabled={accionando !== null || !pendientes}
        onClick={marcarTodas}>
        {accionando === 'todas' ? 'Marcando…' : 'Marcar todas como leídas'}
      </button>
    </header>

    <div className="bandeja-filtros" role="group" aria-label="Filtrar notificaciones">
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
      <p className="bandeja-vacia">{tipo || estado || fecha
        ? 'No hay avisos que coincidan con estos filtros.'
        : 'Todavía no tienes notificaciones.'}</p>}

    {!cargando && notificaciones.length > 0 && <div className="bandeja-lista">
      {notificaciones.map((aviso) => <article className="bandeja-aviso"
        data-pendiente={aviso.estado === 'PENDIENTE'} key={aviso.idNotificacion}>
        <div className="bandeja-aviso-texto">
          <div className="bandeja-aviso-meta">
            <span className="bandeja-estado">{aviso.estado === 'LEIDA' ? 'Leída' : 'Sin leer'}</span>
            <span>{fechaHoraPedidoVisible(aviso.fecha)}</span>
          </div>
          <p>{resumenNotificacionVisible(aviso)}</p>
          {aviso.fechaLectura && <small>Leída el {fechaHoraPedidoVisible(aviso.fechaLectura)}</small>}
        </div>
        <div className="bandeja-acciones">
          <Link className="btn-secundario" to={aviso.enlace}
            onClick={(evento) => abrirDetalle(evento, aviso)}>
            {ACCIONES_POR_TIPO[aviso.tipo] ?? 'Ver detalle'}
          </Link>
          {aviso.estado === 'PENDIENTE' && <button className="btn-secundario" type="button"
            disabled={accionando !== null}
            onClick={() => marcarLeida(aviso)}>
            {accionando === aviso.idNotificacion ? 'Marcando…' : 'Marcar como leída'}
          </button>}
        </div>
      </article>)}
    </div>}

    {!cargando && !error && totalElementos > 0 && totalPaginas <= 1 && <span>{cantidadAvisos}</span>}
    {totalPaginas > 1 && <nav className="bandeja-paginacion" aria-label="Paginación de notificaciones">
      <button className="btn-secundario" type="button" disabled={cargando || pagina === 0}
        onClick={() => setPagina((valor) => Math.max(0, valor - 1))}>Anterior</button>
      <span>Página {pagina + 1} de {totalPaginas} · {cantidadAvisos}</span>
      <button className="btn-secundario" type="button" disabled={cargando || !tieneSiguiente}
        onClick={() => setPagina((valor) => valor + 1)}>Siguiente</button>
    </nav>}
  </section>
}
