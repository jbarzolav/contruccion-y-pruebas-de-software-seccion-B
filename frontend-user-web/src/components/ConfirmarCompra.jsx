import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  confirmarCompra,
  listarPedidos,
  obtenerCarrito,
  obtenerTotalCarrito,
} from '../api/productos.js'
import { estadoPedidoVisible, fechaHoraPedidoVisible, numeroPedidoVisible } from '../utils/formatoPedidos.js'

const INTENTO_STORAGE = 'pulgatec_compra_pendiente'

function leerIntentoPendiente(clienteId) {
  try {
    const intento = JSON.parse(sessionStorage.getItem(INTENTO_STORAGE) || 'null')
    return intento?.clienteId === clienteId ? intento : null
  } catch {
    return null
  }
}

function guardarIntentoPendiente(intento) {
  try { sessionStorage.setItem(INTENTO_STORAGE, JSON.stringify(intento)) } catch { /* La clave también se conserva en memoria. */ }
}

function limpiarIntentoPendiente() {
  try { sessionStorage.removeItem(INTENTO_STORAGE) } catch { /* No hay persistencia disponible. */ }
}

function nuevaClaveIntento() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID()
  const bytes = new Uint8Array(16)
  window.crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function firmaDetalles(detalles = []) {
  return detalles.map(detalle => {
    const productoId = detalle.productoId ?? detalle.idProducto
    return `${productoId ?? 'sin-id'}:${Number(detalle.cantidad)}:${Number(detalle.precioUnitario).toFixed(2)}`
  }).sort().join('|')
}

function esRespuestaIncierta(error) {
  return !error?.response || error.response.status >= 500
}

export default function ConfirmarCompra({ onCarritoActualizado }) {
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [cargando, setCargando] = useState(true)
  const [enviando, setEnviando] = useState(false)
  const [consultando, setConsultando] = useState(false)
  const [puedeReintentar, setPuedeReintentar] = useState(false)
  const [error, setError] = useState('')
  const [pedido, setPedido] = useState(null)
  const [clienteId] = useState(() => Number(localStorage.getItem('pulgatec_usuario_id')))
  const [intentoPendiente, setIntentoPendiente] = useState(() => leerIntentoPendiente(
    Number(localStorage.getItem('pulgatec_usuario_id')),
  ))
  const navigate = useNavigate()

  useEffect(() => { cargarResumen() }, [])

  async function cargarResumen() {
    try {
      setCargando(true)
      setError('')
      const [lista, resultadoTotal] = await Promise.all([obtenerCarrito(), obtenerTotalCarrito()])
      setItems(lista)
      setTotal(Number(resultadoTotal.total))
    } catch (err) {
      setError(err?.response?.data?.messages?.[0] || 'No se pudo cargar el resumen del carrito.')
    } finally {
      setCargando(false)
    }
  }

  function completarCompra(resultado) {
    limpiarIntentoPendiente()
    setIntentoPendiente(null)
    setPuedeReintentar(false)
    setPedido(resultado)
    setItems([])
    setTotal(0)
    setError('')
    onCarritoActualizado?.()
  }

  async function enviarIntento(intento) {
    setEnviando(true)
    setError('')
    try {
      completarCompra(await confirmarCompra(intento.clave))
    } catch (err) {
      if (esRespuestaIncierta(err)) {
        setPuedeReintentar(false)
        setError('No se confirmó la respuesta del servidor. Consulta si el pedido quedó registrado antes de reintentar.')
      } else {
        limpiarIntentoPendiente()
        setIntentoPendiente(null)
        setPuedeReintentar(false)
        setError(err?.response?.data?.messages?.[0] || 'No se pudo registrar el pedido.')
      }
    } finally {
      setEnviando(false)
    }
  }

  async function manejarConfirmacion() {
    if (enviando || consultando || intentoPendiente || items.length === 0) return
    if (!window.confirm('¿Confirmar tu compra? El pedido quedará registrado.')) return
    setEnviando(true)

    let idsPedidosAntes = []
    let consultaBaseDisponible = false
    try {
      const anteriores = await listarPedidos({ page: 0, size: 100 })
      idsPedidosAntes = anteriores.map(anterior => String(anterior.idPedido))
      consultaBaseDisponible = true
    } catch {
      // La clave idempotente mantiene seguro el reintento aunque falle esta consulta previa.
    }

    const intento = {
      clienteId,
      clave: nuevaClaveIntento(),
      idsPedidosAntes,
      consultaBaseDisponible,
      firmaCarrito: firmaDetalles(items),
      iniciadoEn: Date.now(),
    }
    guardarIntentoPendiente(intento)
    setIntentoPendiente(intento)
    await enviarIntento(intento)
  }

  async function consultarPedidoRegistrado() {
    if (!intentoPendiente || consultando || enviando) return
    setConsultando(true)
    setError('')
    try {
      const recientes = await listarPedidos({ page: 0, size: 100 })
      const idsAnteriores = new Set(intentoPendiente.idsPedidosAntes || [])
      const candidato = intentoPendiente.consultaBaseDisponible && recientes.find(registro => {
        const esNuevo = !idsAnteriores.has(String(registro.idPedido))
        const fechaRegistro = new Date(registro.fecha).getTime()
        const creadoDuranteIntento = Number.isFinite(fechaRegistro)
          && fechaRegistro >= intentoPendiente.iniciadoEn - 5000
          && fechaRegistro <= Date.now() + 5000
        return esNuevo && creadoDuranteIntento
          && firmaDetalles(registro.detalles) === intentoPendiente.firmaCarrito
      })
      if (candidato) {
        completarCompra(candidato)
      } else {
        setPuedeReintentar(true)
        setError('No aparece un pedido nuevo con esos productos. Puedes reintentar con la misma clave de compra.')
      }
    } catch (err) {
      setError(err?.response?.data?.messages?.[0] || 'No se pudo consultar tu historial. Inténtalo de nuevo antes de reenviar la compra.')
    } finally {
      setConsultando(false)
    }
  }

  async function reintentarCompra() {
    if (!intentoPendiente || !puedeReintentar || enviando || consultando) return
    if (firmaDetalles(items) !== intentoPendiente.firmaCarrito) {
      setError('El carrito cambió desde el intento anterior. Recupera los mismos productos antes de reintentar.')
      return
    }
    await enviarIntento(intentoPendiente)
  }

  if (cargando) return <p>Cargando resumen de la compra...</p>

  if (pedido) {
    return <section className="tarjeta pedido-exito">
      <h2>Pedido registrado</h2>
      <p className="pedido-exito-id">N.º de pedido: <strong>{numeroPedidoVisible(pedido.idPedido)}</strong></p>
      <p>Estado: <strong>{estadoPedidoVisible(pedido.estado)}</strong></p>
      <p>Fecha: {fechaHoraPedidoVisible(pedido.fecha)}</p>
      <ul className="carrito-lista">
        {(pedido.detalles || []).map((detalle, indice) => <li key={`${detalle.idProducto}-${indice}`} className="carrito-item">
          <div className="carrito-item-datos">
            <strong>{detalle.nombreProducto}</strong>
            <span>{detalle.cantidad} × S/ {Number(detalle.precioUnitario).toFixed(2)}</span>
          </div>
          <div className="carrito-subtotal">Subtotal: <strong>S/ {Number(detalle.subtotal).toFixed(2)}</strong></div>
        </li>)}
      </ul>
      <div className="carrito-total">Total del pedido: <strong>S/ {Number(pedido.total).toFixed(2)}</strong></div>
      <div className="botones">
        <Link className="btn-primario" to={`/pedidos/${pedido.idPedido}`}>Ver pedido</Link>
        <button type="button" onClick={() => navigate('/')}>Seguir comprando</button>
      </div>
    </section>
  }

  if (intentoPendiente) {
    return <section className="tarjeta">
      <h2>Confirmación pendiente</h2>
      <p>Antes de enviar otra vez, consulta tu historial para comprobar si el pedido ya quedó registrado.</p>
      {error && <div className="alerta error" role="alert">{error}</div>}
      <div className="botones">
        <button className="btn-primario" type="button" onClick={consultarPedidoRegistrado} disabled={consultando || enviando}>
          {consultando ? 'Consultando pedidos...' : 'Consultar pedido registrado'}
        </button>
        {puedeReintentar && <button type="button" onClick={reintentarCompra} disabled={enviando || consultando || firmaDetalles(items) !== intentoPendiente.firmaCarrito}>
          {enviando ? 'Recuperando pedido...' : 'Reintentar con la misma clave'}
        </button>}
        <Link to="/pedidos" className="btn-secundario">Ir a mis compras</Link>
      </div>
    </section>
  }

  return <section className="tarjeta">
    <h2>Confirmar compra</h2>
    {error && <div className="alerta error" role="alert">{error}</div>}
    {items.length === 0 ? <>
      <p className="carrito-vacio">Tu carrito está vacío: agrega productos antes de confirmar la compra.</p>
      <div className="botones"><Link to="/" className="btn-secundario">Ir al catálogo</Link></div>
    </> : <>
      <ul className="carrito-lista">
        {items.map(item => <li key={item.id} className="carrito-item">
          <div className="carrito-item-datos">
            <strong>{item.nombreProducto}</strong>
            <span>{item.cantidad} × S/ {Number(item.precioUnitario).toFixed(2)}</span>
          </div>
          <div className="carrito-subtotal">Subtotal: <strong>S/ {Number(item.subtotal).toFixed(2)}</strong></div>
        </li>)}
      </ul>
      <div className="carrito-total">Total a pagar: <strong>S/ {total.toFixed(2)}</strong></div>
      <div className="botones">
        <button type="button" onClick={manejarConfirmacion} disabled={enviando || consultando}>
          {enviando ? 'Registrando pedido...' : 'Confirmar compra'}
        </button>
        <Link to="/" className="btn-secundario">Seguir comprando</Link>
      </div>
    </>}
  </section>
}
