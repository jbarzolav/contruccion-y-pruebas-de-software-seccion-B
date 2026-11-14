import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { consultarEstadoPedido, listarPedidos } from '../api/productos.js'
import ImagenCarrito from './ImagenCarrito.jsx'
import ExperienciaCompra from './ExperienciaCompra.jsx'
import { estadoPedidoVisible, fechaHoraPedidoVisible, numeroPedidoVisible } from '../utils/formatoPedidos.js'

const COMPRAS_POR_PAGINA = 12
const TAMANO_LOTE = 100
const FILTROS_INICIALES = { estado: '', periodo: '', desde: '', hasta: '', montoMin: '', montoMax: '' }

function inicioDelPeriodo(periodo) {
  const fecha = new Date()
  if (periodo === 'anio') fecha.setMonth(0, 1)
  else fecha.setDate(fecha.getDate() - (Number(periodo) - 1))

  const mes = String(fecha.getMonth() + 1).padStart(2, '0')
  const dia = String(fecha.getDate()).padStart(2, '0')
  return `${fecha.getFullYear()}-${mes}-${dia}`
}

export default function Pedidos() {
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [actualizandoId, setActualizandoId] = useState(null)
  const [criterios, setCriterios] = useState(FILTROS_INICIALES)
  const [aplicados, setAplicados] = useState({})
  const [numero, setNumero] = useState('')
  const [pagina, setPagina] = useState(0)
  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError('')

    async function cargar() {
      try {
        const lista = []
        let lote
        let page = 0
        do {
          lote = await listarPedidos({ ...aplicados, page, size: TAMANO_LOTE })
          if (!vigente) return
          lista.push(...lote)
          page += 1
        } while (lote.length === TAMANO_LOTE)
        setPedidos(lista)
      } catch (e) {
        if (vigente) setError(e?.response?.data?.messages?.[0] || 'No se pudo cargar tu historial.')
      } finally {
        if (vigente) setCargando(false)
      }
    }

    cargar()
    return () => { vigente = false }
  }, [aplicados])

  const termino = numero.trim()
  const resultados = pedidos.filter(pedido => numeroPedidoVisible(pedido.idPedido).includes(termino))
  const totalPaginas = Math.ceil(resultados.length / COMPRAS_POR_PAGINA)
  const inicio = pagina * COMPRAS_POR_PAGINA
  const comprasVisibles = resultados.slice(inicio, inicio + COMPRAS_POR_PAGINA)

  function aplicarFiltros(evento) {
    evento.preventDefault()
    if (criterios.desde && criterios.hasta && criterios.desde > criterios.hasta) {
      setError('La fecha inicial no puede ser posterior a la fecha final.')
      return
    }
    if ((criterios.montoMin && Number(criterios.montoMin) < 0) || (criterios.montoMax && Number(criterios.montoMax) < 0)
      || (criterios.montoMin && criterios.montoMax && Number(criterios.montoMin) > Number(criterios.montoMax))) {
      setError('Revisa el rango de montos ingresado.')
      return
    }
    setError('')
    const { periodo, ...filtros } = criterios
    if (periodo && periodo !== 'personalizado') filtros.desde = inicioDelPeriodo(periodo)
    setAplicados(filtros)
    setPagina(0)
  }

  function limpiarFiltros() {
    setCriterios(FILTROS_INICIALES)
    setNumero('')
    setAplicados({})
    setPagina(0)
    setError('')
  }

  async function actualizarEstado(idPedido) {
    if (actualizandoId !== null) return
    try {
      setActualizandoId(idPedido)
      setError('')
      const estado = await consultarEstadoPedido(idPedido)
      setPedidos((lista) => lista.map((pedido) => pedido.idPedido === idPedido
        ? { ...pedido, estado: estado.estado, fechaActualizacion: estado.fechaActualizacion }
        : pedido))
    } catch (e) {
      setError(e?.response?.data?.messages?.[0] || 'No se pudo actualizar el estado del pedido.')
    } finally {
      setActualizandoId(null)
    }
  }

  return <section className="mis-compras">
    <header className="mis-compras-cabecera">
      <h2>Mis compras</h2>
      <Link className="btn-secundario" to="/notificaciones">Ver notificaciones</Link>
    </header>
    <form className="filtros-pedidos" aria-label="Filtrar compras" onSubmit={aplicarFiltros}>
      <label className="campo">N.º de pedido
        <input type="search" inputMode="numeric" placeholder="Ej. 000006" value={numero}
          onChange={(e) => { setNumero(e.target.value); setPagina(0) }} />
      </label>
      <label className="campo">Estado
        <select value={criterios.estado} onChange={(e) => setCriterios({ ...criterios, estado: e.target.value })}>
          <option value="">Todos</option><option value="REGISTRADO">Registrado</option>
          <option value="EN_PROCESO">En proceso</option><option value="ENTREGADO">Entregado</option>
          <option value="CANCELADO">Cancelado</option>
        </select>
      </label>
      <label className="campo">Período
        <select value={criterios.periodo}
          onChange={(e) => setCriterios({ ...criterios, periodo: e.target.value, desde: '', hasta: '' })}>
          <option value="">Todas las fechas</option>
          <option value="30">Últimos 30 días</option>
          <option value="90">Últimos 90 días</option>
          <option value="anio">Este año</option>
          {criterios.periodo === 'personalizado' && <option value="personalizado">Fechas personalizadas</option>}
        </select>
      </label>
      <details className="compras-filtros-adicionales">
        <summary>Más filtros</summary>
        <div className="compras-filtros-campos">
          <label className="campo">Desde<input type="date" value={criterios.desde} onChange={(e) => setCriterios({ ...criterios, periodo: 'personalizado', desde: e.target.value })} /></label>
          <label className="campo">Hasta<input type="date" value={criterios.hasta} onChange={(e) => setCriterios({ ...criterios, periodo: 'personalizado', hasta: e.target.value })} /></label>
          <label className="campo">Monto mínimo (S/)<input type="number" min="0" step="0.01" value={criterios.montoMin} onChange={(e) => setCriterios({ ...criterios, montoMin: e.target.value })} /></label>
          <label className="campo">Monto máximo (S/)<input type="number" min="0" step="0.01" value={criterios.montoMax} onChange={(e) => setCriterios({ ...criterios, montoMax: e.target.value })} /></label>
        </div>
      </details>
      <button className="btn-primario" type="submit">Aplicar filtros</button>
      <button className="btn-secundario" type="button" onClick={limpiarFiltros}>Limpiar</button>
    </form>
    {cargando && <p role="status">Cargando historial de compras...</p>}
    {error && <div className="alerta error" role="alert">{error}</div>}
    {!cargando && !error && !resultados.length && <p>{termino || Object.values(aplicados).some(Boolean)
      ? 'No hay compras que coincidan con los filtros.'
      : 'Aún no tienes compras registradas.'}</p>}
    {!cargando && !error && comprasVisibles.map((pedido) => <article className="tarjeta pedido-historial" key={pedido.idPedido}>
      <header className="mis-compras-cabecera">
        <div>
          <h3>N.º de pedido: {numeroPedidoVisible(pedido.idPedido)}</h3>
          <p>{fechaHoraPedidoVisible(pedido.fecha)}</p>
        </div>
        <span className="compra-estado" data-estado={pedido.estado}>
          {estadoPedidoVisible(pedido.estado)}
        </span>
      </header>
      {pedido.detalles?.length > 0 && <ul className="pedido-recibido-productos" aria-label="Productos comprados">
        {pedido.detalles.map((producto, indice) => <li key={indice}>
          <span className="pedido-producto">
            {producto.idProducto != null && <ImagenCarrito productoId={producto.idProducto} nombre="" />}
            <span><strong>{producto.nombreProducto}</strong> <small>× {producto.cantidad}</small></span>
          </span>
        </li>)}
      </ul>}
      <p className="compra-total">Total <strong>S/ {Number(pedido.total).toFixed(2)}</strong></p>
      {pedido.fechaActualizacion && <p>Última actualización: {fechaHoraPedidoVisible(pedido.fechaActualizacion)}</p>}
      <div className="compra-acciones">
        <Link className="btn-primario" to={`/pedidos/${pedido.idPedido}`}>Ver detalle</Link>
        <button className="btn-secundario" type="button" disabled={actualizandoId !== null}
          onClick={() => actualizarEstado(pedido.idPedido)}>
          {actualizandoId === pedido.idPedido ? 'Actualizando...' : 'Actualizar estado'}
        </button>
      </div>
      {pedido.estado === 'ENTREGADO' && <details className="compra-experiencia">
        <summary>Calificar mi compra</summary>
        <ExperienciaCompra mostrarTitulo={false} pedido={pedido} onActualizado={(actualizado) => setPedidos((lista) => lista.map((item) => item.idPedido === actualizado.idPedido ? actualizado : item))} />
      </details>}
    </article>)}
    {!cargando && !error && totalPaginas > 1 && <nav className="paginacion-pedidos" aria-label="Páginas de compras">
      <button className="btn-secundario" type="button" disabled={pagina === 0 || cargando} onClick={() => setPagina((actual) => actual - 1)}>Anterior</button>
      <span aria-live="polite">Página {pagina + 1} de {totalPaginas}</span>
      <button className="btn-secundario" type="button" disabled={pagina >= totalPaginas - 1 || cargando} onClick={() => setPagina((actual) => actual + 1)}>Siguiente</button>
    </nav>}
  </section>
}
