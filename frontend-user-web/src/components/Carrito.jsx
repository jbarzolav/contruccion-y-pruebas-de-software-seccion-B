import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
    actualizarCantidadItem,
    eliminarItemCarrito,
    obtenerCarrito,
    obtenerTotalCarrito
} from '../api/productos.js'

export default function Carrito({ onCarritoActualizado }) {
    const [items, setItems] = useState([])
    const [total, setTotal] = useState(0)
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [actualizandoId, setActualizandoId] = useState(null)

    useEffect(() => {
        cargarCarrito()
    }, [])

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

            setItems(lista =>
                lista.map(elemento =>
                    elemento.id === item.id ? actualizado : elemento
                )
            )

            await actualizarTotal()
            onCarritoActualizado?.()
        } catch (err) {
            setError(err?.response?.data?.messages?.[0] || 'No se pudo actualizar la cantidad o el total.')
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

            setItems(lista =>
                lista.filter(elemento => elemento.id !== item.id)
            )

            await actualizarTotal()
            onCarritoActualizado?.()
        } catch (err) {
            setError(err?.response?.data?.messages?.[0] || 'No se pudo eliminar el producto o actualizar el total.')
        } finally {
            setActualizandoId(null)
        }
    }

    if (cargando) {
        return <p>Cargando carrito...</p>
    }

    return (
        <section className="tarjeta carrito">
            <h2>Mi carrito</h2>

            {error && <div className="alerta error">{error}</div>}

            {items.length === 0 ? (
                <>
                    <p className="carrito-vacio">Tu carrito está vacío.</p>
                    <div className="carrito-total">
                        Total: <strong>S/ {total.toFixed(2)}</strong>
                    </div>
                </>
            ) : (
                <>
                    <ul className="carrito-lista">
                        {items.map(item => (
                            <li key={item.id} className="carrito-item">
                                <div className="carrito-item-datos">
                                    <strong>{item.nombreProducto}</strong>
                                    <span>
                                        Precio unitario: S/ {Number(item.precioUnitario).toFixed(2)}
                                    </span>
                                </div>

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
                            </li>
                        ))}
                    </ul>

                    <div className="carrito-total">
                        Total: <strong>S/ {total.toFixed(2)}</strong>
                    </div>
                </>
            )}

            <div className="botones">
                <Link to="/" className="btn-secundario">
                    Seguir comprando
                </Link>
            </div>
        </section>
    )
}
