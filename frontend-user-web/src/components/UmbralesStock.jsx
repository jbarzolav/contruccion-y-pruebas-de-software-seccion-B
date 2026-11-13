import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { guardarUmbralStock, listarAvisosStockBajo, listarUmbralesStock } from '../api/umbralesStock.js'
import { obtenerMisProductos } from '../api/productos.js'
import { fechaHoraPedidoVisible } from '../utils/formatoPedidos.js'

/**
 * HU 58 - Panel de umbrales de stock y avisos de stock bajo del vendedor.
 *
 * Estados contemplados: cargando, con datos, sin publicaciones, sin umbral
 * configurado, error de conexión y error de permisos.
 *
 * El aviso se emite solo en el cruce descendente del umbral y se rearma tras
 * una reposicion. Al configurar un umbral con el stock ya por debajo NO se
 * genera un aviso inmediato: el panel muestra el estado "Stock bajo".
 */
export default function UmbralesStock({ onEditar, actualizar }) {
  const [publicaciones, setPublicaciones] = useState([])
  const [avisos, setAvisos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [abriendoId, setAbriendoId] = useState(null)
  const [mensaje, setMensaje] = useState('')
  const [ediciones, setEdiciones] = useState({})
  const [errores, setErrores] = useState({})
  const [filtro, setFiltro] = useState('')
  const [editandoId, setEditandoId] = useState(null)
  const [guardandoId, setGuardandoId] = useState(null)

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError('')
    Promise.all([listarUmbralesStock(), listarAvisosStockBajo()])
      .then(([lista, listaAvisos]) => {
        if (!vigente) return
        setPublicaciones(lista || [])
        setAvisos(listaAvisos || [])
        // Precarga el valor actual del umbral en cada campo editable.
        const valores = {}
        for (const publicacion of lista || []) {
          valores[publicacion.idProducto] = publicacion.umbral === null || publicacion.umbral === undefined
            ? ''
            : String(publicacion.umbral)
        }
        setEdiciones(valores)
      })
      .catch((e) => {
        if (!vigente) return
        const estado = e?.response?.status
        const mensajeServidor = e?.response?.data?.messages?.[0]
        if (!e?.response) {
          setError('No se pudo conectar con el servidor. Revisa tu conexión a internet.')
        } else if (estado === 403) {
          setError(mensajeServidor || 'No tienes permiso para gestionar los umbrales de stock.')
        } else if (estado === 401) {
          setError('Tu sesión venció. Inicia sesión nuevamente.')
        } else {
          setError(mensajeServidor || 'No se pudieron cargar los umbrales de stock.')
        }
      })
      .finally(() => { if (vigente) setCargando(false) })
    return () => { vigente = false }
  }, [actualizar])

  /** Valida el umbral introducido: entero mayor o igual a cero. */
  function validarUmbral(valor) {
    const texto = String(valor ?? '').trim()
    if (texto === '') return 'Indica un umbral (0 o mayor).'
    if (!/^\d+$/.test(texto)) return 'El umbral debe ser un número entero.'
    if (Number(texto) < 0) return 'El umbral no puede ser negativo.'
    return ''
  }

  async function guardar(publicacion) {
    if (guardandoId !== null) return
    const valor = ediciones[publicacion.idProducto]
    const problema = validarUmbral(valor)
    if (problema) {
      setErrores((actual) => ({ ...actual, [publicacion.idProducto]: problema }))
      return
    }
    setGuardandoId(publicacion.idProducto)
    setError('')
    setMensaje('')
    setErrores((actual) => ({ ...actual, [publicacion.idProducto]: '' }))
    try {
      const respuesta = await guardarUmbralStock(publicacion.idProducto, Number(String(valor).trim()))
      setPublicaciones((lista) => lista.map((item) => item.idProducto === publicacion.idProducto
        ? { ...item, ...respuesta }
        : item))
      setEditandoId(null)
      setMensaje(`Umbral guardado para «${publicacion.nombre}».`)
      // El aviso solo se emite al cruzar el umbral: recargamos la lista.
      listarAvisosStockBajo().then(setAvisos).catch(() => {})
    } catch (e) {
      const mensajeServidor = e?.response?.data?.messages?.[0]
      setError(mensajeServidor || 'No se pudo guardar el umbral de stock.')
    } finally {
      setGuardandoId(null)
    }
  }

  async function gestionarPublicacion(id) {
    setAbriendoId(id)
    setError('')
    try {
      const productos = await obtenerMisProductos()
      const producto = productos.find(item => item.id === id)
      if (!producto) {
        setError('La publicación ya no está disponible para editar.')
        return
      }
      onEditar(producto)
    } catch {
      setError('No se pudo cargar la publicación. Inténtalo de nuevo.')
    } finally {
      setAbriendoId(null)
    }
  }

  const visibles = publicaciones.filter(producto => {
    if (filtro === 'agotados') return producto.stock === 0
    if (filtro === 'bajo') return producto.stockBajo
    if (filtro === 'sin-alerta') return producto.umbral == null
    return true
  })

  if (cargando) {
    return <section className="tarjeta umbrales-stock"><h2>Alertas de stock</h2>
      <p role="status">Cargando umbrales de stock…</p>
    </section>
  }

  return <section className="tarjeta umbrales-stock">
    <h2>Alertas de stock</h2>
    <p className="umbrales-explicacion">
      Define el mínimo de unidades para recibir un aviso cuando el stock baje hasta ese nivel o menos.
    </p>

    {error && <div className="alerta error" role="alert">{error}</div>}
    {mensaje && <div className="alerta exito" role="status">{mensaje}</div>}

    {publicaciones.length > 0 && <label className="campo umbrales-filtro">
      Filtrar publicaciones
      <select value={filtro} onChange={evento => {
        setFiltro(evento.target.value)
        setEditandoId(null)
      }}>
        <option value="">Todas</option>
        <option value="agotados">Agotados</option>
        <option value="bajo">Stock bajo</option>
        <option value="sin-alerta">Sin alerta</option>
      </select>
    </label>}
    {!error && publicaciones.length === 0 && <p>Aún no tienes publicaciones.</p>}
    {publicaciones.length > 0 && visibles.length === 0 && <p role="status">No hay publicaciones para este filtro.</p>}

    {visibles.length > 0 && <div className="umbrales-lista">
      <table aria-label="Alertas de stock por publicación">
        <thead>
          <tr><th scope="col">Producto</th><th scope="col">Stock actual</th><th scope="col">Alerta</th><th scope="col">Acciones</th></tr>
        </thead>
        <tbody>
          {visibles.map(publicacion => {
            const id = publicacion.idProducto
            const editando = editandoId === id
            return <Fragment key={id}>
              <tr id={`producto-${id}`}>
                <th scope="row"><Link className="pedido-enlace-producto" to={`/producto/${id}`}>{publicacion.nombre}</Link></th>
                <td>{publicacion.stock === 0
                  ? <span className="badge badge-stock-bajo">0 · Agotado</span>
                  : <>{publicacion.stock}{publicacion.stockBajo && <span className="badge badge-stock-bajo">Stock bajo</span>}</>}</td>
                <td>{publicacion.umbral == null ? 'Sin configurar' : `Mínimo: ${publicacion.umbral}`}</td>
                <td><div className="umbral-acciones">
                  <button className="btn-secundario" type="button" aria-expanded={editando}
                    disabled={guardandoId !== null} onClick={() => setEditandoId(editando ? null : id)}>
                    {editando ? 'Cerrar edición' : publicacion.umbral == null ? 'Configurar alerta' : 'Editar alerta'}
                  </button>
                  {onEditar && <button className="btn-secundario" type="button"
                    disabled={abriendoId !== null}
                    onClick={() => gestionarPublicacion(id)}>
                    {abriendoId === id ? 'Abriendo…' : 'Gestionar publicación'}
                  </button>}
                </div></td>
              </tr>
              {editando && <tr><td colSpan={4}>
                <form className="umbral-item-edicion" onSubmit={evento => {
                  evento.preventDefault()
                  guardar(publicacion)
                }}>
                  <label className="campo">Umbral mínimo
                    <input type="number" min="0" step="1" inputMode="numeric" required
                      disabled={guardandoId !== null} value={ediciones[id] ?? ''}
                      onChange={evento => setEdiciones(actual => ({ ...actual, [id]: evento.target.value }))} />
                  </label>
                  <button className="btn-primario" type="submit" disabled={guardandoId !== null}>
                    {guardandoId === id ? 'Guardando…' : 'Guardar'}
                  </button>
                  {errores[id] && <p role="alert">{errores[id]}</p>}
                </form>
              </td></tr>}
            </Fragment>
          })}
        </tbody>
      </table>
    </div>}

    <h3>Avisos de stock bajo</h3>
    {avisos.length === 0 && <p>No tienes avisos de stock bajo por ahora.</p>}
    {avisos.length > 0 && <ul className="umbrales-avisos">
      {avisos.map((aviso) => <li key={aviso.idNotificacion}>
        <span className="badge badge-stock-bajo">Stock bajo</span>{' '}
        {aviso.nombre ? <Link to={`/producto/${aviso.idProducto}`}>{aviso.nombre}</Link> : `Publicación ${aviso.idProducto}`}
        {' · '}stock actual {aviso.stockActual ?? '—'}
        {aviso.umbral !== null && aviso.umbral !== undefined && ` · umbral ${aviso.umbral}`}
        {' · '}{fechaHoraPedidoVisible(aviso.fecha)}
      </li>)}
    </ul>}
  </section>
}
