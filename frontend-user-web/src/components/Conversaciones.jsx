import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarConversaciones, listarMensajesConversacion, responderConsulta } from '../api/conversaciones.js'
import { useSesion } from '../api/sesion.js'
import FormularioConsulta from './FormularioConsulta.jsx'

/** HU38 - Lista conversaciones propias y permite consultar el historial paginado. */
export default function Conversaciones() {
    const sesion = useSesion()
    const [lista, setLista] = useState([])
    const [seleccion, setSeleccion] = useState(null)
    const [mensajes, setMensajes] = useState([])
    const [pagina, setPagina] = useState(0)
    const [tieneSiguiente, setTieneSiguiente] = useState(false)
    const [cargandoMensajes, setCargandoMensajes] = useState(false)
    const [cargandoAnteriores, setCargandoAnteriores] = useState(false)
    const [errorMensajes, setErrorMensajes] = useState('')
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [version, setVersion] = useState(0)
    useEffect(() => {
        let vigente = true
        setCargando(true)
        setError('')
        listarConversaciones().then(data => { if (vigente) setLista(data) })
            .catch(err => { if (vigente) setError(err?.response?.data?.messages?.[0] || 'No se pudieron cargar las consultas.') })
            .finally(() => { if (vigente) setCargando(false) })
        return () => { vigente = false }
    }, [version, sesion?.token])
    const conversacion = lista.find(c => c.idConversacion === seleccion)

    // HU39 - Actualiza silenciosamente la lista para mostrar mensajes entrantes recientes.
    useEffect(() => {
        if (!sesion?.token) return
        let vigente = true
        const intervalo = window.setInterval(() => {
            listarConversaciones().then(data => { if (vigente) setLista(data) }).catch(() => {})
        }, 15000)
        return () => { vigente = false; window.clearInterval(intervalo) }
    }, [sesion?.token])

    useEffect(() => {
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
        }).catch(err => {
            if (vigente) setErrorMensajes(err?.response?.data?.messages?.[0] || 'No se pudo cargar el historial.')
        }).finally(() => { if (vigente) setCargandoMensajes(false) })
        return () => { vigente = false }
    }, [seleccion, version, conversacion?.ultimoMensaje?.id])

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

    return <section className="tarjeta">
        <h2>Consultas</h2>
        <button onClick={() => setVersion(v => v + 1)} disabled={cargando}>Actualizar consultas</button>
        {cargando && <p role="status">Cargando consultas…</p>}
        {error && <p role="alert">{error} <button onClick={() => setVersion(v => v + 1)}>Reintentar</button></p>}
        {!cargando && !error && !lista.length && <p>Aún no tienes consultas.</p>}
        {!error && lista.map(c => <p key={c.idConversacion}><button onClick={() => setSeleccion(c.idConversacion)}>
            Consulta #{c.idConversacion} — {c.productoNombre}: {c.ultimoMensaje?.contenido || 'Sin mensajes'}
            {c.ultimoMensaje && c.ultimoMensaje.emisorId !== sesion.id && c.idConversacion !== seleccion &&
                <strong aria-label="Mensaje nuevo"> · Nuevo mensaje</strong>}
        </button></p>)}
        {conversacion && <article>
            <h3>{conversacion.productoNombre}</h3>
            <Link to={`/producto/${conversacion.productoId}`}>Ver producto</Link>
            {tieneSiguiente && <button onClick={cargarAnteriores} disabled={cargandoAnteriores}>
                {cargandoAnteriores ? 'Cargando mensajes…' : 'Cargar mensajes anteriores'}
            </button>}
            {cargandoMensajes && <p role="status">Cargando historial…</p>}
            {errorMensajes && <p role="alert">{errorMensajes}</p>}
            {mensajes.map(m => <div key={m.id} className="tarjeta">
                <strong>{m.emisorId === sesion.id ? 'Tú' : m.emisorRol}</strong> · <time>{new Date(m.fecha).toLocaleString()}</time>
                <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{m.contenido}</p>
            </div>)}
            {sesion.rol === 'VENDEDOR' && <FormularioConsulta key={conversacion.idConversacion}
                titulo="Responder consulta" enviar={texto => responderConsulta(conversacion.idConversacion, texto)}
                onEnviado={() => setVersion(v => v + 1)} />}
        </article>}
    </section>
}
