import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenerCatalogo, urlImagen } from '../api/productos.js'

const CLASES_ESTADO = {
    DISPONIBLE: 'badge-disponible',
    AGOTADO: 'badge-agotado',
    INACTIVO: 'badge-inactivo',
    RETIRADO: 'badge-retirado',
}

// Catálogo público (HU 07 + HU 08): tarjetas clickeables hacia el detalle
// HU 19: el prop "actualizar" refresca el catálogo cuando cambia algo
// (publicar, editar o activar/desactivar el destacado en Mis productos).
export default function Catalogo({ actualizar }) {
    const [productos, setProductos] = useState([])
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        cargarCatalogo()
    }, [actualizar])

    async function cargarCatalogo() {
        try {
            setCargando(true)
            setError('')
            const data = await obtenerCatalogo()
            setProductos(data)
        } catch {
            setError('No se pudo cargar el catálogo.')
        } finally {
            setCargando(false)
        }
    }

    return (
        <section className="tarjeta catalogo">
            <h2>Catálogo</h2>
            <p className="catalogo-ayuda">
                Selecciona un producto para ver su detalle.
            </p>

            {cargando && <p>Cargando catálogo...</p>}

            {error && <div className="alerta error">{error}</div>}

            {!cargando && !error && productos.length === 0 && (
                <p>No hay productos disponibles por ahora.</p>
            )}

            <div className="catalogo-grilla">
                {productos.map((producto) => {
                    const imagen = urlImagen(producto.imagenUrl)

                    return (
                        <Link
                            key={producto.id}
                            to={`/producto/${producto.id}`}
                            className="catalogo-tarjeta"
                        >
                            {imagen ? (
                                <img
                                    className="catalogo-imagen"
                                    src={imagen}
                                    alt={producto.nombre}
                                />
                            ) : (
                                <div className="catalogo-imagen-vacia">
                                    Sin imagen
                                </div>
                            )}

                            <div className="catalogo-datos">
                                {/* HU 19 - distintivo de publicación destacada */}
                                {producto.esDestacado && (
                                    <span className="badge badge-destacado">
                                        Destacado
                                    </span>
                                )}
                                <h3>{producto.nombre}</h3>
                                <p className="catalogo-precio">
                                    S/ {Number(producto.precio).toFixed(2)}
                                </p>
                                <span
                                    className={`badge ${
                                        CLASES_ESTADO[producto.estado] || ''
                                    }`}
                                >
                                    {producto.estado}
                                </span>
                            </div>
                        </Link>
                    )
                })}
            </div>
        </section>
    )
}
