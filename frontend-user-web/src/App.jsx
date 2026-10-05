import { useState } from 'react'
import { HashRouter, Route, Routes } from 'react-router-dom'
import PublicarProducto from './components/PublicarProducto.jsx'
import EditarProducto from './components/EditarProducto.jsx'
import MisProductos from './components/MisProductos.jsx'
import BuscarProductos from './components/BuscarProductos.jsx'
import Catalogo from './components/Catalogo.jsx'
import DetalleProducto from './components/DetalleProducto.jsx'

export default function App() {
    const [productoEnEdicion, setProductoEnEdicion] = useState(null)
    const [actualizarMisProductos, setActualizarMisProductos] = useState(0)

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

                    {/* HU 07 - detalle de producto */}
                    <Route path="/producto/:idProducto" element={<DetalleProducto />} />
                </Routes>
            </main>
        </HashRouter>
    )
}