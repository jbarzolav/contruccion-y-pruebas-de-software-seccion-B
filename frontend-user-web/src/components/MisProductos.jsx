import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { obtenerMisProductos, gestionarDestacado, urlImagen } from '../api/productos.js'

import { listarUmbralesStock } from '../api/umbralesStock.js'

const estados = {
    DISPONIBLE: 'Disponible',
    AGOTADO: 'Agotada',
    RETIRADO: 'Retirada',
    INACTIVO: 'Inactiva',
}

function estadoVisible(producto) {
    return producto.estado === 'DISPONIBLE' && producto.stock === 0
        ? 'AGOTADO'
        : producto.estado
}

export default function MisProductos({ actualizar, onEditar, onActualizado }) {
    const [productos, setProductos] = useState([])
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [aviso, setAviso] = useState('')
    const [busqueda, setBusqueda] = useState('')
    const [filtro, setFiltro] = useState('')
    const [stockBajo, setStockBajo] = useState(new Set())
    const { hash, key } = useLocation()

    useEffect(() => {
        if (hash.startsWith('#producto-')) {
            setBusqueda('')
            setFiltro('')
        }
    }, [hash, key])

    useEffect(() => {
        let vigente = true
        listarUmbralesStock()
            .then((publicaciones) => {
                if (vigente) {
                    setStockBajo(new Set(publicaciones.filter((item) => item.stockBajo).map((item) => item.idProducto)))
                }
            })
            .catch(() => {
                if (vigente) setStockBajo(new Set())
            })
        return () => { vigente = false }
    }, [actualizar])

    const termino = busqueda.trim().toLocaleLowerCase('es')
    const visibles = productos.filter((producto) =>
        producto.nombre.toLocaleLowerCase('es').includes(termino) &&
        (!filtro || estadoVisible(producto) === filtro)
    )

    useEffect(() => {
        cargarProductos()
    }, [actualizar])

    async function cargarProductos() {
        try {
            setCargando(true)
            setError('')
            const data = await obtenerMisProductos()
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
            await gestionarDestacado(producto.id, producto.vendedorId, activar)
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
            <h2>Mis publicaciones</h2>
            <div className="publicaciones-filtros">
                <label className="campo">
                    Buscar publicación
                    <input type="search" placeholder="Nombre del producto" value={busqueda}
                        onChange={(event) => setBusqueda(event.target.value)} />
                </label>
                <label className="campo">
                    Estado
                    <select value={filtro} onChange={(event) => setFiltro(event.target.value)}>
                        <option value="">Todas</option>
                        <option value="DISPONIBLE">Disponibles</option>
                        <option value="AGOTADO">Agotadas</option>
                        <option value="RETIRADO">Retiradas</option>
                        <option value="INACTIVO">Inactivas</option>
                    </select>
                </label>
            </div>

            {cargando && <p>Cargando productos...</p>}

            {error && <div className="alerta error">{error}</div>}

            {/* HU 19 - aviso cuando el vendedor no tiene el beneficio Premium */}
            {aviso && <div className="alerta error">{aviso}</div>}

            {!cargando && !error && productos.length === 0 && (
                <p>Todavía no tienes productos publicados.</p>
            )}

            {!cargando && !error && productos.length > 0 && visibles.length === 0 && (
                <p>No hay publicaciones que coincidan con la búsqueda y el estado seleccionados.</p>
            )}

            {!cargando && !error && visibles.map((producto) => (
                /* HU 58 - ancla usada por el aviso de stock bajo: /mis-publicaciones#producto-{id} */
                <article className="producto-item" id={`producto-${producto.id}`} key={producto.id}>
                    {/* HU 08 - toda la tarjeta abre el detalle (HU 07) */}
                    <Link
                        to={`/producto/${producto.id}`}
                        className="producto-item-enlace"
                    >
                        {producto.imagenUrl && (
                            <img
                                className="producto-imagen"
                                src={urlImagen(producto.imagenUrl)}
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
                            <p><strong>Stock: {producto.stock}</strong></p>
                            <div className="publicaciones-estados">
                                <span className={`publicacion-estado publicacion-estado--${estadoVisible(producto)}`}>
                                    {estados[estadoVisible(producto)] ?? producto.estado}
                                </span>
                                {estadoVisible(producto) === 'DISPONIBLE' && stockBajo.has(producto.id) && (
                                    <span className="badge badge-stock-bajo">Stock bajo</span>
                                )}
                            </div>
                        </div>
                    </Link>

                    <div className="producto-acciones">
                        <button
                            type="button"
                            className="btn-primario"
                            onClick={() => onEditar?.(producto)}
                        >
                            Editar publicación
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
                            Ver ficha
                        </Link>
                        {producto.estado !== 'RETIRADO' && (
                            <button type="button" className="btn-secundario"
                                onClick={() => onEditar?.(producto, true)}>
                                Retirar publicación
                            </button>
                        )}
                    </div>
                </article>
            ))}
        </section>
    )
}
