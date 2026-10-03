import { useState } from 'react'
import PublicarProducto from './components/PublicarProducto.jsx'
import EditarProducto from './components/EditarProducto.jsx'

export default function App() {
  const [productoEnEdicion, setProductoEnEdicion] = useState(null)

  return (
    <main className="contenedor">
      <h1>Mi Marketplace</h1>

      {/* HU 01 + HU 02 */}
      <PublicarProducto onProductoCreado={setProductoEnEdicion} />

      {/* HU 03 */}
      <EditarProducto
        producto={productoEnEdicion}
        onGuardado={setProductoEnEdicion}
        onCancelar={() => setProductoEnEdicion(null)}
      />
    </main>
  )
}
