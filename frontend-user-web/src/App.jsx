import { useEffect, useState } from 'react'
import { HashRouter, Link, Route, Routes } from 'react-router-dom'
import PublicarProducto from './components/PublicarProducto.jsx'
import EditarProducto from './components/EditarProducto.jsx'
import MisProductos from './components/MisProductos.jsx'
import BuscarProductos from './components/BuscarProductos.jsx'
import Catalogo from './components/Catalogo.jsx'
import DetalleProducto from './components/DetalleProducto.jsx'
import Carrito from './components/Carrito.jsx'
import ConfirmarCompra from './components/ConfirmarCompra.jsx'
import { obtenerCarrito } from './api/productos.js'

export default function App() {
    const [productoEnEdicion, setProductoEnEdicion] = useState(null)
    const [actualizarMisProductos, setActualizarMisProductos] = useState(0)

    // HU 11 - total de unidades en el carrito (badge visual del estado del carrito)
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

    const handleProductoCreado = (producto) => {
        setProductoEnEdicion(producto)
        setActualizarMisProductos((valor) => valor + 1)
    }

    const handleProductoGuardado = (producto) => {
        setProductoEnEdicion(producto)
        setActualizarMisProductos((valor) => valor + 1)
    }

    return (
        <HashRouter>
            <main className="contenedor">
                <h1>Mi Marketplace</h1>

                {/* HU 11 - acceso al carrito con contador de unidades */}
                <nav className="nav-principal">
                    <Link to="/">Inicio</Link>
                    <Link to="/carrito" className="nav-carrito">
                        Carrito
                        {itemsEnCarrito > 0 && (
                            <span className="badge-carrito">{itemsEnCarrito}</span>
                        )}
                    </Link>
                </nav>

                <Routes>
                    <Route
                        path="/"
                        element={
                            <>
                                {/* HU 01 + HU 02 */}
                                <PublicarProducto onProductoCreado={handleProductoCreado} />

                                {/* HU 03 */}
                                <EditarProducto
                                    producto={productoEnEdicion}
                                    onGuardado={handleProductoGuardado}
                                    onCancelar={() => setProductoEnEdicion(null)}
                                />

                                {/* HU 07 + HU 08 - catálogo con tarjetas clickeables */}
                                <Catalogo />

                                {/* HU 05 */}
                                <MisProductos
                                    actualizar={actualizarMisProductos}
                                    onEditar={setProductoEnEdicion}
                                />

                                {/* HU 06 */}
                                <BuscarProductos />
                            </>
                        }
                    />

                    {/* HU 07 - detalle de producto (HU 11 avisa al badge tras agregar) */}
                    <Route
                        path="/producto/:idProducto"
                        element={<DetalleProducto onCarritoActualizado={recargarCarrito} />}
                    />

                    {/* HU 12 - vista del carrito con cantidades y subtotales */}
                    <Route
                        path="/carrito"
                        element={<Carrito onCarritoActualizado={recargarCarrito} />}
                    />

                    {/* HU 15 - checkout: resumen y registro del pedido */}
                    <Route
                        path="/confirmar-compra"
                        element={<ConfirmarCompra onCarritoActualizado={recargarCarrito} />}
                    />
                </Routes>
            </main>
        </HashRouter>
    )
}