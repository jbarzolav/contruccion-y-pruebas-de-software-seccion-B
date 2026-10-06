import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buscarProductosPorNombre } from '../api/productos.js'

export default function BuscarProductos() {
  const [nombre, setNombre] = useState('')
  const [sort, setSort] = useState('')
  const [resultados, setResultados] = useState([])
  const [mensaje, setMensaje] = useState('')
  const [buscando, setBuscando] = useState(false)

  // HU 06 / HU 09 - búsqueda en tiempo real con debounce y ordenamiento
  useEffect(() => {
    const criterio = nombre.trim()

    if (!criterio) {
      setResultados([])
      setMensaje('')
      return undefined
    }

    const timer = setTimeout(() => {
      ejecutarBusqueda(criterio, sort)
    }, 300)

    return () => clearTimeout(timer)
  }, [nombre, sort])

  async function ejecutarBusqueda(criterio, criterioOrden = sort) {
    try {
      setBuscando(true)
      setMensaje('')

      const productos = await buscarProductosPorNombre(
          criterio,
          criterioOrden
      )

      setResultados(productos)

      if (productos.length === 0) {
        setMensaje('No se encontraron productos disponibles.')
      }
    } catch {
      setResultados([])
      setMensaje('No se pudo realizar la búsqueda.')
    } finally {
      setBuscando(false)
    }
  }

  const handleBuscar = (event) => {
    event.preventDefault()

    const criterio = nombre.trim()

    if (!criterio) {
      setResultados([])
      setMensaje('Ingresa un nombre de producto para buscar.')
      return
    }

    // el botón fuerza la búsqueda sin esperar al debounce
    ejecutarBusqueda(criterio, sort)
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

          <select
              value={sort}
              onChange={(event) => setSort(event.target.value)}
              aria-label="Ordenar por"
          >
            <option value="">Ordenar por</option>
            <option value="precio_asc">Precio: menor a mayor</option>
            <option value="precio_desc">Precio: mayor a menor</option>
            <option value="nombre_asc">Nombre: A-Z</option>
            <option value="nombre_desc">Nombre: Z-A</option>
          </select>
        </form>

        {mensaje && <p>{mensaje}</p>}

        <div className="resultados-busqueda">
          {resultados.map((producto) => (
              <Link
                  key={producto.id}
                  to={`/producto/${producto.id}`}
                  className="producto-busqueda producto-enlace"
              >
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
              </Link>
          ))}
        </div>
      </section>
  )
}
