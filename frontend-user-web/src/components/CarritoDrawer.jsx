import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    actualizarCantidadItem,
    eliminarItemCarrito,
    obtenerCarrito,
    obtenerTotalCarrito
} from '../api/productos.js'

/**
 * Rediseño UI/UX - Carrito de compras como panel lateral (Drawer).
 * HU 11 / HU 12 (cantidades) / HU 13 (eliminar) / HU 14 (total).
 * Se abre con el icono 🛒 del header sin salir de la página actual.
 */
export default function CarritoDrawer({ abierto, onCerrar, onCarritoActualizado }) {
    const [items, setItems] = useState([])
    const [total, setTotal] = useState(0)
    const [cargando, setCargando] = useState(false)
    const [error, setError] = useState('')
    const [actualizandoId, setActualizandoId] = useState(null)
    const navegar = useNavigate()

    // Recarga el carrito cada vez que se abre el panel
    useEffect(() => {
        if (abierto) {
            cargarCarrito()
        }
        return undefined
    }, [abierto])

    // La tecla Escape cierra el panel
    useEffect(() => {
        if (!abierto) return undefined

        function manejarTecla(evento) {
            if (evento.key === 'Escape') {
                onCerrar?.()
            }
        }

        window.addEventListener('keydown', manejarTecla)
        return () => window.removeEventListener('keydown', manejarTecla)
    }, [abierto, onCerrar])

    async function cargarCarrito() {
        try {
            setCargando(true)
            setError('')

            const [lista, resultadoTotal] = await Promise.all([
                obtenerCarrito(),
                obtenerTotalCarrito()
            ])

            setItems(lista)
            setTotal(Number(resultadoTotal.total))
        } catch (err) {
            setError(err?.response?.data?.messages?.[0] || 'No se pudo cargar el carrito.')
        } finally {
            setCargando(false)
        }
    }

    async function actualizarTotal() {
        const resultado = await obtenerTotalCarrito()
        setTotal(Number(resultado.total))
    }

    // HU 12 - Modificar cantidad
    async function cambiarCantidad(item, delta) {
        const nuevaCantidad = item.cantidad + delta
        if (nuevaCantidad < 1 || actualizandoId !== null) return

        try {
            setError('')
            setActualizandoId(item.id)

            const actualizado = await actualizarCantidadItem(item.id, nuevaCantidad)

            setItems((lista) =>
                lista.map((elemento) =>
                    elemento.id === item.id ? actualizado : elemento
                )
            )

            await actualizarTotal()
            onCarritoActualizado?.()
        } catch (err) {
            setError(err?.response?.data?.messages?.[0] || 'No se pudo actualizar la cantidad.')
        } finally {
            setActualizandoId(null)
        }
    }

    // HU 13 - Eliminar producto
    async function eliminarProducto(item) {
        if (actualizandoId !== null) return

        const confirmado = window.confirm(
            `¿Deseas eliminar "${item.nombreProducto}" del carrito?`
        )
        if (!confirmado) return

        try {
            setError('')
            setActualizandoId(item.id)

            await eliminarItemCarrito(item.id)

            setItems((lista) => lista.filter((elemento) => elemento.id !== item.id))

            await actualizarTotal()
            onCarritoActualizado?.()
        } catch (err) {
            setError(err?.response?.data?.messages?.[0] || 'No se pudo eliminar el producto.')
        } finally {
            setActualizandoId(null)
        }
    }

    if (!abierto) {
        return null
    }

    return (
        <>
            <div className="drawer-overlay" onClick={onCerrar} />

            <aside className="drawer-panel" aria-label="Carrito de compras">
                <div className="drawer-cabecera">
                    <h2>🛒 Mi carrito</h2>
                    <button
                        type="button"
                        className="modal-cerrar"
                        onClick={onCerrar}
                        aria-label="Cerrar el carrito"
                    >
                        ✕
                    </button>
                </div>

                <div className="drawer-cuerpo">
                    {cargando && <p>Cargando carrito...</p>}

                    {error && <div className="alerta error">{error}</div>}

                    {!cargando && items.length === 0 && (
                        <p className="carrito-vacio">Tu carrito está vacío.</p>
                    )}

                    <ul className="carrito-lista">
                        {items.map((item) => (
                            <li key={item.id} className="carrito-item">
                                <div className="carrito-item-datos">
                                    <strong>{item.nombreProducto}</strong>
                                    <span>
                                        S/ {Number(item.precioUnitario).toFixed(2)} c/u
                                    </span>
                                </div>

                                <div className="drawer-item-pie">
                                    <div className="carrito-controles">
                                        <button
                                            type="button"
                                            className="btn-cantidad"
                                            onClick={() => cambiarCantidad(item, -1)}
                                            disabled={item.cantidad <= 1 || actualizandoId !== null}
                                            aria-label={`Restar una unidad de ${item.nombreProducto}`}
                                        >
                                            &minus;
                                        </button>

                                        <span className="carrito-cantidad">
                                            {item.cantidad}
                                        </span>

                                        <button
                                            type="button"
                                            className="btn-cantidad"
                                            onClick={() => cambiarCantidad(item, 1)}
                                            disabled={actualizandoId !== null}
                                            aria-label={`Sumar una unidad de ${item.nombreProducto}`}
                                        >
                                            +
                                        </button>
                                    </div>

                                    <div className="carrito-subtotal">
                                        Subtotal: <strong>S/ {Number(item.subtotal).toFixed(2)}</strong>
                                    </div>

                                    <button
                                        type="button"
                                        className="btn-secundario"
                                        onClick={() => eliminarProducto(item)}
                                        disabled={actualizandoId !== null}
                                        aria-label={`Eliminar ${item.nombreProducto} del carrito`}
                                    >
                                        Eliminar
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                </div>

                <div className="drawer-pie">
                    <div className="carrito-total">
                        Total: <strong>S/ {total.toFixed(2)}</strong>
                    </div>

                    <button
                        type="button"
                        className="btn-secundario"
                        onClick={onCerrar}
                    >
                        Seguir comprando
                    </button>

                    {/* HU 15 - pasar al checkout cuando hay productos */}
                    {items.length > 0 && (
                        <button
                            type="button"
                            className="btn-primario"
                            onClick={() => {
                                onCerrar?.()
                                navegar('/confirmar-compra')
                            }}
                        >
                            Confirmar compra
                        </button>
                    )}
                </div>
            </aside>
        </>
    )
}
