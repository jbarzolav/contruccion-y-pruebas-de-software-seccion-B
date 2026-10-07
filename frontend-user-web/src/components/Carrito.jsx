import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { actualizarCantidadItem, obtenerCarrito } from '../api/productos.js'

// HU 12 - Modificar la cantidad de un producto en el carrito
// Lista los ítems con controles [-] / [+] y subtotales recalculados por el servidor.
export default function Carrito({ onCarritoActualizado }) {
    const [items, setItems] = useState([])
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
            const data = await obtenerCarrito()
            setItems(data)
        } catch (err) {
            const mensajeBackend = err?.response?.data?.messages?.[0]
            setError(mensajeBackend || 'No se pudo cargar el carrito.')
        } finally {
            setCargando(false)
        }
    }

    // PUT /api/carrito/items/{id} → el backend devuelve el ítem con el subtotal ya recalculado
    async function cambiarCantidad(item, delta) {
        const nuevaCantidad = item.cantidad + delta

        if (nuevaCantidad < 1) return

        try {
            setError('')
            setActualizandoId(item.id)

            const actualizado = await actualizarCantidadItem(item.id, nuevaCantidad)

            setItems((lista) =>
                lista.map((elemento) =>
                    elemento.id === item.id ? actualizado : elemento
                )
            )
            onCarritoActualizado?.()
        } catch (err) {
            const mensajeBackend = err?.response?.data?.messages?.[0]
            setError(mensajeBackend || 'No se pudo actualizar la cantidad.')
        } finally {
            setActualizandoId(null)
        }
    }

    if (cargando) {
        return <p>Cargando carrito...</p>
    }

    const total = items.reduce((suma, item) => suma + Number(item.subtotal), 0)

    if (items.length === 0) {
        return (
            <section className="tarjeta carrito">
                <h2>Mi carrito</h2>
                <p className="carrito-vacio">Tu carrito está vacío.</p>
                <div className="botones">
                    <Link to="/" className="btn-primario">
                        Volver al catálogo
                    </Link>
                </div>
            </section>
        )
    }

    return (
        <section className="tarjeta carrito">
            <h2>Mi carrito</h2>

            {error && <div className="alerta error">{error}</div>}

            <ul className="carrito-lista">
                {items.map((item) => (
                    <li key={item.id} className="carrito-item">
                        <div className="carrito-item-datos">
                            <strong>{item.nombreProducto}</strong>
                            <span>
                                Precio unitario: S/{' '}
                                {Number(item.precioUnitario).toFixed(2)}
                            </span>
                        </div>

                        <div className="carrito-controles">
                            <button
                                type="button"
                                className="btn-cantidad"
                                onClick={() => cambiarCantidad(item, -1)}
                                disabled={
                                    item.cantidad <= 1 ||
                                    actualizandoId === item.id
                                }
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
                                disabled={actualizandoId === item.id}
                                aria-label={`Sumar una unidad de ${item.nombreProducto}`}
                            >
                                +
                            </button>
                        </div>

                        <div className="carrito-subtotal">
                            Subtotal:{' '}
                            <strong>S/ {Number(item.subtotal).toFixed(2)}</strong>
                        </div>
                    </li>
                ))}
            </ul>

            <div className="carrito-total">
                Total: <strong>S/ {total.toFixed(2)}</strong>
            </div>

            <div className="botones">
                <Link to="/" className="btn-secundario">
                    Seguir comprando
                </Link>
            </div>
        </section>
    )
}
