import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { obtenerProducto, urlImagen } from '../api/productos.js'

const CLASES_ESTADO = {
    DISPONIBLE: 'badge-disponible',
    AGOTADO: 'badge-agotado',
    INACTIVO: 'badge-inactivo',
    RETIRADO: 'badge-retirado',
}

// HU 07 - Visualizar el detalle de un producto
export default function DetalleProducto() {
    const { idProducto } = useParams()
    const navigate = useNavigate()

    const [producto, setProducto] = useState(null)
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        let vigente = true

        async function cargar() {
            try {
                setCargando(true)
                setError('')
                const data = await obtenerProducto(idProducto)
                if (vigente) setProducto(data)
            } catch (err) {
                if (!vigente) return
                // el backend responde {status, error, messages}
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
                <span className={`badge ${CLASES_ESTADO[producto.estado] || ''}`}>
                    {producto.estado}
                </span>
            </div>

            <div className="detalle-cuerpo">
                {/* Galería de imágenes (hoy: 1 imagen por producto, HU 02) */}
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
                            <strong>Stock:</strong> {producto.stock} unidades
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

                    {producto.estado === 'RETIRADO' && (
                        <div className="alerta error">
                            Este producto fue retirado del catálogo (baja lógica).
                        </div>
                    )}
                </div>
            </div>

            <div className="botones">
                <button
                    type="button"
                    className="btn-primario"
                    onClick={() => navigate('/')}
                >
                    Seguir navegando
                </button>
            </div>
        </section>
    )
}
