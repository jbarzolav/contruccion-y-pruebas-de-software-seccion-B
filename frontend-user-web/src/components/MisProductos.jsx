import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenerMisProductos, gestionarDestacado } from '../api/productos.js'

export default function MisProductos({ actualizar, onEditar, onActualizado }) {
    const [productos, setProductos] = useState([])
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [aviso, setAviso] = useState('')

    useEffect(() => {
        cargarProductos()
    }, [actualizar])

    async function cargarProductos() {
        try {
            setCargando(true)
            setError('')
            const data = await obtenerMisProductos(1)
            setProductos(data)
        } catch {
            setError('No se pudieron cargar los productos.')
        } finally {
            setCargando(false)
        }
    }

    // HU 19 - Activar o desactivar el destacado (plan Premium activo).
    async function alternarDestacado(producto, activar) {
        try {
            setAviso('')
            await gestionarDestacado(producto.id, 1, activar)
            // Avisa al padre para refrescar "Mis productos" y el Catálogo.
            if (onActualizado) {
                onActualizado()
            } else {
                await cargarProductos()
            }
        } catch (e) {
            const mensaje = e?.response?.data?.messages?.[0]
            setAviso(mensaje || 'No se pudo actualizar el destacado.')
        }
    }

    return (
        <section className="tarjeta mis-productos">
            <h2>Mis Publicaciones</h2>

            {cargando && <p>Cargando productos...</p>}

            {error && <div className="alerta error">{error}</div>}

            {/* HU 19 - aviso cuando el vendedor no tiene el beneficio Premium */}
            {aviso && <div className="alerta error">{aviso}</div>}

            {!cargando && !error && productos.length === 0 && (
                <p>Todavía no tienes productos publicados.</p>
            )}

            {!cargando && !error && productos.map((producto) => (
                <article className="producto-item" key={producto.id}>
                    {/* HU 08 - toda la tarjeta abre el detalle (HU 07) */}
                    <Link
                        to={`/producto/${producto.id}`}
                        className="producto-item-enlace"
                    >
                        {producto.imagenUrl && (
                            <img
                                className="producto-imagen"
                                src={`http://localhost:8080${producto.imagenUrl}`}
                                alt={producto.nombre}
                            />
                        )}

                        <div className="producto-info">
                            <h3>{producto.nombre}</h3>
                            {/* HU 19 - distintivo de publicación destacada */}
                            {producto.esDestacado && (
                                <span className="badge badge-destacado">
                                    Destacado
                                </span>
                            )}
                            <p>Precio: S/ {Number(producto.precio).toFixed(2)}</p>
                            <p>Stock: {producto.stock}</p>
                            <p>Estado: {producto.estado}</p>
                        </div>
                    </Link>

                    <div className="producto-acciones">
                        <button
                            type="button"
                            className="btn-secundario"
                            onClick={() => onEditar?.(producto)}
                        >
                            Editar
                        </button>
                        {/* HU 19 - gestionar el destacado: activar o desactivar */}
                        <button
                            type="button"
                            className="btn-secundario"
                            onClick={() =>
                                alternarDestacado(producto, !producto.esDestacado)
                            }
                        >
                            {producto.esDestacado
                                ? 'Quitar destacado'
                                : 'Destacar'}
                        </button>
                        <Link
                            to={`/producto/${producto.id}`}
                            className="btn-secundario"
                        >
                            Ver detalle
                        </Link>
                    </div>
                </article>
            ))}
        </section>
    )
}