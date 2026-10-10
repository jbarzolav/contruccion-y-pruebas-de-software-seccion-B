
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
    consultarChatbot,
    consultarChatbotTecnico,
    urlImagen
} from '../api/productos.js'

/**
 * HU 16 - Recomendaciones de productos.
 * HU 17 - Consultas técnicas sobre componentes electrónicos.
 */
export default function ChatbotRecomendaciones() {
    const [modo, setModo] = useState('recomendaciones')

    const [mensajes, setMensajes] = useState([
        {
            rol: 'bot',
            texto: '¡Hola! 👋 Puedo recomendarte productos y responder consultas técnicas sobre componentes electrónicos.',
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

        const modoConsulta = modo
        setEnviando(true)

        // Mostrar la pregunta inmediatamente.
        setMensajes((lista) => [
            ...lista,
            {
                rol: 'usuario',
                texto,
                productos: []
            }
        ])
        setConsulta('')

        try {
            const respuesta = modoConsulta === 'tecnico'
                ? await consultarChatbotTecnico(texto)
                : await consultarChatbot(texto)

            setMensajes((lista) => [
                ...lista,
                {
                    rol: 'bot',
                    texto: respuesta.mensaje,
                    productos: modoConsulta === 'tecnico'
                        ? []
                        : (respuesta.productos || [])
                }
            ])
        } catch (err) {
            const mensajeError =
                err?.response?.data?.messages?.[0] ||
                'No se pudo consultar el chatbot.'

            setMensajes((lista) => [
                ...lista,
                {
                    rol: 'bot',
                    texto: `⚠️ ${mensajeError}`,
                    productos: []
                }
            ])
        } finally {
            setEnviando(false)
            setTimeout(() => {
                finMensajesRef.current?.scrollIntoView({
                    behavior: 'smooth'
                })
            }, 0)
        }
    }

    return (
        <section className="tarjeta chatbot">
            <h2>🤖 Asistente PulgaTec</h2>

            <p>
                Busca productos disponibles o resuelve dudas
                sobre componentes electrónicos.
            </p>

            {/* HU 16 y HU 17 - Selección de funcionalidad */}
            <div className="chatbot-modos">
                <button
                    type="button"
                    className={modo === 'recomendaciones' ? 'activo' : ''}
                    onClick={() => setModo('recomendaciones')}
                    disabled={enviando}
                    aria-pressed={modo === 'recomendaciones'}
                >
                    🛒 Recomendaciones
                </button>

                <button
                    type="button"
                    className={modo === 'tecnico' ? 'activo' : ''}
                    onClick={() => setModo('tecnico')}
                    disabled={enviando}
                    aria-pressed={modo === 'tecnico'}
                >
                    🔧 Consultas técnicas
                </button>
            </div>

            <div className="chatbot-mensajes" aria-live="polite">
                {mensajes.map((mensaje, indice) => (
                    <div
                        key={indice}
                        className={`chatbot-mensaje ${
                            mensaje.rol === 'usuario'
                                ? 'chatbot-usuario'
                                : 'chatbot-bot'
                        }`}
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
                                        <span>
                                            S/ {Number(producto.precio).toFixed(2)}
                                        </span>
                                        <small>{producto.categoria}</small>
                                    </Link>
                                ))}
                            </div>
                        )}
                    </div>
                ))}

                {enviando && (
                    <div className="chatbot-mensaje chatbot-bot">
                        <p>🤖 Estoy preparando mi respuesta...</p>
                    </div>
                )}

                <div ref={finMensajesRef} />
            </div>

            <form
                className="chatbot-formulario"
                onSubmit={manejarEnvio}
            >
                <input
                    type="text"
                    value={consulta}
                    onChange={(evento) => setConsulta(evento.target.value)}
                    placeholder={
                        modo === 'tecnico'
                            ? 'Ejemplo: ¿Para qué sirve una protoboard?'
                            : 'Escribe qué producto buscas...'
                    }
                    aria-label="Consulta para el chatbot"
                />

                <button
                    type="submit"
                    disabled={enviando || !consulta.trim()}
                >
                    {enviando ? 'Pensando...' : 'Enviar'}
                </button>
            </form>
        </section>
    )
}
