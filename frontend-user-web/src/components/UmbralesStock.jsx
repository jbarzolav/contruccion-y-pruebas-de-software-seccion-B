import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { guardarUmbralStock, listarAvisosStockBajo, listarUmbralesStock } from '../api/umbralesStock.js'
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
export default function UmbralesStock({ onEditar }) {
  const [publicaciones, setPublicaciones] = useState([])
  const [avisos, setAvisos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [ediciones, setEdiciones] = useState({})
  const [errores, setErrores] = useState({})
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
  }, [])

  /** Valida el umbral introducido: entero mayor o igual a cero. */
  function validarUmbral(valor) {
    const texto = String(valor ?? '').trim()
    if (texto === '') return 'Indica un umbral (0 o mayor).'
    if (!/^\d+$/.test(texto)) return 'El umbral debe ser un número entero.'
    if (Number(texto) < 0) return 'El umbral no puede ser negativo.'
    return ''
  }

  async function guardar(publicacion) {
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

  if (cargando) {
    return <section className="tarjeta umbrales-stock"><h2>Umbrales de stock</h2>
      <p role="status">Cargando umbrales de stock…</p>
    </section>
  }

  return <section className="tarjeta umbrales-stock">
    <h2>Umbrales de stock</h2>
    <p className="umbrales-explicacion">
      Configura el mínimo de unidades que quieres mantener en cada publicación.
      Te avisaremos cuando el stock cruce ese umbral (una sola vez por cada
      Bajada); si repones el stock, la alerta se rearma para el siguiente cruce.
    </p>

    {error && <div className="alerta error" role="alert">{error}</div>}
    {mensaje && <div className="alerta exito" role="status">{mensaje}</div>}

    <h3>Mis publicaciones</h3>
    {publicaciones.length === 0 && <p>Todavía no tienes publicaciones publicadas.</p>}

    {!publicaciones.length ? null : <div className="umbrales-lista">
      {publicaciones.map((publicacion) => {
        const sinUmbral = publicacion.umbral === null || publicacion.umbral === undefined
        return <article className="umbral-item" id={`producto-${publicacion.idProducto}`}
          key={publicacion.idProducto}>
          <div className="umbral-item-info">
            <h4>{publicacion.nombre}</h4>
            <p>Stock actual: <strong>{publicacion.stock}</strong> unidad(es)</p>
            {sinUmbral
              ? <span className="badge">Sin umbral configurado</span>
              : <p>Umbral configurado: <strong>{publicacion.umbral}</strong></p>}
            {publicacion.stockBajo && <span className="badge badge-stock-bajo">Stock bajo</span>}
            {publicacion.alertaNotificada && !publicacion.stockBajo &&
              <span className="badge badge-avisado">Aviso ya notificado</span>}
          </div>
          <div className="umbral-item-edicion">
            <label className="campo" htmlFor={`umbral-${publicacion.idProducto}`}>Umbral mínimo</label>
            <input
              id={`umbral-${publicacion.idProducto}`}
              type="number"
              min="0"
              step="1"
              inputMode="numeric"
              value={ediciones[publicacion.idProducto] ?? ''}
              onChange={(e) => setEdiciones((actual) => ({ ...actual, [publicacion.idProducto]: e.target.value }))}
            />
            {errores[publicacion.idProducto] &&
              <p className="error">{errores[publicacion.idProducto]}</p>}
            <div className="botones">
              <button className="btn-primario" type="button"
                disabled={guardandoId === publicacion.idProducto}
                onClick={() => guardar(publicacion)}>
                {guardandoId === publicacion.idProducto ? 'Guardando...' : 'Guardar umbral'}
              </button>
              {onEditar && <button className="btn-secundario" type="button"
                onClick={() => onEditar(publicacion)}>
                Gestionar publicación
              </button>}
              <Link className="btn-secundario" to={`/producto/${publicacion.idProducto}`}>Ver publicación</Link>
            </div>
          </div>
        </article>
      })}
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
