import { Link } from 'react-router-dom'
import { fechaHoraPedidoVisible, resumenNotificacionVisible } from '../utils/formatoPedidos.js'

export default function AvisosNuevaCompra({ notificaciones }) {
  const avisos = notificaciones.filter((aviso) => aviso.tipo === 'NUEVA_COMPRA')
  if (!avisos.length) return null
  return <section className="tarjeta" aria-label="Avisos de nuevas compras">
    <h3>Nuevas compras</h3>
    {avisos.map((aviso) => <p key={aviso.idNotificacion}>
      {resumenNotificacionVisible(aviso)} · {fechaHoraPedidoVisible(aviso.fecha)}{' '}
      <Link to={aviso.enlace}>Ver pedido recibido</Link>
    </p>)}
  </section>
}
