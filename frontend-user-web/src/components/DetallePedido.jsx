import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { obtenerDetallePedido } from '../api/productos.js'

/**
 * HU 50 - Detalle de un pedido propio.
 * Muestra cabecera (fecha, estado), productos, cantidades, precios unitarios,
 * subtotales y total; permite volver al historial de compras.
 */
export default function DetallePedido() {
  const { idPedido } = useParams()
  const [pedido, setPedido] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    obtenerDetallePedido(idPedido)
      .then(setPedido)
      .catch((e) => setError(e?.response?.data?.messages?.[0] || 'No se pudo cargar el detalle del pedido.'))
      .finally(() => setCargando(false))
  }, [idPedido])

  if (cargando) return <section className="tarjeta"><p>Cargando detalle del pedido...</p></section>

  return <section className="tarjeta">
    {error ? <>
      <div className="alerta error" role="alert">{error}</div>
      <Link className="btn-secundario" to="/pedidos">Volver a mis compras</Link>
    </> : <>
      <h2>Detalle del pedido #{pedido.idPedido}</h2>
      <p>
        Fecha: {pedido.fecha ? new Date(pedido.fecha).toLocaleString('es-PE') : 'Sin información'} ·
        Estado: {pedido.estado || 'REGISTRADO'}
      </p>
      <p>Última actualización del estado: {pedido.fechaActualizacion ? new Date(pedido.fechaActualizacion).toLocaleString('es-PE') : 'Sin información'}</p>
      <table className="tabla">
        <thead>
          <tr>
            <th>Producto</th>
            <th>Cantidad</th>
            <th>Precio unitario</th>
            <th>Subtotal</th>
          </tr>
        </thead>
        <tbody>
          {pedido.detalles.map((detalle, indice) => <tr key={`${detalle.idProducto}-${indice}`}>
            <td>{detalle.nombreProducto}</td>
            <td>{detalle.cantidad}</td>
            <td>S/ {Number(detalle.precioUnitario).toFixed(2)}</td>
            <td>S/ {Number(detalle.subtotal).toFixed(2)}</td>
          </tr>)}
        </tbody>
        <tfoot>
          <tr>
            <th colSpan="3">Total</th>
            <th>S/ {Number(pedido.total).toFixed(2)}</th>
          </tr>
        </tfoot>
      </table>
      <Link className="btn-secundario" to="/pedidos">Volver a mis compras</Link>
    </>}
  </section>
}
