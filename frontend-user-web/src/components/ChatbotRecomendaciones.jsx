import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { consultarChatbot, urlImagen } from '../api/productos.js'

/**
 * HU 16 - Chatbot de recomendaciones.
 * Área de mensajes + input de texto + botón Enviar.
 * El backend responde con un mensaje y tarjetas de productos
 * sugeridos (solo disponibles) que enlazan a su detalle.
 */
export default function ChatbotRecomendaciones() {
    const [mensajes, setMensajes] = useState([
        {
            rol: 'bot',
            texto: '¡Hola! 👋 Pregúntame por productos, por ejemplo: "notebook", "mouse" o "Computadoras".',
            productos: []
        }
    ])
    const [consulta, setConsulta] = useState('')
    const [enviando, setEnviando] = useState(false)
    const finMensajesRef = useRef(null)

    async function manejarEnvio(evento) {
        evento.preventDefault()

        const texto = consulta.trim()
        if (!texto || enviando) return

        try {
            setEnviando(true)

            const respuesta = await consultarChatbot(texto)

            setMensajes((lista) => [
                ...lista,
                { rol: 'usuario', texto, productos: [] },
                {
                    rol: 'bot',
                    texto: respuesta.mensaje,
                    productos: respuesta.productos || []
                }
            ])
            setConsulta('')
        } catch (err) {
            const mensajeError =
                err?.response?.data?.messages?.[0] || 'No se pudo consultar el chatbot.'
            setMensajes((lista) => [
                ...lista,
                { rol: 'usuario', texto, productos: [] },
                { rol: 'bot', texto: `⚠️ ${mensajeError}`, productos: [] }
            ])
            setConsulta('')
        } finally {
            setEnviando(false)
            finMensajesRef.current?.scrollIntoView({ behavior: 'smooth' })
        }
    }

    return (
        <section className="tarjeta chatbot">
            <h2>🤖 Chatbot de recomendaciones</h2>

            <div className="chatbot-mensajes">
                {mensajes.map((mensaje, indice) => (
                    <div
                        key={indice}
                        className={`chatbot-mensaje ${mensaje.rol === 'usuario' ? 'chatbot-usuario' : 'chatbot-bot'}`}
                    >
                        <p>{mensaje.texto}</p>

                        {mensaje.productos.length > 0 && (
                            <div className="chatbot-productos">
                                {mensaje.productos.map((producto) => (
                                    <Link
                                        key={producto.id}
                                        to={`/producto/${producto.id}`}
                                        className="chatbot-producto"
                                    >
                                        {producto.imagenUrl && (
                                            <img
                                                src={urlImagen(producto.imagenUrl)}
                                                alt={producto.nombre}
                                            />
                                        )}
                                        <strong>{producto.nombre}</strong>
                                        <span>S/ {Number(producto.precio).toFixed(2)}</span>
                                        <small>{producto.categoria}</small>
                                    </Link>
                                ))}
                            </div>
                        )}
                    </div>
                ))}
                <div ref={finMensajesRef} />
            </div>

            <form className="chatbot-formulario" onSubmit={manejarEnvio}>
                <input
                    type="text"
                    value={consulta}
                    onChange={(evento) => setConsulta(evento.target.value)}
                    placeholder="Escribe qué producto buscas..."
                    aria-label="Consulta para el chatbot"
                />
                <button type="submit" disabled={enviando || !consulta.trim()}>
                    {enviando ? 'Pensando...' : 'Enviar'}
                </button>
            </form>
        </section>
    )
}
