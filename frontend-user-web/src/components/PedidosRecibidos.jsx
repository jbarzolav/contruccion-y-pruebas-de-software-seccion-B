import { useEffect, useState } from 'react'
import { listarPedidosRecibidos } from '../api/pedidosVendedor.js'

export default function PedidosRecibidos() {
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    listarPedidosRecibidos()
      .then(setPedidos)
      .catch((e) => setError(e?.response?.data?.messages?.[0] || 'No se pudieron cargar los pedidos recibidos.'))
      .finally(() => setCargando(false))
  }, [])

  if (cargando) return <section className="tarjeta"><p>Cargando pedidos recibidos...</p></section>
  return <section className="tarjeta pedidos-recibidos">
    <h2>Pedidos recibidos</h2>
    {error && <div className="alerta error" role="alert">{error}</div>}
    {!error && pedidos.length === 0 && <p>Aún no tienes pedidos que incluyan tus productos.</p>}
    {!error && pedidos.map((pedido) => <article className="pedido-historial" key={pedido.idPedido}>
      <h3>Pedido #{pedido.idPedido}</h3>
      <p>{pedido.fecha} · Estado: {pedido.estado} · Total de tus productos: S/ {Number(pedido.total).toFixed(2)}</p>
      <p>Última actualización: {pedido.fechaActualizacion ? new Date(pedido.fechaActualizacion).toLocaleString('es-PE') : 'Sin información'}</p>
      <ul>{pedido.productos.map((producto, indice) => <li key={`${producto.idProducto}-${indice}`}>
        {producto.nombreProducto} × {producto.cantidad} · S/ {Number(producto.subtotal).toFixed(2)}
      </li>)}</ul>
    </article>)}
  </section>
}
