import { useEffect, useState } from 'react'
import { listarPedidos } from '../api/productos.js'
import ExperienciaCompra from './ExperienciaCompra.jsx'

export default function Pedidos() {
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => { listarPedidos().then(setPedidos).catch((e) => setError(e?.response?.data?.messages?.[0] || 'No se pudo cargar tu historial.')).finally(() => setCargando(false)) }, [])
  if (cargando) return <p>Cargando historial de compras...</p>
  return <section><h2>Mis compras</h2>{error && <div className="alerta error">{error}</div>}{!error && !pedidos.length && <p>Aún no tienes compras registradas.</p>}
    {pedidos.map((pedido) => <article className="tarjeta pedido-historial" key={pedido.idPedido}>
      <h3>Pedido #{pedido.idPedido}</h3><p>{pedido.fecha} · Total S/ {Number(pedido.total).toFixed(2)}</p>
      <ExperienciaCompra pedido={pedido} onActualizado={(actualizado) => setPedidos((lista) => lista.map((item) => item.idPedido === actualizado.idPedido ? actualizado : item))} />
    </article>)}
  </section>
}
