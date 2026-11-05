import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { consultarEstadoPedido, listarPedidos } from '../api/productos.js'
import { listarNotificaciones } from '../api/notificaciones.js'
import AvisosEstadoPedido from './AvisosEstadoPedido.jsx'
import ExperienciaCompra from './ExperienciaCompra.jsx'
import { estadoPedidoVisible, fechaHoraPedidoVisible } from '../utils/formatoPedidos.js'

export default function Pedidos() {
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [actualizandoId, setActualizandoId] = useState(null)
  const [notificaciones, setNotificaciones] = useState([])
  useEffect(() => {
    listarPedidos().then(setPedidos).catch((e) => setError(e?.response?.data?.messages?.[0] || 'No se pudo cargar tu historial.')).finally(() => setCargando(false))
    listarNotificaciones().then(setNotificaciones).catch(() => {})
    const intervalo = window.setInterval(() => listarNotificaciones().then(setNotificaciones).catch(() => {}), 15000)
    return () => window.clearInterval(intervalo)
  }, [])

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

  if (cargando) return <p>Cargando historial de compras...</p>
  return <section><h2>Mis compras</h2><AvisosEstadoPedido notificaciones={notificaciones} />{error && <div className="alerta error">{error}</div>}{!error && !pedidos.length && <p>Aún no tienes compras registradas.</p>}
    {pedidos.map((pedido) => <article className="tarjeta pedido-historial" key={pedido.idPedido}>
      <h3>N.º de compra: {pedido.idPedido}</h3>
      <p>{fechaHoraPedidoVisible(pedido.fecha)} · Estado: {estadoPedidoVisible(pedido.estado)} · Total S/ {Number(pedido.total).toFixed(2)}</p>
      <p>Última actualización: {fechaHoraPedidoVisible(pedido.fechaActualizacion)}</p>
      {/* HU 50 - Vista con productos, cantidades, precios y total. */}
      <Link className="btn-secundario" to={`/pedidos/${pedido.idPedido}`}>Ver detalle</Link>
      <button className="btn-secundario" type="button" disabled={actualizandoId !== null}
        onClick={() => actualizarEstado(pedido.idPedido)}>
        {actualizandoId === pedido.idPedido ? 'Actualizando...' : 'Actualizar estado'}
      </button>
      <ExperienciaCompra pedido={pedido} onActualizado={(actualizado) => setPedidos((lista) => lista.map((item) => item.idPedido === actualizado.idPedido ? actualizado : item))} />
    </article>)}
  </section>
}
