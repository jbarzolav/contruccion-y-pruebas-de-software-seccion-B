import { useState } from 'react'
import ChatbotRecomendaciones from './ChatbotRecomendaciones.jsx'

/**
 * Rediseño UI/UX - Chatbot IA (HU 16 / HU 17) como widget flotante.
 * Botón FAB en la esquina inferior derecha que abre/cierra la ventana
 * de chat sin ocupar una página del sitio.
 */
export default function ChatbotFab() {
    const [abierto, setAbierto] = useState(false)

    return (
        <>
            <button
                type="button"
                className="fab-chatbot"
                onClick={() => setAbierto((valor) => !valor)}
                aria-label={abierto ? 'Cerrar el asistente' : 'Abrir el asistente'}
                aria-expanded={abierto}
            >
                {abierto ? '✕' : '🤖'}
            </button>

            {abierto && (
                <div className="chatbot-panel" role="dialog" aria-label="Asistente PulgaTec">
                    <div className="chatbot-panel-cabecera">
                        <strong>🤖 Asistente PulgaTec</strong>
                        <button
                            type="button"
                            className="modal-cerrar chatbot-cerrar"
                            onClick={() => setAbierto(false)}
                            aria-label="Cerrar el asistente"
                        >
                            ✕
                        </button>
                    </div>

                    <div className="chatbot-panel-cuerpo">
                        <ChatbotRecomendaciones />
                    </div>
                </div>
            )}
        </>
    )
}
