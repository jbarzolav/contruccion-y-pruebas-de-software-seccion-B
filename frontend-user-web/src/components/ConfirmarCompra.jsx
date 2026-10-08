import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
    confirmarCompra,
    obtenerCarrito,
    obtenerTotalCarrito
} from '../api/productos.js'
import { useEffect } from 'react'

/**
 * HU 15 - Confirmar mi compra (Checkout).
 * Resumen final del carrito (lista, subtotales y total) + botón
 * "Confirmar compra" que registra el pedido en el backend.
 */
export default function ConfirmarCompra({ onCarritoActualizado }) {
    const [items, setItems] = useState([])
    const [total, setTotal] = useState(0)
    const [cargando, setCargando] = useState(true)
    const [enviando, setEnviando] = useState(false)
    const [error, setError] = useState('')
    const [pedido, setPedido] = useState(null)
    const navigate = useNavigate()

    useEffect(() => {
        cargarResumen()
    }, [])

    async function cargarResumen() {
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
            setError(err?.response?.data?.messages?.[0] || 'No se pudo cargar el resumen del carrito.')
        } finally {
            setCargando(false)
        }
    }

    // HU 15 - POST /api/pedidos: convierte el carrito en un pedido registrado
    async function manejarConfirmacion() {
        if (enviando || items.length === 0) return

        const confirmado = window.confirm('¿Confirmar tu compra? El pedido quedará registrado.')
        if (!confirmado) return

        try {
            setEnviando(true)
            setError('')

            const resultado = await confirmarCompra()

            setPedido(resultado)
            setItems([])
            setTotal(0)
            onCarritoActualizado?.() // el badge del nav vuelve a 0
        } catch (err) {
            setError(err?.response?.data?.messages?.[0] || 'No se pudo registrar el pedido.')
        } finally {
            setEnviando(false)
        }
    }

    if (cargando) {
        return <p>Cargando resumen de la compra...</p>
    }

    // Éxito: comprobante con el ID del pedido generado
    if (pedido) {
        return (
            <section className="tarjeta pedido-exito">
                <h2>✅ Pedido registrado</h2>
                <p className="pedido-exito-id">
                    Tu pedido <strong>#{pedido.idPedido}</strong> fue registrado correctamente.
                </p>

                <ul className="carrito-lista">
                    {pedido.detalles.map((detalle) => (
                        <li key={detalle.idProducto} className="carrito-item">
                            <div className="carrito-item-datos">
                                <strong>{detalle.nombreProducto}</strong>
                                <span>
                                    {detalle.cantidad} × S/ {Number(detalle.precioUnitario).toFixed(2)}
                                </span>
                            </div>
                            <div className="carrito-subtotal">
                                Subtotal: <strong>S/ {Number(detalle.subtotal).toFixed(2)}</strong>
                            </div>
                        </li>
                    ))}
                </ul>

                <div className="carrito-total">
                    Total pagado: <strong>S/ {Number(pedido.total).toFixed(2)}</strong>
                </div>

                <div className="botones">
                    <button type="button" onClick={() => navigate('/')}>
                        Seguir comprando
                    </button>
                    <Link to="/carrito" className="btn-secundario">
                        Ver mi carrito
                    </Link>
                </div>
            </section>
        )
    }

    return (
        <section className="tarjeta">
            <h2>Confirmar compra</h2>

            {error && <div className="alerta error">{error}</div>}

            {items.length === 0 ? (
                <>
                    <p className="carrito-vacio">
                        Tu carrito está vacío: agrega productos antes de confirmar la compra.
                    </p>
                    <div className="botones">
                        <Link to="/" className="btn-secundario">
                            Ir al catálogo
                        </Link>
                    </div>
                </>
            ) : (
                <>
                    <ul className="carrito-lista">
                        {items.map((item) => (
                            <li key={item.id} className="carrito-item">
                                <div className="carrito-item-datos">
                                    <strong>{item.nombreProducto}</strong>
                                    <span>
                                        {item.cantidad} × S/ {Number(item.precioUnitario).toFixed(2)}
                                    </span>
                                </div>
                                <div className="carrito-subtotal">
                                    Subtotal: <strong>S/ {Number(item.subtotal).toFixed(2)}</strong>
                                </div>
                            </li>
                        ))}
                    </ul>

                    <div className="carrito-total">
                        Total a pagar: <strong>S/ {total.toFixed(2)}</strong>
                    </div>

                    <div className="botones">
                        <button
                            type="button"
                            onClick={manejarConfirmacion}
                            disabled={enviando}
                        >
                            {enviando ? 'Registrando pedido...' : 'Confirmar compra'}
                        </button>
                        <Link to="/carrito" className="btn-secundario">
                            Volver al carrito
                        </Link>
                    </div>
                </>
            )}
        </section>
    )
}
