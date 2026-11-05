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

  useEffect(() => {
    listarPedidosRecibidos()
      .then(setPedidos)
      .catch((e) => setError(e?.response?.data?.messages?.[0] || 'No se pudieron cargar los pedidos recibidos.'))
      .finally(() => setCargando(false))
    listarNotificaciones().then(setNotificaciones).catch(() => {})
    const intervalo = window.setInterval(() => listarNotificaciones().then(setNotificaciones).catch(() => {}), 15000)
    return () => window.clearInterval(intervalo)
  }, [])

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
    {error && <div className="alerta error" role="alert">{error}</div>}
    {mensaje && <div className="alerta" role="status">{mensaje}</div>}
    {!error && pedidos.length === 0 && <p>Aún no tienes pedidos que incluyan tus productos.</p>}
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
  </section>
}
