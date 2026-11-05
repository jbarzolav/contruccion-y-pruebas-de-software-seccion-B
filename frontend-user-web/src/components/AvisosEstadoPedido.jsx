import { Link } from 'react-router-dom'
import { fechaHoraPedidoVisible, resumenNotificacionVisible } from '../utils/formatoPedidos.js'

export default function AvisosEstadoPedido({ notificaciones }) {
  const avisos = notificaciones.filter((aviso) => aviso.tipo === 'CAMBIO_ESTADO_PEDIDO')
  if (!avisos.length) return null
  return <section className="tarjeta" aria-label="Avisos sobre el estado de tus pedidos">
    <h3>Actualizaciones de tus pedidos</h3>
    {avisos.map((aviso) => <p key={aviso.idNotificacion}>
      {resumenNotificacionVisible(aviso)} · {fechaHoraPedidoVisible(aviso.fecha)}{' '}
      <Link to={aviso.enlace}>Ver pedido</Link>
    </p>)}
  </section>
}
