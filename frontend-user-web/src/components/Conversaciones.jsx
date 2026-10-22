import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarConversaciones, responderConsulta } from '../api/conversaciones.js'
import { useSesion } from '../api/sesion.js'
import FormularioConsulta from './FormularioConsulta.jsx'

export default function Conversaciones() {
    const sesion = useSesion()
    const [lista, setLista] = useState([])
    const [seleccion, setSeleccion] = useState(null)
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
    return <section className="tarjeta">
        <h2>Consultas</h2>
        <button onClick={() => setVersion(v => v + 1)} disabled={cargando}>Actualizar consultas</button>
        {cargando && <p role="status">Cargando consultas…</p>}
        {error && <p role="alert">{error} <button onClick={() => setVersion(v => v + 1)}>Reintentar</button></p>}
        {!cargando && !error && !lista.length && <p>Aún no tienes consultas.</p>}
        {!error && lista.map(c => <p key={c.idConversacion}><button onClick={() => setSeleccion(c.idConversacion)}>
            Consulta #{c.idConversacion} — {c.productoNombre}</button></p>)}
        {conversacion && <article>
            <h3>{conversacion.productoNombre}</h3>
            <Link to={`/producto/${conversacion.productoId}`}>Ver producto</Link>
            {conversacion.mensajes.map(m => <div key={m.id} className="tarjeta">
                <strong>{m.emisorId === sesion.id ? 'Tú' : m.emisorRol}</strong> · <time>{new Date(m.fecha).toLocaleString()}</time>
                <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{m.contenido}</p>
            </div>)}
            {sesion.rol === 'VENDEDOR' && <FormularioConsulta key={conversacion.idConversacion}
                titulo="Responder consulta" enviar={texto => responderConsulta(conversacion.idConversacion, texto)}
                onEnviado={() => setVersion(v => v + 1)} />}
        </article>}
    </section>
}
