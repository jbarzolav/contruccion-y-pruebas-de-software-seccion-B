/**
 * Rediseño UI/UX - Modal flotante reutilizable.
 * Envuelve los formularios de Publicar (HU 01/02) y Editar (HU 03)
 * para no navegar a una página separada.
 */
export default function ProductoModal({ titulo, onCerrar, children }) {
    return (
        <div
            className="modal-overlay modal-formulario"
            onClick={onCerrar}
            role="presentation"
        >
            <div
                className="modal"
                role="dialog"
                aria-modal="true"
                aria-label={titulo}
                onClick={(evento) => evento.stopPropagation()}
            >
                <div className="modal-cabecera">
                    <h3>{titulo}</h3>
                    <button
                        type="button"
                        className="modal-cerrar"
                        onClick={onCerrar}
                        aria-label="Cerrar el formulario"
                    >
                        ✕
                    </button>
                </div>

                <div className="modal-cuerpo">{children}</div>
            </div>
        </div>
    )
}
