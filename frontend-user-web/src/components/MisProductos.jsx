import { useEffect, useState } from 'react'
import { obtenerMisProductos } from '../api/productos.js'

export default function MisProductos({ actualizar, onEditar }) {
    const [productos, setProductos] = useState([])
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')

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

    return (
        <section className="tarjeta mis-productos">
            <h2>Mis productos</h2>

            {cargando && <p>Cargando productos...</p>}

            {error && <div className="alerta error">{error}</div>}

            {!cargando && !error && productos.length === 0 && (
                <p>Todavía no tienes productos publicados.</p>
            )}

            {!cargando && !error && productos.map((producto) => (
                <article className="producto-item" key={producto.id}>
                    {producto.imagenUrl && (
                        <img
                            className="producto-imagen"
                            src={`http://localhost:8080${producto.imagenUrl}`}
                            alt={producto.nombre}
                        />
                    )}

                    <div className="producto-info">
                        <h3>{producto.nombre}</h3>
                        <p>Precio: S/ {Number(producto.precio).toFixed(2)}</p>
                        <p>Stock: {producto.stock}</p>
                        <p>Estado: {producto.estado}</p>

                        <div className="botones">
                            <button
                                type="button"
                                className="btn-secundario"
                                onClick={() => onEditar?.(producto)}
                            >
                                Editar
                            </button>
                        </div>
                    </div>
                </article>
            ))}
        </section>
    )
}