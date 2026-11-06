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

export function resumenNotificacionVisible(aviso) {
  const idPedido = aviso.idPedido ?? ''
  if (aviso.tipo === 'NUEVA_COMPRA') return `Nueva compra recibida · N.º de compra: ${idPedido}.`
  // HU 53 - El aviso de mensaje ya llega redactado por el backend.
  if (aviso.tipo === 'NUEVO_MENSAJE') return aviso.resumen || 'Nuevo mensaje en una consulta.'

  const estado = String(aviso.resumen || '').match(/cambió a\s+(.+?)[.]?$/i)?.[1]
  return estado
    ? `La compra N.º ${idPedido} cambió a ${estadoPedidoVisible(estado.replace(/[.]$/, ''))}.`
    : `El estado de la compra N.º ${idPedido} fue actualizado.`
}
