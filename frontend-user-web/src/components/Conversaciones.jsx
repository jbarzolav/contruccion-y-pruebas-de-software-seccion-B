import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { listarConversaciones, listarMensajesConversacion, responderConsulta } from '../api/conversaciones.js'
import { marcarMensajesLeidos } from '../api/notificaciones.js'
import { urlImagen } from '../api/productos.js'
import { useSesion } from '../api/sesion.js'
import { fechaHoraPedidoVisible } from '../utils/formatoPedidos.js'
import FormularioConsulta from './FormularioConsulta.jsx'

const CONSULTAS_POR_PAGINA = 8

/** HU38 - Lista conversaciones propias y permite consultar el historial paginado. */
export default function Conversaciones({ avisosPendientes }) {
    const sesion = useSesion()
    const ubicacion = useLocation()
    // HU 53 - Enlace ?c= desde la bandeja: abre la conversación indicada.
    const conversacionInicial = new URLSearchParams(ubicacion.search).get('c')
    const historial = useRef(null)
    const [lista, setLista] = useState([])
    const [paginaLista, setPaginaLista] = useState(0)
    const [seleccion, setSeleccion] = useState(null)
    const [mensajes, setMensajes] = useState([])
    const [historialCargado, setHistorialCargado] = useState(null)
    const [pagina, setPagina] = useState(0)
    const [tieneSiguiente, setTieneSiguiente] = useState(false)
    const [cargandoMensajes, setCargandoMensajes] = useState(false)
    const [cargandoAnteriores, setCargandoAnteriores] = useState(false)
    const [errorMensajes, setErrorMensajes] = useState('')
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [avisosLeidos, setAvisosLeidos] = useState(() => new Set())
    const [version, setVersion] = useState(0)
    useEffect(() => {
        let vigente = true
        setCargando(true)
        setError('')
        listarConversaciones().then(data => {
            if (!vigente) return
            actualizarLista(data)
            // HU 53 - Selección aditiva: solo si aún no hay conversación abierta.
            const idInicial = Number(conversacionInicial)
            if (Number.isInteger(idInicial) && idInicial > 0 && !seleccion &&
                data.some(c => c.idConversacion === idInicial)) {
                setSeleccion(idInicial)
                setPaginaLista(Math.floor(data.findIndex(c => c.idConversacion === idInicial) / CONSULTAS_POR_PAGINA))
            }
        })
            .catch(err => { if (vigente) setError(err?.response?.data?.messages?.[0] || 'No se pudieron cargar las consultas.') })
            .finally(() => { if (vigente) setCargando(false) })
        return () => { vigente = false }
    }, [version, sesion?.token])
    const pendientesPorChat = new Map()
    for (const aviso of avisosPendientes) {
        if (avisosLeidos.has(String(aviso.idNotificacion))) continue
        const ids = pendientesPorChat.get(aviso.idConversacion) ?? []
        ids.push(aviso.idNotificacion)
        pendientesPorChat.set(aviso.idConversacion, ids)
    }
    const avisosChat = (pendientesPorChat.get(seleccion) ?? []).join(',')

    const conversacion = lista.find(c => c.idConversacion === seleccion)
    const totalPaginas = Math.ceil(lista.length / CONSULTAS_POR_PAGINA)
    const inicioPagina = paginaLista * CONSULTAS_POR_PAGINA
    const consultasVisibles = lista.slice(inicioPagina, inicioPagina + CONSULTAS_POR_PAGINA)

    function actualizarLista(data) {
        setLista(data)
        const ultimaPagina = Math.max(0, Math.ceil(data.length / CONSULTAS_POR_PAGINA) - 1)
        setPaginaLista(actual => Math.min(actual, ultimaPagina))
    }

    // HU39 - Actualiza silenciosamente la lista para mostrar mensajes entrantes recientes.
    useEffect(() => {
        if (!sesion?.token) return
        let vigente = true
        const intervalo = window.setInterval(() => {
            listarConversaciones().then(data => { if (vigente) actualizarLista(data) }).catch(() => {})
        }, 15000)
        return () => { vigente = false; window.clearInterval(intervalo) }
    }, [sesion?.token])

    useEffect(() => {
        setHistorialCargado(null)
        if (!seleccion) {
            setMensajes([])
            return
        }
        let vigente = true
        setMensajes([])
        setTieneSiguiente(false)
        setCargandoMensajes(true)
        setErrorMensajes('')
        listarMensajesConversacion(seleccion, 0).then(data => {
            if (!vigente) return
            setMensajes(data.mensajes.reverse())
            setPagina(data.pagina)
            setTieneSiguiente(data.tieneSiguiente)
            setHistorialCargado(seleccion)
        }).catch(err => {
            if (vigente) setErrorMensajes(err?.response?.data?.messages?.[0] || 'No se pudo cargar el historial.')
        }).finally(() => { if (vigente) setCargandoMensajes(false) })
        return () => { vigente = false }
    }, [seleccion, version, conversacion?.ultimoMensaje?.id])

    useEffect(() => {
        if (!avisosChat || historialCargado !== seleccion) return
        let vigente = true
        marcarMensajesLeidos(avisosChat.split(',')).then(ids => {
            if (vigente && ids.length) {
                setAvisosLeidos(actuales => new Set([...actuales, ...ids]))
            }
        })
        return () => { vigente = false }
    }, [avisosChat, historialCargado, seleccion])

    useLayoutEffect(() => {
        if (historial.current && pagina === 0 && historialCargado === seleccion) {
            historial.current.scrollTop = historial.current.scrollHeight
        }
    }, [mensajes, pagina, historialCargado, seleccion])

    async function cargarAnteriores() {
        if (cargandoAnteriores || !tieneSiguiente || !seleccion) return
        setCargandoAnteriores(true)
        setErrorMensajes('')
        try {
            const data = await listarMensajesConversacion(seleccion, pagina + 1)
            setMensajes(actuales => [...data.mensajes.reverse(), ...actuales])
            setPagina(data.pagina)
            setTieneSiguiente(data.tieneSiguiente)
        } catch (err) {
            setErrorMensajes(err?.response?.data?.messages?.[0] || 'No se pudieron cargar más mensajes.')
        } finally {
            setCargandoAnteriores(false)
        }
    }

    return <section className="tarjeta consultas">
        <h2>Consultas</h2>
        <button className="btn-secundario" onClick={() => setVersion(v => v + 1)} disabled={cargando}>Actualizar consultas</button>
        {cargando && <p role="status">Cargando consultas…</p>}
        {error && <p role="alert">{error} <button onClick={() => setVersion(v => v + 1)}>Reintentar</button></p>}
        {!cargando && !error && !lista.length && <p>Aún no tienes consultas.</p>}
        {!!lista.length && !error && <div className={conversacion ? 'consultas-panel consultas-panel-abierto' : 'consultas-panel'}>
            <div className="consultas-lista" aria-label="Lista de consultas">
                {consultasVisibles.map(c => {
                    const pendientes = pendientesPorChat.get(c.idConversacion)?.length ?? 0
                    return <div className="consulta-producto-fila" key={c.idConversacion}>
                        <button className="consulta-producto-seleccion" aria-pressed={seleccion === c.idConversacion} disabled={cargandoAnteriores}
                            onClick={() => setSeleccion(c.idConversacion)}>
                            {urlImagen(c.productoImagenUrl)
                                ? <img className="consulta-producto-imagen" src={urlImagen(c.productoImagenUrl)} alt={`Imagen de ${c.productoNombre}`} />
                                : <span className="consulta-producto-sin-imagen">Sin imagen</span>}
                            <span className="consulta-producto-resumen">
                                <strong>Consulta #{c.idConversacion} — {c.productoNombre}</strong>
                                <span>{c.ultimoMensaje?.contenido || 'Sin mensajes'}</span>
                                {pendientes > 0 && <span className="consulta-mensaje-nuevo">
                                    {pendientes === 1 ? 'Nuevo mensaje' : 'Nuevos mensajes'}
                                </span>}
                            </span>
                            {pendientes > 0 && <span className="consulta-contador"
                                aria-label={pendientes === 1 ? '1 mensaje sin leer' : `${pendientes} mensajes sin leer`}>
                                {pendientes}
                            </span>}
                        </button>
                        {c.productoDisponible === false && <span className="consulta-producto-estado">Producto ya no disponible</span>}
                    </div>
                })}
                {totalPaginas > 1 && <nav className="consultas-paginacion" aria-label="Páginas de consultas">
                    <button onClick={() => setPaginaLista(p => p - 1)} disabled={paginaLista === 0}>Anterior</button>
                    <span aria-live="polite">{paginaLista + 1} de {totalPaginas}</span>
                    <button onClick={() => setPaginaLista(p => p + 1)} disabled={paginaLista === totalPaginas - 1}>Siguiente</button>
                </nav>}
            </div>
            {conversacion ? <article className="consultas-chat" aria-label="Conversación">
                <button className="consultas-volver" onClick={() => setSeleccion(null)} disabled={cargandoAnteriores}>Volver a consultas</button>
                <EncabezadoProductoConsulta conversacion={conversacion} />
                <div className="consulta-historial" ref={historial} tabIndex={0} aria-label="Historial de mensajes">
                    {tieneSiguiente && <button className="btn-secundario" onClick={cargarAnteriores} disabled={cargandoAnteriores}>
                        {cargandoAnteriores ? 'Cargando mensajes…' : 'Cargar mensajes anteriores'}
                    </button>}
                    {cargandoMensajes && <p role="status">Cargando historial…</p>}
                    {errorMensajes && <p role="alert">{errorMensajes}</p>}
                    {mensajes.map(m => <div key={m.id} className="consulta-mensaje" data-propio={m.emisorId === sesion.id}>
                        <p>{m.contenido}</p>
                        <time dateTime={m.fecha}>{fechaHoraPedidoVisible(m.fecha)}</time>
                    </div>)}
                </div>
                <FormularioConsulta key={conversacion.idConversacion} enChat
                    enviar={texto => responderConsulta(conversacion.idConversacion, texto)}
                    onEnviado={() => setVersion(v => v + 1)} />
            </article> : <p className="consultas-sin-seleccion">Selecciona una consulta para ver la conversación.</p>}
        </div>}
    </section>
}

function EncabezadoProductoConsulta({ conversacion }) {
    const { productoId, productoNombre, productoImagenUrl, productoDisponible } = conversacion
    const imagen = urlImagen(productoImagenUrl)
    const contenido = <>
        {imagen
            ? <img className="consulta-producto-imagen" src={imagen} alt="" />
            : <span className="consulta-producto-sin-imagen">Sin imagen</span>}
        <h3>{productoNombre}</h3>
    </>

    if (productoDisponible === false) {
        return <div className="consulta-producto-encabezado">
            {contenido}
            <span className="consulta-producto-estado">Producto ya no disponible</span>
        </div>
    }

    return <div>
        <Link className="consulta-producto-encabezado pedido-enlace-producto"
            style={{ display: 'inline-flex' }} to={`/producto/${productoId}`}>
            {contenido}
        </Link>
    </div>
}
