import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { consultarEstadoPedido, listarPedidos } from '../api/productos.js'
import { listarNotificaciones } from '../api/notificaciones.js'
import AvisosEstadoPedido from './AvisosEstadoPedido.jsx'
import ExperienciaCompra from './ExperienciaCompra.jsx'
import { estadoPedidoVisible, fechaHoraPedidoVisible } from '../utils/formatoPedidos.js'

export default function Pedidos() {
  const [pedidos, setPedidos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [actualizandoId, setActualizandoId] = useState(null)
  const [notificaciones, setNotificaciones] = useState([])
  const [criterios, setCriterios] = useState({ estado: '', desde: '', hasta: '', montoMin: '', montoMax: '' })
  const [aplicados, setAplicados] = useState({})
  const [pagina, setPagina] = useState(0)
  useEffect(() => {
    setCargando(true)
    listarPedidos({ ...aplicados, page: pagina, size: 100 })
      .then(setPedidos)
      .catch((e) => setError(e?.response?.data?.messages?.[0] || 'No se pudo cargar tu historial.'))
      .finally(() => setCargando(false))
  }, [aplicados, pagina])
  useEffect(() => {
    listarNotificaciones().then(setNotificaciones).catch(() => {})
    const intervalo = window.setInterval(() => listarNotificaciones().then(setNotificaciones).catch(() => {}), 15000)
    return () => window.clearInterval(intervalo)
  }, [])

  function aplicarFiltros(evento) {
    evento.preventDefault()
    if (criterios.desde && criterios.hasta && criterios.desde > criterios.hasta) {
      setError('La fecha inicial no puede ser posterior a la fecha final.')
      return
    }
    if ((criterios.montoMin && Number(criterios.montoMin) < 0) || (criterios.montoMax && Number(criterios.montoMax) < 0)
      || (criterios.montoMin && criterios.montoMax && Number(criterios.montoMin) > Number(criterios.montoMax))) {
      setError('Revisa el rango de montos ingresado.')
      return
    }
    setError('')
    setAplicados(criterios)
    setPagina(0)
  }

  function limpiarFiltros() {
    const vacios = { estado: '', desde: '', hasta: '', montoMin: '', montoMax: '' }
    setCriterios(vacios)
    setAplicados({})
    setPagina(0)
    setError('')
  }

  async function actualizarEstado(idPedido) {
    if (actualizandoId !== null) return
    try {
      setActualizandoId(idPedido)
      setError('')
      const estado = await consultarEstadoPedido(idPedido)
      setPedidos((lista) => lista.map((pedido) => pedido.idPedido === idPedido
        ? { ...pedido, estado: estado.estado, fechaActualizacion: estado.fechaActualizacion }
        : pedido))
    } catch (e) {
      setError(e?.response?.data?.messages?.[0] || 'No se pudo actualizar el estado del pedido.')
    } finally {
      setActualizandoId(null)
    }
  }

  if (cargando) return <p>Cargando historial de compras...</p>
  return <section><h2>Mis compras</h2><AvisosEstadoPedido notificaciones={notificaciones} />
    <form className="filtros-pedidos" onSubmit={aplicarFiltros}>
      <label className="campo">Estado
        <select value={criterios.estado} onChange={(e) => setCriterios({ ...criterios, estado: e.target.value })}>
          <option value="">Todos</option><option value="REGISTRADO">Registrado</option>
          <option value="EN_PROCESO">En proceso</option><option value="ENTREGADO">Entregado</option>
          <option value="CANCELADO">Cancelado</option>
        </select>
      </label>
      <label className="campo">Desde<input type="date" value={criterios.desde} onChange={(e) => setCriterios({ ...criterios, desde: e.target.value })} /></label>
      <label className="campo">Hasta<input type="date" value={criterios.hasta} onChange={(e) => setCriterios({ ...criterios, hasta: e.target.value })} /></label>
      <label className="campo">Monto mínimo (S/)<input type="number" min="0" step="0.01" value={criterios.montoMin} onChange={(e) => setCriterios({ ...criterios, montoMin: e.target.value })} /></label>
      <label className="campo">Monto máximo (S/)<input type="number" min="0" step="0.01" value={criterios.montoMax} onChange={(e) => setCriterios({ ...criterios, montoMax: e.target.value })} /></label>
      <button className="btn-primario" type="submit">Aplicar filtros</button>
      <button className="btn-secundario" type="button" onClick={limpiarFiltros}>Limpiar</button>
    </form>
    {error && <div className="alerta error">{error}</div>}{!error && !pedidos.length && <p>{Object.values(aplicados).some(Boolean) ? 'No hay compras que coincidan con los filtros.' : 'Aún no tienes compras registradas.'}</p>}
    {!error && pedidos.map((pedido) => <article className="tarjeta pedido-historial" key={pedido.idPedido}>
      <h3>N.º de compra: {pedido.idPedido}</h3>
      <p>{fechaHoraPedidoVisible(pedido.fecha)} · Estado: {estadoPedidoVisible(pedido.estado)} · Total S/ {Number(pedido.total).toFixed(2)}</p>
      <p>Última actualización: {fechaHoraPedidoVisible(pedido.fechaActualizacion)}</p>
      {/* HU 50 - Vista con productos, cantidades, precios y total. */}
      <Link className="btn-secundario" to={`/pedidos/${pedido.idPedido}`}>Ver detalle</Link>
      <button className="btn-secundario" type="button" disabled={actualizandoId !== null}
        onClick={() => actualizarEstado(pedido.idPedido)}>
        {actualizandoId === pedido.idPedido ? 'Actualizando...' : 'Actualizar estado'}
      </button>
      <ExperienciaCompra pedido={pedido} onActualizado={(actualizado) => setPedidos((lista) => lista.map((item) => item.idPedido === actualizado.idPedido ? actualizado : item))} />
    </article>)}
    {(pagina > 0 || pedidos.length === 100) && <div className="paginacion-pedidos">
      <button className="btn-secundario" type="button" disabled={pagina === 0 || cargando} onClick={() => setPagina((actual) => actual - 1)}>Anterior</button>
      <span>Página {pagina + 1}</span>
      <button className="btn-secundario" type="button" disabled={pedidos.length < 100 || cargando} onClick={() => setPagina((actual) => actual + 1)}>Siguiente</button>
    </div>}
  </section>
}
