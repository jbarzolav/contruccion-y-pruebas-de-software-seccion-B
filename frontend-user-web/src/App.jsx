import { useState } from 'react'
import PublicarProducto from './components/PublicarProducto.jsx'
import EditarProducto from './components/EditarProducto.jsx'
import MisProductos from './components/MisProductos.jsx'
import BuscarProductos from './components/BuscarProductos.jsx'

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
        <main className="contenedor">
            <h1>Mi Marketplace</h1>

            {/* HU 01 + HU 02 */}
            <PublicarProducto onProductoCreado={handleProductoCreado} />

            {/* HU 03 */}
            <EditarProducto
                producto={productoEnEdicion}
                onGuardado={handleProductoGuardado}
                onCancelar={() => setProductoEnEdicion(null)}
            />

            {/* HU 05 */}
            <MisProductos
                actualizar={actualizarMisProductos}
                onEditar={setProductoEnEdicion}
            />

            {/* HU 06 */}
            <BuscarProductos />
        </main>
    )
}