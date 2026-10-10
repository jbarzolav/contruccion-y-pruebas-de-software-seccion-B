
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
    agregarAlCarrito,
    obtenerDisponibilidadProducto,
    obtenerProducto,
    urlImagen,
} from '../api/productos.js'

const CLASES_ESTADO = {
    DISPONIBLE: 'badge-disponible',
    AGOTADO: 'badge-agotado',
    INACTIVO: 'badge-inactivo',
    RETIRADO: 'badge-retirado',
}

// HU 07 / HU 08 - Visualizar detalle del producto
// HU 10 - Mostrar disponibilidad y bloquear compra sin stock
// HU 11 / HU 12 - Seleccionar cantidad y agregar al carrito
// HU 15 - Comprar y navegar a la confirmación de compra
export default function DetalleProducto({ onCarritoActualizado }) {
    const { idProducto } = useParams()
    const navigate = useNavigate()

    const [producto, setProducto] = useState(null)
    const [disponibilidad, setDisponibilidad] = useState(null)
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')

    // HU 11 - Estado visual del carrito
    const [cantidad, setCantidad] = useState(1)
    const [agregando, setAgregando] = useState(false)
    const [mensajeCarrito, setMensajeCarrito] = useState('')
    const [errorCarrito, setErrorCarrito] = useState('')

    useEffect(() => {
        let vigente = true

        async function cargar() {
            try {
                setCargando(true)
                setError('')

                const [productoData, disponibilidadData] = await Promise.all([
                    obtenerProducto(idProducto),
                    obtenerDisponibilidadProducto(idProducto),
                ])

                if (vigente) {
                    setProducto(productoData)
                    setDisponibilidad(disponibilidadData)
                }
            } catch (err) {
                if (!vigente) return

                const mensajeBackend = err?.response?.data?.messages?.[0]
                setError(mensajeBackend || 'No se pudo cargar el producto.')
            } finally {
                if (vigente) setCargando(false)
            }
        }

        cargar()

        return () => {
            vigente = false
        }
    }, [idProducto])

    // HU 11 / HU 15 - Agregar al carrito y permitir compra directa
    async function handleAgregarAlCarrito(comprarAhora = false) {
        if (agregando || !producto || !disponibilidad?.disponible) {
            return
        }

        try {
            setAgregando(true)
            setMensajeCarrito('')
            setErrorCarrito('')

            await agregarAlCarrito(producto.id, cantidad)

            setMensajeCarrito(
                `Se agregó ${cantidad} unidad${cantidad > 1 ? 'es' : ''} de ${producto.nombre} al carrito.`
            )

            onCarritoActualizado?.()

            // HU 15 - Ir a la confirmación solo si se agregó correctamente
            if (comprarAhora) {
                navigate('/confirmar-compra')
            }
        } catch (err) {
            const mensajeBackend = err?.response?.data?.messages?.[0]
            setErrorCarrito(
                mensajeBackend || 'No se pudo agregar el producto al carrito.'
            )
        } finally {
            setAgregando(false)
        }
    }

    if (cargando) {
        return <p>Cargando producto...</p>
    }

    if (error) {
        return (
            <section className="tarjeta detalle-producto">
                <div className="alerta error">{error}</div>
                <div className="botones">
                    <button
                        type="button"
                        className="btn-secundario"
                        onClick={() => navigate('/')}
                    >
                        Volver al inicio
                    </button>
                </div>
            </section>
        )
    }

    if (!producto) {
        return (
            <section className="tarjeta detalle-producto">
                <div className="alerta error">
                    No se encontró información del producto.
                </div>
            </section>
        )
    }

    const imagen = urlImagen(producto.imagenUrl)
    const disponible = disponibilidad?.disponible === true
    const stock = disponibilidad?.stock ?? producto.stock
    const estado = disponibilidad?.estado ?? producto.estado

    return (
        <section className="tarjeta detalle-producto">
            <div className="detalle-cabecera">
                <button
                    type="button"
                    className="btn-secundario"
                    onClick={() => navigate(-1)}
                >
                    &larr; Volver
                </button>

                <span
                    className={`badge ${
                        CLASES_ESTADO[estado] ??
                        (disponible ? 'badge-disponible' : 'badge-agotado')
                    }`}
                >
                    {disponible ? 'Disponible' : 'Agotado'}
                </span>
            </div>

            <div className="detalle-cuerpo">
                <div className="galeria">
                    {imagen ? (
                        <img
                            className="galeria-principal"
                            src={imagen}
                            alt={producto.nombre}
                        />
                    ) : (
                        <div className="galeria-vacia">
                            Sin imagen disponible
                        </div>
                    )}
                    <p className="galeria-pie">Imagen 1 de 1</p>
                </div>

                <div className="detalle-datos">
                    <p className="detalle-categoria">{producto.categoria}</p>
                    <h2>{producto.nombre}</h2>

                    <p className="detalle-precio">
                        S/ {Number(producto.precio).toFixed(2)}
                    </p>

                    <ul className="detalle-ficha">
                        <li>
                            <strong>Disponibilidad:</strong>{' '}
                            {disponible ? 'Disponible' : 'Agotado'}
                        </li>
                        <li>
                            <strong>Stock:</strong> {stock} unidades
                        </li>
                        <li>
                            <strong>Estado:</strong> {estado}
                        </li>
                        <li>
                            <strong>Categoría:</strong> {producto.categoria}
                        </li>
                        <li>
                            <strong>Vendedor:</strong> #{producto.vendedorId}
                        </li>
                        <li>
                            <strong>ID del producto:</strong> #{producto.id}
                        </li>
                    </ul>

                    <h3>Descripción</h3>
                    <p className="detalle-descripcion">
                        {producto.descripcion}
                    </p>

                    {!disponible && (
                        <div className="alerta error">
                            Este producto no se encuentra disponible para compra.
                        </div>
                    )}
                </div>
            </div>

            {/* HU 11 - Selector de cantidad y agregar al carrito */}
            <div className="detalle-carrito">
                <div className="cantidad-selector">
                    <span>Cantidad:</span>

                    <button
                        type="button"
                        className="btn-cantidad"
                        onClick={() =>
                            setCantidad((valor) => Math.max(1, valor - 1))
                        }
                        disabled={agregando || cantidad <= 1}
                        aria-label="Restar una unidad"
                    >
                        &minus;
                    </button>

                    <input
                        type="number"
                        min="1"
                        max={stock}
                        value={cantidad}
                        onChange={(evento) => {
                            const valor = Number(evento.target.value)
                            setCantidad(
                                Number.isNaN(valor)
                                    ? 1
                                    : Math.max(1, Math.trunc(valor))
                            )
                        }}
                        disabled={!disponible || agregando}
                        aria-label="Cantidad a agregar"
                    />

                    <button
                        type="button"
                        className="btn-cantidad"
                        onClick={() =>
                            setCantidad((valor) => valor + 1)
                        }
                        disabled={agregando || cantidad >= stock}
                        aria-label="Sumar una unidad"
                    >
                        +
                    </button>

                    <span className="cantidad-stock">
                        Stock: {stock}
                    </span>
                </div>

                <button
                    type="button"
                    className="btn-primario"
                    onClick={() => handleAgregarAlCarrito(false)}
                    disabled={
                        !disponible ||
                        agregando ||
                        cantidad > stock
                    }
                    title={
                        disponible
                            ? 'Agregar esta cantidad al carrito'
                            : 'Producto agotado o no disponible'
                    }
                >
                    {agregando ? 'Agregando...' : 'Agregar al carrito'}
                </button>

                {mensajeCarrito && (
                    <div className="alerta exito">{mensajeCarrito}</div>
                )}

                {errorCarrito && (
                    <div className="alerta error">{errorCarrito}</div>
                )}
            </div>

            <div className="botones">
                {/* HU 15 - Comprar y abrir confirmación de compra */}
                <button
                    type="button"
                    className="btn-primario"
                    onClick={() => handleAgregarAlCarrito(true)}
                    disabled={
                        !disponible ||
                        agregando ||
                        cantidad > stock
                    }
                    title={
                        disponible
                            ? 'Agregar al carrito y confirmar compra'
                            : 'Producto agotado o no disponible'
                    }
                >
                    {agregando ? 'Procesando...' : 'Comprar'}
                </button>

                <button
                    type="button"
                    className="btn-secundario"
                    onClick={() => navigate('/')}
                >
                    Seguir navegando
                </button>
            </div>
        </section>
    )
}
