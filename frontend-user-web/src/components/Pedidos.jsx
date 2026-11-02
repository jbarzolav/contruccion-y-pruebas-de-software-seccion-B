import { useEffect, useState } from 'react'
import { consultarEstadoPedido, listarPedidos } from '../api/productos.js'
import ExperienciaCompra from './ExperienciaCompra.jsx'

export default function Pedidos() {
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [actualizandoId, setActualizandoId] = useState(null)
  useEffect(() => { listarPedidos().then(setPedidos).catch((e) => setError(e?.response?.data?.messages?.[0] || 'No se pudo cargar tu historial.')).finally(() => setCargando(false)) }, [])

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
  return <section><h2>Mis compras</h2>{error && <div className="alerta error">{error}</div>}{!error && !pedidos.length && <p>Aún no tienes compras registradas.</p>}
    {pedidos.map((pedido) => <article className="tarjeta pedido-historial" key={pedido.idPedido}>
      <h3>Pedido #{pedido.idPedido}</h3>
      <p>{pedido.fecha} · Estado: {pedido.estado || 'REGISTRADO'} · Total S/ {Number(pedido.total).toFixed(2)}</p>
      <p>Última actualización: {pedido.fechaActualizacion ? new Date(pedido.fechaActualizacion).toLocaleString('es-PE') : 'Sin información'}</p>
      <button className="btn-secundario" type="button" disabled={actualizandoId !== null}
        onClick={() => actualizarEstado(pedido.idPedido)}>
        {actualizandoId === pedido.idPedido ? 'Actualizando...' : 'Actualizar estado'}
      </button>
      <ExperienciaCompra pedido={pedido} onActualizado={(actualizado) => setPedidos((lista) => lista.map((item) => item.idPedido === actualizado.idPedido ? actualizado : item))} />
    </article>)}
  </section>
}
