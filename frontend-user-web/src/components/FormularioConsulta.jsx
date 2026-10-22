import { useEffect, useRef, useState } from 'react'
import { configuracionConsultas } from '../api/conversaciones.js'

export default function FormularioConsulta({ enviar, titulo = 'Consultar al vendedor', onEnviado }) {
    const [mensaje, setMensaje] = useState('')
    const [maximo, setMaximo] = useState(null)
    const [cargando, setCargando] = useState(false)
    const [error, setError] = useState('')
    const [exito, setExito] = useState('')
    const [intento, setIntento] = useState(0)
    const vigente = useRef(false)
    useEffect(() => {
        vigente.current = true
        let activo = true
        configuracionConsultas().then(c => { if (activo) setMaximo(c.longitudMaxima) })
            .catch(() => { if (activo) setError('No se pudo cargar el límite del mensaje.') })
        return () => { activo = false; vigente.current = false }
    }, [intento])
    async function enviarMensaje(evento) {
        evento.preventDefault()
        if (cargando || !maximo) return
        const texto = mensaje.trim()
        setExito('')
        if (!texto || texto.length > maximo) { setError(`Escribe entre 1 y ${maximo} caracteres.`); return }
        setCargando(true)
        setError('')
        try {
            await enviar(texto)
            if (!vigente.current) return
            setMensaje('')
            setExito('Mensaje enviado correctamente.')
            onEnviado?.()
        } catch (err) {
            if (vigente.current) setError(err?.response?.data?.messages?.[0] || 'No se pudo enviar. Revisa la conexión e inténtalo nuevamente.')
        } finally { if (vigente.current) setCargando(false) }
    }
    return <form onSubmit={enviarMensaje} className="tarjeta">
        <h3>{titulo}</h3>
        {!maximo && <p>Cargando configuración… <button type="button" onClick={() => { setError(''); setIntento(i => i + 1) }}>Reintentar</button></p>}
        <label>Mensaje<textarea aria-label="Mensaje" value={mensaje} maxLength={maximo || undefined}
            disabled={cargando || !maximo} onChange={e => setMensaje(e.target.value)} required /></label>
        {maximo && <p>{mensaje.length}/{maximo} caracteres</p>}
        <button className="btn-primario" disabled={cargando || !maximo || !mensaje.trim()}>{cargando ? 'Enviando…' : 'Enviar mensaje'}</button>
        {exito && <p role="status" className="alerta exito">{exito}</p>}
        {error && <p role="alert" className="alerta error">{error}</p>}
    </form>
}
