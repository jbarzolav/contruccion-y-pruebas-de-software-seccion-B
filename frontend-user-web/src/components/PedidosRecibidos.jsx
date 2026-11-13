import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { actualizarEstadoPedido, listarPedidosRecibidos } from '../api/pedidosVendedor.js'
import { estadoPedidoVisible, fechaHoraPedidoVisible, numeroPedidoVisible } from '../utils/formatoPedidos.js'

const PEDIDOS_POR_PAGINA = 12
const FILTROS_INICIALES = { estado: '', desde: '', hasta: '', productoId: '' }

/** HU 48 - Pedidos recibidos por el vendedor. HU 49 - Actualización del estado. */
export default function PedidosRecibidos() {
  const { hash } = useLocation()
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [seleccion, setSeleccion] = useState({})
  const [guardandoId, setGuardandoId] = useState(null)
  const [criterios, setCriterios] = useState(FILTROS_INICIALES)
  const [aplicados, setAplicados] = useState({})
  const [pagina, setPagina] = useState(null)
  const [numero, setNumero] = useState('')

  useEffect(() => {
    if (hash.startsWith('#pedido-')) {
      setNumero('')
      setPagina(null)
      setCriterios(FILTROS_INICIALES)
      setAplicados(FILTROS_INICIALES)
    }
  }, [hash])

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError('')
    async function cargarPedidos() {
      try {
        const lista = []
        const size = 100
        for (let page = 0; ; page += 1) {
          const lote = await listarPedidosRecibidos({ ...aplicados, page, size })
          if (!vigente) return
          lista.push(...lote)
          if (lote.length < size) break
        }
        setPedidos(lista)
      } catch (e) {
        if (vigente) setError(e?.response?.data?.messages?.[0] || 'No se pudieron cargar los pedidos recibidos.')
      } finally {
        if (vigente) setCargando(false)
      }
    }
    cargarPedidos()
    return () => { vigente = false }
  }, [aplicados])

  function aplicarFiltros(evento) {
    evento.preventDefault()
    if (criterios.desde && criterios.hasta && criterios.desde > criterios.hasta) {
      setError('La fecha inicial no puede ser posterior a la fecha final.')
      return
    }
    setError('')
    setAplicados(criterios)
    setPagina(0)
  }

  function limpiarFiltros() {
    setCriterios(FILTROS_INICIALES)
    setNumero('')
    setAplicados({})
    setPagina(0)
    setError('')
  }

  const productos = [...new Map(pedidos.flatMap((pedido) => pedido.productos || [])
    .filter((producto) => producto.idProducto != null)
    .map((producto) => [producto.idProducto, producto])).values()]

  const termino = numero.trim()
  const resultados = pedidos.filter(pedido => numeroPedidoVisible(pedido.idPedido).includes(termino))
  const indiceDestino = resultados.findIndex(pedido => hash === `#pedido-${pedido.idPedido}`)
  const paginaActual = pagina ?? Math.max(0, Math.floor(indiceDestino / PEDIDOS_POR_PAGINA))
  const totalPaginas = Math.ceil(resultados.length / PEDIDOS_POR_PAGINA)
  const inicio = paginaActual * PEDIDOS_POR_PAGINA

  /** HU 49.1 - Guarda el estado elegido y confirma el resultado. */
  async function guardarEstado(pedido) {
    const nuevoEstado = seleccion[pedido.idPedido]
    if (!nuevoEstado || guardandoId !== null) return
    setGuardandoId(pedido.idPedido)
    setError('')
    setMensaje('')
    try {
      const respuesta = await actualizarEstadoPedido(pedido.idPedido, nuevoEstado)
      setPedidos((lista) => lista.map((item) => item.idPedido === pedido.idPedido
        ? {
          ...item,
          estado: respuesta.estado,
          fechaActualizacion: respuesta.fechaActualizacion,
          estadosPermitidos: respuesta.estadosPermitidos,
        }
        : item))
      setSeleccion((actual) => ({ ...actual, [pedido.idPedido]: '' }))
      setMensaje(`N.º de pedido: ${numeroPedidoVisible(pedido.idPedido)} · Estado actualizado a ${estadoPedidoVisible(respuesta.estado)}.`)
    } catch (e) {
      setError(e?.response?.data?.messages?.[0] || 'No se pudo actualizar el estado del pedido.')
    } finally {
      setGuardandoId(null)
    }
  }

  if (cargando) return <section className="tarjeta"><p>Cargando pedidos recibidos...</p></section>
  return <section className="tarjeta pedidos-recibidos">
    <header className="mis-compras-cabecera">
      <h2>Pedidos recibidos</h2>
      <Link className="btn-secundario" to="/notificaciones">Ver notificaciones</Link>
    </header>
    <form className="filtros-pedidos" onSubmit={aplicarFiltros}>
      <label className="campo">N.º de pedido
        <input type="search" inputMode="numeric" placeholder="Ej. 000006" value={numero}
          onChange={(e) => { setNumero(e.target.value); setPagina(0) }} />
      </label>
      <label className="campo">Estado
        <select value={criterios.estado} onChange={(e) => setCriterios({ ...criterios, estado: e.target.value })}>
          <option value="">Todos</option><option value="REGISTRADO">Registrado</option>
          <option value="EN_PROCESO">En proceso</option><option value="ENTREGADO">Entregado</option>
          <option value="CANCELADO">Cancelado</option>
        </select>
      </label>
      <label className="campo">Producto
        <select value={criterios.productoId} onChange={(e) => setCriterios({ ...criterios, productoId: e.target.value })}>
          <option value="">Todos</option>
          {productos.map((producto) => <option value={producto.idProducto} key={producto.idProducto}>{producto.nombreProducto}</option>)}
        </select>
      </label>
      <details className="compras-filtros-adicionales">
        <summary>Más filtros</summary>
        <div className="compras-filtros-campos">
          <label className="campo">Desde<input type="date" value={criterios.desde} onChange={(e) => setCriterios({ ...criterios, desde: e.target.value })} /></label>
          <label className="campo">Hasta<input type="date" value={criterios.hasta} onChange={(e) => setCriterios({ ...criterios, hasta: e.target.value })} /></label>
        </div>
      </details>
      <div className="pedidos-recibidos-acciones">
        <button className="btn-secundario" type="button" onClick={limpiarFiltros}>Limpiar</button>
        <button className="btn-primario" type="submit">Aplicar filtros</button>
      </div>
    </form>
    {error && <div className="alerta error" role="alert">{error}</div>}
    {mensaje && <div className="alerta" role="status">{mensaje}</div>}
    {!error && resultados.length === 0 && <p>{(termino || Object.values(aplicados).some(Boolean)) ? 'No hay pedidos que coincidan con los filtros.' : 'Aún no tienes pedidos que incluyan tus productos.'}</p>}
    {!error && resultados.slice(inicio, inicio + PEDIDOS_POR_PAGINA).map((pedido) => {
      const permitidos = pedido.estadosPermitidos || []
      const elegido = seleccion[pedido.idPedido] || ''
      return <article className="pedido-historial" id={`pedido-${pedido.idPedido}`} key={pedido.idPedido}>
        <header className="mis-compras-cabecera">
          <div>
            <h3>N.º de pedido: {numeroPedidoVisible(pedido.idPedido)}</h3>
            <p>{fechaHoraPedidoVisible(pedido.fecha)}</p>
          </div>
          <span className="compra-estado" data-estado={pedido.estado}>{estadoPedidoVisible(pedido.estado)}</span>
        </header>
        <ul className="pedido-recibido-productos" aria-label="Tus productos">
          {pedido.productos.map((producto, indice) => <li key={`${producto.idProducto}-${indice}`}>
            <span>
              {producto.idProducto != null ? (
                <Link className="pedido-enlace-producto" to={`/producto/${producto.idProducto}`}>
                  {producto.nombreProducto}
                </Link>
              ) : <strong>{producto.nombreProducto}</strong>}
              {' '}<small>× {producto.cantidad}</small>
            </span>
            <span>S/ {Number(producto.subtotal).toFixed(2)}</span>
          </li>)}
        </ul>
        <div className="pedido-recibido-resumen">
          <small>Última actualización: {fechaHoraPedidoVisible(pedido.fechaActualizacion)}</small>
          <span>Total de tus productos <strong>S/ {Number(pedido.total).toFixed(2)}</strong></span>
        </div>
        {permitidos.length > 0 && <div className="pedido-recibido-estado">
          <label className="campo" htmlFor={`estado-${pedido.idPedido}`}>Actualizar estado</label>
          <select
            id={`estado-${pedido.idPedido}`}
            value={elegido}
            disabled={guardandoId !== null}
            onChange={(evento) => setSeleccion((actual) => ({ ...actual, [pedido.idPedido]: evento.target.value }))}
          >
            <option value="">Selecciona un estado</option>
            {permitidos.map((estado) => <option key={estado} value={estado}>{estadoPedidoVisible(estado)}</option>)}
          </select>
          <button
            className="btn-primario"
            type="button"
            disabled={!elegido || guardandoId !== null}
            onClick={() => guardarEstado(pedido)}
          >
            {guardandoId === pedido.idPedido ? 'Guardando...' : 'Guardar estado'}
          </button>
        </div>}
      </article>
    })}
    {!error && totalPaginas > 1 && <nav className="paginacion-pedidos" aria-label="Páginas de pedidos recibidos">
      <button className="btn-secundario" type="button" disabled={paginaActual === 0}
        onClick={() => setPagina(paginaActual - 1)}>Anterior</button>
      <span aria-live="polite">Página {paginaActual + 1} de {totalPaginas}</span>
      <button className="btn-secundario" type="button" disabled={paginaActual >= totalPaginas - 1}
        onClick={() => setPagina(paginaActual + 1)}>Siguiente</button>
    </nav>}
  </section>
}
