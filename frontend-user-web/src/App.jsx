import { useEffect, useState } from 'react'
import { HashRouter, Link, Route, Routes } from 'react-router-dom'
import Inicio from './components/Inicio.jsx'
import PublicarProducto from './components/PublicarProducto.jsx'
import EditarProducto from './components/EditarProducto.jsx'
import MisProductos from './components/MisProductos.jsx'
import DetalleProducto from './components/DetalleProducto.jsx'
import ConfirmarCompra from './components/ConfirmarCompra.jsx'
import PlanPremium from './components/PlanPremium.jsx'
import ProductoModal from './components/ProductoModal.jsx'
import CarritoDrawer from './components/CarritoDrawer.jsx'
import ChatbotFab from './components/ChatbotFab.jsx'
import { obtenerCarrito } from './api/productos.js'

/**
 * Rediseño UI/UX - PulgaTec.
 * Header con 3 accesos (Inicio · Mis Publicaciones · Plan Premium),
 * carrito como panel lateral, publicar/editar en modal y chatbot flotante.
 */
export default function App() {
    const [productoEnEdicion, setProductoEnEdicion] = useState(null)
    const [publicando, setPublicando] = useState(false)
    const [drawerCarritoAbierto, setDrawerCarritoAbierto] = useState(false)
    const [actualizarMisProductos, setActualizarMisProductos] = useState(0)

    // HU 11 - total de unidades en el carrito (badge del header)
    const [itemsEnCarrito, setItemsEnCarrito] = useState(0)

    useEffect(() => {
        recargarCarrito()
    }, [])

    // GET /api/carrito/items → suma de unidades para el badge
    const recargarCarrito = async () => {
        try {
            const items = await obtenerCarrito()
            const total = items.reduce((suma, item) => suma + item.cantidad, 0)
            setItemsEnCarrito(total)
        } catch {
            // Backend apagado: el badge queda en 0 sin romper la vista
            setItemsEnCarrito(0)
        }
    }

    // ------------------------------------------------------------
    // Modal de publicar (HU 01/02) y editar (HU 03)
    // ------------------------------------------------------------
    const modalAbierto = publicando || productoEnEdicion !== null

    function abrirPublicacion() {
        setProductoEnEdicion(null)
        setPublicando(true)
    }

    function abrirEdicion(producto) {
        setPublicando(false)
        setProductoEnEdicion(producto)
    }

    function cerrarModal() {
        setPublicando(false)
        setProductoEnEdicion(null)
    }

    const refrescarPublicaciones = () => {
        setActualizarMisProductos((valor) => valor + 1)
    }

    // Al publicar, el modal pasa al formulario de edición del producto creado.
    const handleProductoCreado = (producto) => {
        refrescarPublicaciones()
        setProductoEnEdicion(producto)
    }

    const handleProductoGuardado = (producto) => {
        refrescarPublicaciones()
        setProductoEnEdicion(producto)
    }

    return (
        <HashRouter>
            <main className="contenedor">
                {/* ===== Header: 3 accesos principales + acciones ===== */}
                <header className="app-header">
                    <span className="app-marca">PulgaTec</span>

                    <nav className="nav-principal" aria-label="Navegación principal">
                        <Link to="/">Inicio</Link>
                        <Link to="/mis-publicaciones">Mis Publicaciones</Link>
                        <Link to="/premium">Plan Premium</Link>
                    </nav>

                    <div className="app-acciones">
                        {/* Carrito como panel lateral (sin cambiar de página) */}
                        <button
                            type="button"
                            className="btn-carrito"
                            onClick={() => setDrawerCarritoAbierto(true)}
                            aria-label="Abrir el carrito de compras"
                        >
                            🛒
                            {itemsEnCarrito > 0 && (
                                <span className="badge-carrito">{itemsEnCarrito}</span>
                            )}
                        </button>

                        <button
                            type="button"
                            className="btn-primario btn-publicar"
                            onClick={abrirPublicacion}
                        >
                            + Publicar
                        </button>
                    </div>
                </header>

                <Routes>
                    {/* Inicio: catálogo con búsqueda (HU 06) y orden (HU 09)
                        + insignias Destacado/Agotado (HU 07/HU 19) */}
                    <Route
                        path="/"
                        element={<Inicio actualizar={actualizarMisProductos} />}
                    />

                    {/* HU 05 + HU 19 - gestión del vendedor (ver, editar, destacar) */}
                    <Route
                        path="/mis-publicaciones"
                        element={
                            <MisProductos
                                actualizar={actualizarMisProductos}
                                onEditar={abrirEdicion}
                                onActualizado={refrescarPublicaciones}
                            />
                        }
                    />

                    {/* HU 07 - detalle de producto (HU 11 avisa al badge tras agregar) */}
                    <Route
                        path="/producto/:idProducto"
                        element={
                            <DetalleProducto onCarritoActualizado={recargarCarrito} />
                        }
                    />

                    {/* HU 15 - checkout: resumen y registro del pedido */}
                    <Route
                        path="/confirmar-compra"
                        element={
                            <ConfirmarCompra onCarritoActualizado={recargarCarrito} />
                        }
                    />

                    {/* HU 18 - Beneficios Premium */}
                    <Route path="/premium" element={<PlanPremium />} />
                </Routes>

                {/* Modal flotante de Publicar (HU 01/02) o Editar (HU 03) */}
                {modalAbierto && (
                    <ProductoModal
                        titulo={
                            productoEnEdicion
                                ? `Editar producto (ID ${productoEnEdicion.id})`
                                : 'Publicar producto'
                        }
                        onCerrar={cerrarModal}
                    >
                        {productoEnEdicion ? (
                            <EditarProducto
                                producto={productoEnEdicion}
                                onGuardado={handleProductoGuardado}
                                onCancelar={cerrarModal}
                            />
                        ) : (
                            <PublicarProducto onProductoCreado={handleProductoCreado} />
                        )}
                    </ProductoModal>
                )}

                {/* Panel lateral del carrito (HU 11 / 12 / 13 / 14) */}
                <CarritoDrawer
                    abierto={drawerCarritoAbierto}
                    onCerrar={() => setDrawerCarritoAbierto(false)}
                    onCarritoActualizado={recargarCarrito}
                />

                {/* HU 16 / HU 17 - asistente IA como widget flotante */}
                <ChatbotFab />
            </main>
        </HashRouter>
    )
}
