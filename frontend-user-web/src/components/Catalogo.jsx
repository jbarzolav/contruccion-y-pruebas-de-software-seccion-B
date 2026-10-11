import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
    buscarProductosPorNombre,
    obtenerCatalogo,
    urlImagen
} from '../api/productos.js'

const CLASES_ESTADO = {
    DISPONIBLE: 'badge-disponible',
    AGOTADO: 'badge-agotado',
    INACTIVO: 'badge-inactivo',
    RETIRADO: 'badge-retirado',
}

// HU 07 + HU 08 - Catálogo público con tarjetas clickeables hacia el detalle.
// Rediseño UI/UX: recibe la búsqueda (HU 06) y el orden (HU 09) desde Inicio
// y muestra insignias visuales de Destacado (HU 19) y Agotado.
export default function Catalogo({ nombre = '', sort = '', actualizar }) {
    const [productos, setProductos] = useState([])
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [mensaje, setMensaje] = useState('')

    // Recarga con debounce al escribir, al cambiar el orden o al modificar productos
    useEffect(() => {
        const timer = setTimeout(() => cargarCatalogo(), 300)
        return () => clearTimeout(timer)
    }, [nombre, sort, actualizar])

    async function cargarCatalogo() {
        try {
            setCargando(true)
            setError('')
            setMensaje('')

            const criterio = nombre.trim()
            let data

            if (criterio) {
                // HU 06 + HU 09 - búsqueda por nombre con orden del backend
                data = await buscarProductosPorNombre(criterio, sort)

                if (data.length === 0) {
                    setMensaje('No se encontraron productos disponibles.')
                }
            } else {
                // Sin texto: catálogo completo (destacados primero) + orden local
                data = ordenarLocal(await obtenerCatalogo(), sort)
            }

            setProductos(data)
        } catch {
            setError('No se pudo cargar el catálogo.')
        } finally {
            setCargando(false)
        }
    }

    // HU 09 - criterios de ordenamiento aplicados al catálogo completo
    function ordenarLocal(lista, criterio) {
        const copia = [...lista]

        switch (criterio) {
            case 'precio_asc':
                return copia.sort((a, b) => Number(a.precio) - Number(b.precio))
            case 'precio_desc':
                return copia.sort((a, b) => Number(b.precio) - Number(a.precio))
            case 'nombre_asc':
                return copia.sort((a, b) => a.nombre.localeCompare(b.nombre))
            case 'nombre_desc':
                return copia.sort((a, b) => b.nombre.localeCompare(a.nombre))
            default:
                return copia
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

            {mensaje && <p className="catalogo-mensaje">{mensaje}</p>}

            {!cargando && !error && !mensaje && productos.length === 0 && (
                <p>No hay productos disponibles por ahora.</p>
            )}

            <div className="catalogo-grilla">
                {productos.map((producto) => {
                    const imagen = urlImagen(producto.imagenUrl)
                    const agotado = producto.stock === 0

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
                                {/* Insignias visuales: Destacado (HU 19) y estado */}
                                <div className="catalogo-insignias">
                                    {producto.esDestacado && (
                                        <span className="badge badge-destacado">
                                            ★ Destacado
                                        </span>
                                    )}

                                    {agotado && producto.estado === 'DISPONIBLE' ? (
                                        <span className="badge badge-agotado">
                                            Agotado
                                        </span>
                                    ) : (
                                        <span
                                            className={`badge ${
                                                CLASES_ESTADO[producto.estado] || ''
                                            }`}
                                        >
                                            {producto.estado}
                                        </span>
                                    )}
                                </div>

                                <h3>{producto.nombre}</h3>
                                <p className="catalogo-precio">
                                    S/ {Number(producto.precio).toFixed(2)}
                                </p>
                            </div>
                        </Link>
                    )
                })}
            </div>
        </section>
    )
}
