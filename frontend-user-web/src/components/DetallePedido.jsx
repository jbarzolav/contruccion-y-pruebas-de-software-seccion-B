import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import ExperienciaCompra from './ExperienciaCompra.jsx'
import { obtenerDetallePedido, obtenerExperienciaPedido } from '../api/productos.js'
import { estadoPedidoVisible, fechaHoraPedidoVisible, numeroPedidoVisible } from '../utils/formatoPedidos.js'

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
    let vigente = true
    setCargando(true)
    setError('')
    obtenerDetallePedido(idPedido)
      .then(data => { if (vigente) setPedido(data) })
      .catch(e => { if (vigente) setError(e?.response?.data?.messages?.[0] || 'No se pudo cargar el detalle del pedido.') })
      .finally(() => { if (vigente) setCargando(false) })
    return () => { vigente = false }
  }, [idPedido])

  if (cargando) return <section className="tarjeta"><p>Cargando detalle del pedido...</p></section>

  return <section className="tarjeta">
    {error ? <>
      <div className="alerta error" role="alert">{error}</div>
      <Link className="btn-secundario" to="/pedidos">Volver a mis compras</Link>
    </> : <>
      <header className="mis-compras-cabecera">
        <div>
          <h2>N.º de pedido: {numeroPedidoVisible(pedido.idPedido)}</h2>
          <p>Fecha de compra: {fechaHoraPedidoVisible(pedido.fecha)}</p>
          <p>Última actualización: {fechaHoraPedidoVisible(pedido.fechaActualizacion)}</p>
        </div>
        <span className="compra-estado" data-estado={pedido.estado}>
          {estadoPedidoVisible(pedido.estado)}
        </span>
      </header>
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
            <td>
              {detalle.idProducto != null ? (
                <Link className="pedido-enlace-producto" to={`/producto/${detalle.idProducto}`}>
                  {detalle.nombreProducto} <span aria-hidden="true">→</span>
                </Link>
              ) : detalle.nombreProducto}
            </td>
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
      {pedido.estado === 'ENTREGADO' && <CalificacionPedido key={pedido.idPedido} idPedido={pedido.idPedido} />}
      <Link className="btn-secundario" to="/pedidos">Volver a mis compras</Link>
    </>}
  </section>
}

function CalificacionPedido({ idPedido }) {
  const [pedido, setPedido] = useState(null)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState('')

  async function cargar() {
    if (cargando || pedido) return
    setCargando(true)
    setError('')
    try {
      const resultado = await obtenerExperienciaPedido(idPedido)
      if (resultado?.estado === 'ENTREGADO') setPedido(resultado)
      else setError('La calificación no está disponible para este pedido.')
    } catch {
      setError('No se pudo cargar la calificación del pedido.')
    } finally {
      setCargando(false)
    }
  }

  return <details className="compra-experiencia" onToggle={evento => {
    if (evento.currentTarget.open) cargar()
  }}>
    <summary>Calificar mi compra</summary>
    {cargando && <p role="status">Cargando calificación…</p>}
    {error && <p role="alert">{error} <button type="button" onClick={cargar}>Reintentar</button></p>}
    {pedido && <ExperienciaCompra pedido={pedido} onActualizado={setPedido} mostrarTitulo={false} />}
  </details>
}
