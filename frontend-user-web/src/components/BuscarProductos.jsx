import { useState } from 'react'
import { buscarProductosPorNombre } from '../api/productos.js'

export default function BuscarProductos() {
  const [nombre, setNombre] = useState('')
  const [resultados, setResultados] = useState([])
  const [mensaje, setMensaje] = useState('')
  const [buscando, setBuscando] = useState(false)

  const handleBuscar = async (event) => {
    event.preventDefault()

    const criterio = nombre.trim()

    if (!criterio) {
      setResultados([])
      setMensaje('Ingresa un nombre de producto para buscar.')
      return
    }

    try {
      setBuscando(true)
      setMensaje('')

      const productos = await buscarProductosPorNombre(criterio)
      setResultados(productos)

      if (productos.length === 0) {
        setMensaje('No se encontraron productos disponibles.')
      }
    } catch (error) {
      setResultados([])
      setMensaje('No se pudo realizar la búsqueda.')
    } finally {
      setBuscando(false)
    }
  }

  return (
    <section className="buscar-productos">
      <h2>Buscar productos</h2>

      <form onSubmit={handleBuscar}>
        <input
          type="text"
          placeholder="Nombre del producto"
          value={nombre}
          onChange={(event) => setNombre(event.target.value)}
        />

        <button type="submit" disabled={buscando}>
          {buscando ? 'Buscando...' : 'Buscar'}
        </button>
      </form>

      {mensaje && <p>{mensaje}</p>}

      <div className="resultados-busqueda">
        {resultados.map((producto) => (
          <article key={producto.id} className="producto-busqueda">
            {producto.imagenUrl && (
              <img
                src={`http://localhost:8080${producto.imagenUrl}`}
                alt={producto.nombre}
                width="150"
              />
            )}

            <h3>{producto.nombre}</h3>
            <p>Precio: S/ {Number(producto.precio).toFixed(2)}</p>
            <p>Disponibilidad: {producto.estado}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
