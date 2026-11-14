import CampoTextoAjustable from './CampoTextoAjustable.jsx'
import { useEffect, useRef, useState } from 'react'
import { configuracionConsultas } from '../api/conversaciones.js'

export default function FormularioConsulta({ enviar, titulo = 'Consultar al vendedor', onEnviado, enChat = false }) {
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
            if (!enChat) setExito('Mensaje enviado correctamente.')
            onEnviado?.()
        } catch (err) {
            if (vigente.current) setError(err?.response?.data?.messages?.[0] || 'No se pudo enviar. Revisa la conexión e inténtalo nuevamente.')
        } finally { if (vigente.current) setCargando(false) }
    }
    return <form onSubmit={enviarMensaje} className={enChat ? "consulta-redactar" : "tarjeta consulta-ficha"}>
        {!enChat && <h3>{titulo}</h3>}
        {!maximo && <p>Cargando configuración… <button type="button" onClick={() => { setError(''); setIntento(i => i + 1) }}>Reintentar</button></p>}
        <div className="consulta-campo">{!enChat && <label htmlFor="consulta-mensaje">Mensaje</label>}
            <CampoTextoAjustable id={enChat ? undefined : "consulta-mensaje"} placeholder="Escribe un mensaje…" aria-label="Mensaje" value={mensaje} maxLength={maximo || undefined}
            disabled={cargando || !maximo} onChange={e => setMensaje(e.target.value)} required /></div>
        {maximo && !enChat && <p className="consulta-contador-caracteres">{mensaje.length}/{maximo} caracteres</p>}
        <button className={enChat ? "btn-primario consulta-enviar" : "btn-primario"}
            aria-label={cargando ? "Enviando mensaje" : "Enviar mensaje"} disabled={cargando || !maximo || !mensaje.trim()}>
            {enChat ? <span aria-hidden="true">➤</span> : cargando ? 'Enviando…' : 'Enviar mensaje'}
        </button>
        {exito && <p role="status" className="alerta exito">{exito}</p>}
        {error && <p role="alert" className="alerta error">{error}</p>}
    </form>
}
