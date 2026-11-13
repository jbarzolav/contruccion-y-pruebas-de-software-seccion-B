const ESTADOS_PEDIDO = {
  REGISTRADO: 'Registrado',
  EN_PROCESO: 'En proceso',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
}

export function estadoPedidoVisible(estado) {
  if (!estado) return ESTADOS_PEDIDO.REGISTRADO
  return ESTADOS_PEDIDO[estado] || String(estado).replaceAll('_', ' ').toLowerCase()
    .replace(/(^|\s)\S/g, (letra) => letra.toUpperCase())
}

export function fechaHoraPedidoVisible(valor, alternativa = 'Sin información') {
  if (!valor) return alternativa
  const fecha = new Date(valor)
  if (Number.isNaN(fecha.getTime())) return alternativa
  return new Intl.DateTimeFormat('es-PE', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(fecha)
}

export function numeroPedidoVisible(idPedido) {
  return idPedido == null ? '' : String(idPedido).padStart(6, '0')
}

export function resumenNotificacionVisible(aviso) {
  const idPedido = numeroPedidoVisible(aviso.idPedido)
  if (aviso.tipo === 'NUEVA_COMPRA') return `Nueva compra recibida · N.º de pedido: ${idPedido}.`
  // HU 53 - El aviso de mensaje ya llega redactado por el backend.
  if (aviso.tipo === 'NUEVO_MENSAJE') return aviso.resumen || 'Nuevo mensaje en una consulta.'
  // HU 58 - El aviso de stock bajo identifica la publicación del vendedor.
  if (aviso.tipo === 'STOCK_BAJO') {
    return aviso.resumen
      || `Stock bajo en la publicación N.º ${aviso.productoId ?? ''}.`
  }

  const estado = String(aviso.resumen || '').match(/cambió a\s+(.+?)[.]?$/i)?.[1]
  return estado
    ? `El pedido N.º ${idPedido} cambió a ${estadoPedidoVisible(estado.replace(/[.]$/, ''))}.`
    : `El estado del pedido N.º ${idPedido} fue actualizado.`
}
