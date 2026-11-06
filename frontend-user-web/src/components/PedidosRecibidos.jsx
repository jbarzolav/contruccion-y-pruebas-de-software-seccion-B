import { useEffect, useState } from 'react'
import { actualizarEstadoPedido, listarPedidosRecibidos } from '../api/pedidosVendedor.js'
import { listarNotificaciones } from '../api/notificaciones.js'
import AvisosNuevaCompra from './AvisosNuevaCompra.jsx'
import { estadoPedidoVisible, fechaHoraPedidoVisible } from '../utils/formatoPedidos.js'

/** HU 48 - Pedidos recibidos por el vendedor. HU 49 - Actualización del estado. */
export default function PedidosRecibidos() {
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [seleccion, setSeleccion] = useState({})
  const [guardandoId, setGuardandoId] = useState(null)
  const [notificaciones, setNotificaciones] = useState([])
  const [criterios, setCriterios] = useState({ estado: '', desde: '', hasta: '', productoId: '' })
  const [aplicados, setAplicados] = useState({})
  const [pagina, setPagina] = useState(0)

  useEffect(() => {
    setCargando(true)
    setError('')
    listarPedidosRecibidos({ ...aplicados, page: pagina, size: 100 })
      .then(setPedidos)
      .catch((e) => setError(e?.response?.data?.messages?.[0] || 'No se pudieron cargar los pedidos recibidos.'))
      .finally(() => setCargando(false))
  }, [aplicados, pagina])

  useEffect(() => {
    listarNotificaciones().then(setNotificaciones).catch(() => {})
    const intervalo = window.setInterval(() => listarNotificaciones().then(setNotificaciones).catch(() => {}), 15000)
    return () => window.clearInterval(intervalo)
  }, [])

  function aplicarFiltros(evento) {
    evento.preventDefault()
    if (criterios.desde && criterios.hasta && criterios.desde > criterios.hasta) {
      setError('La fecha inicial no puede ser posterior a la fecha final.')
      return
    }
    setError('')
    setAplicados(criterios)
    setPagina(0)
  }

  function limpiarFiltros() {
    const vacios = { estado: '', desde: '', hasta: '', productoId: '' }
    setCriterios(vacios)
    setAplicados({})
    setPagina(0)
    setError('')
  }

  const productos = [...new Map(pedidos.flatMap((pedido) => pedido.productos || [])
    .filter((producto) => producto.idProducto != null)
    .map((producto) => [producto.idProducto, producto])).values()]

  /** HU 49.1 - Guarda el estado elegido y confirma el resultado. */
  async function guardarEstado(pedido) {
    const nuevoEstado = seleccion[pedido.idPedido]
    if (!nuevoEstado || guardandoId !== null) return
    setGuardandoId(pedido.idPedido)
    setError('')
    setMensaje('')
    try {
      const respuesta = await actualizarEstadoPedido(pedido.idPedido, nuevoEstado)
      setPedidos((lista) => lista.map((item) => item.idPedido === pedido.idPedido
        ? {
          ...item,
          estado: respuesta.estado,
          fechaActualizacion: respuesta.fechaActualizacion,
          estadosPermitidos: respuesta.estadosPermitidos,
        }
        : item))
      setSeleccion((actual) => ({ ...actual, [pedido.idPedido]: '' }))
      setMensaje(`N.º de compra: ${pedido.idPedido} · Estado actualizado a ${estadoPedidoVisible(respuesta.estado)}.`)
    } catch (e) {
      setError(e?.response?.data?.messages?.[0] || 'No se pudo actualizar el estado del pedido.')
    } finally {
      setGuardandoId(null)
    }
  }

  if (cargando) return <section className="tarjeta"><p>Cargando pedidos recibidos...</p></section>
  return <section className="tarjeta pedidos-recibidos">
    <h2>Pedidos recibidos</h2>
    <AvisosNuevaCompra notificaciones={notificaciones} />
    <form className="filtros-pedidos" onSubmit={aplicarFiltros}>
      <label className="campo">Estado
        <select value={criterios.estado} onChange={(e) => setCriterios({ ...criterios, estado: e.target.value })}>
          <option value="">Todos</option><option value="REGISTRADO">Registrado</option>
          <option value="EN_PROCESO">En proceso</option><option value="ENTREGADO">Entregado</option>
          <option value="CANCELADO">Cancelado</option>
        </select>
      </label>
      <label className="campo">Desde<input type="date" value={criterios.desde} onChange={(e) => setCriterios({ ...criterios, desde: e.target.value })} /></label>
      <label className="campo">Hasta<input type="date" value={criterios.hasta} onChange={(e) => setCriterios({ ...criterios, hasta: e.target.value })} /></label>
      <label className="campo">Producto
        <select value={criterios.productoId} onChange={(e) => setCriterios({ ...criterios, productoId: e.target.value })}>
          <option value="">Todos</option>
          {productos.map((producto) => <option value={producto.idProducto} key={producto.idProducto}>{producto.nombreProducto}</option>)}
        </select>
      </label>
      <button className="btn-primario" type="submit">Aplicar filtros</button>
      <button className="btn-secundario" type="button" onClick={limpiarFiltros}>Limpiar</button>
    </form>
    {error && <div className="alerta error" role="alert">{error}</div>}
    {mensaje && <div className="alerta" role="status">{mensaje}</div>}
    {!error && pedidos.length === 0 && <p>{Object.values(aplicados).some(Boolean) ? 'No hay pedidos que coincidan con los filtros.' : 'Aún no tienes pedidos que incluyan tus productos.'}</p>}
    {!error && pedidos.map((pedido) => {
      const permitidos = pedido.estadosPermitidos || []
      const elegido = seleccion[pedido.idPedido] || ''
      return <article className="pedido-historial" id={`pedido-${pedido.idPedido}`} key={pedido.idPedido}>
        <h3>N.º de compra: {pedido.idPedido}</h3>
        <p>{fechaHoraPedidoVisible(pedido.fecha)} · Estado: {estadoPedidoVisible(pedido.estado)} · Total de tus productos: S/ {Number(pedido.total).toFixed(2)}</p>
        <p>Última actualización: {fechaHoraPedidoVisible(pedido.fechaActualizacion)}</p>
        <ul>{pedido.productos.map((producto, indice) => <li key={`${producto.idProducto}-${indice}`}>
          {producto.nombreProducto} × {producto.cantidad} · S/ {Number(producto.subtotal).toFixed(2)}
        </li>)}</ul>
        {permitidos.length > 0 ? <>
          <label className="campo" htmlFor={`estado-${pedido.idPedido}`}>Actualizar estado</label>
          <select
            id={`estado-${pedido.idPedido}`}
            value={elegido}
            disabled={guardandoId !== null}
            onChange={(evento) => setSeleccion((actual) => ({ ...actual, [pedido.idPedido]: evento.target.value }))}
          >
            <option value="">Selecciona un estado</option>
            {permitidos.map((estado) => <option key={estado} value={estado}>{estadoPedidoVisible(estado)}</option>)}
          </select>
          <button
            className="btn-primario"
            type="button"
            disabled={!elegido || guardandoId !== null}
            onClick={() => guardarEstado(pedido)}
          >
            {guardandoId === pedido.idPedido ? 'Guardando...' : 'Guardar estado'}
          </button>
        </> : <p>Este pedido ya no admite cambios de estado.</p>}
      </article>
    })}
    {!error && (pagina > 0 || pedidos.length === 100) && <div className="paginacion-pedidos">
      <button className="btn-secundario" type="button" disabled={pagina === 0 || cargando} onClick={() => setPagina((actual) => actual - 1)}>Anterior</button>
      <span>Página {pagina + 1}</span>
      <button className="btn-secundario" type="button" disabled={pedidos.length < 100 || cargando} onClick={() => setPagina((actual) => actual + 1)}>Siguiente</button>
    </div>}
  </section>
}
