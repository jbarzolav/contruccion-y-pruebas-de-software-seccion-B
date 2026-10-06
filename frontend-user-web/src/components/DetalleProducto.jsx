import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
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

// HU 07 / HU 10 - Visualizar detalle y disponibilidad de un producto
export default function DetalleProducto() {
    const { idProducto } = useParams()
    const navigate = useNavigate()

    const [producto, setProducto] = useState(null)
    const [disponibilidad, setDisponibilidad] = useState(null)
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')

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

    const imagen = urlImagen(producto.imagenUrl)
    const disponible = disponibilidad?.disponible === true

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
                        disponible ? 'badge-disponible' : 'badge-agotado'
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
                        <div className="galeria-vacia">Sin imagen disponible</div>
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
                            <strong>Stock:</strong>{' '}
                            {disponibilidad?.stock ?? producto.stock} unidades
                        </li>
                        <li>
                            <strong>Estado:</strong>{' '}
                            {disponibilidad?.estado ?? producto.estado}
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
                    <p className="detalle-descripcion">{producto.descripcion}</p>

                    {!disponible && (
                        <div className="alerta error">
                            Este producto no se encuentra disponible para compra.
                        </div>
                    )}
                </div>
            </div>

            <div className="botones">
                <button
                    type="button"
                    className="btn-primario"
                    disabled={!disponible}
                    title={
                        disponible
                            ? 'Producto disponible para compra'
                            : 'Producto agotado o no disponible'
                    }
                >
                    Comprar
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
