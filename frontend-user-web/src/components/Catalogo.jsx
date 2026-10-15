import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
    buscarProductosPorNombre,
    obtenerCatalogo,
    obtenerRecomendaciones,
    urlImagen
} from '../api/productos.js'

const CLASES_ESTADO = {
    DISPONIBLE: 'badge-disponible',
    AGOTADO: 'badge-agotado',
    INACTIVO: 'badge-inactivo',
    RETIRADO: 'badge-retirado',
}

// HU 07 + HU 08 + HU 20 - Tarjeta única del catálogo: la usan tanto los
// resultados de búsqueda como las recomendaciones (mismos estilos y la
// misma navegación hacia /producto/:id, sin duplicar componentes).
function tarjetaProducto(producto) {
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
}

// HU 07 + HU 08 - Catálogo público con tarjetas clickeables hacia el detalle.
// Rediseño UI/UX: recibe la búsqueda (HU 06) y el orden (HU 09) desde Inicio
// y muestra insignias visuales de Destacado (HU 19) y Agotado.
export default function Catalogo({ nombre = '', sort = '', actualizar }) {
    const [productos, setProductos] = useState([])
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [mensaje, setMensaje] = useState('')

    // HU 20 - recomendaciones relacionadas con el término buscado
    const [recomendaciones, setRecomendaciones] = useState([])
    const [cargandoRecomendaciones, setCargandoRecomendaciones] = useState(false)
    const [errorRecomendaciones, setErrorRecomendaciones] = useState('')
    // Último término realmente solicitado: descarta respuestas desactualizadas
    const ultimoTermino = useRef('')

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
                // HU 20 - recomendaciones en paralelo (sin await): la sección
                // muestra su propio estado de carga de forma independiente
                cargarRecomendaciones(criterio)

                // HU 06 + HU 09 - búsqueda por nombre con orden del backend
                data = await buscarProductosPorNombre(criterio, sort)

                if (data.length === 0) {
                    setMensaje('No se encontraron productos disponibles.')
                }
            } else {
                // Sin término no hay recomendaciones que mostrar (HU 20)
                limpiarRecomendaciones()

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

    // HU 20 - GET /api/recomendaciones?termino={texto}
    // Estados: cargando / resultados / vacío / error (en la sección inferior).
    async function cargarRecomendaciones(criterio) {
        ultimoTermino.current = criterio
        setCargandoRecomendaciones(true)
        setErrorRecomendaciones('')

        try {
            const datos = await obtenerRecomendaciones(criterio)

            // Si el usuario ya buscó otra cosa, esta respuesta ya no sirve
            if (ultimoTermino.current !== criterio) return
            setRecomendaciones(datos)
        } catch {
            if (ultimoTermino.current !== criterio) return
            setRecomendaciones([])
            setErrorRecomendaciones('No se pudieron cargar las recomendaciones.')
        } finally {
            if (ultimoTermino.current === criterio) {
                setCargandoRecomendaciones(false)
            }
        }
    }

    function limpiarRecomendaciones() {
        ultimoTermino.current = ''
        setRecomendaciones([])
        setCargandoRecomendaciones(false)
        setErrorRecomendaciones('')
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

    // HU 20 - derivados para la sección de recomendaciones
    const termino = nombre.trim()
    // Evita duplicados entre los resultados de búsqueda y las recomendaciones
    const idsEnResultados = new Set(productos.map((producto) => producto.id))
    const recomendacionesVisibles = recomendaciones.filter(
        (producto) => !idsEnResultados.has(producto.id)
    )
    // Cargando real o término tecleado aún no solicitado (debounce)
    const recomendacionesPendientes =
        cargandoRecomendaciones || ultimoTermino.current !== termino

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
                {productos.map(tarjetaProducto)}
            </div>

            {/* HU 20 - recomendaciones asociadas al término buscado */}
            {termino && !cargando && !error && (
                <div className="recomendaciones">
                    <h3 className="recomendaciones-titulo">
                        Recomendaciones para “{termino}”
                    </h3>

                    {recomendacionesPendientes && (
                        <p className="catalogo-mensaje">
                            Buscando recomendaciones...
                        </p>
                    )}

                    {!recomendacionesPendientes && errorRecomendaciones && (
                        <div className="alerta error">
                            {errorRecomendaciones}
                        </div>
                    )}

                    {!recomendacionesPendientes &&
                        !errorRecomendaciones &&
                        recomendacionesVisibles.length === 0 && (
                            <p className="catalogo-mensaje">
                                No hay recomendaciones relacionadas con esta
                                búsqueda.
                            </p>
                        )}

                    {!recomendacionesPendientes &&
                        !errorRecomendaciones &&
                        recomendacionesVisibles.length > 0 && (
                            <div className="catalogo-grilla">
                                {recomendacionesVisibles.map(tarjetaProducto)}
                            </div>
                        )}
                </div>
            )}
        </section>
    )
}
