import { useEffect, useState } from 'react'
import { obtenerProducto, urlImagen } from '../api/productos.js'

export default function ImagenCarrito({ productoId, nombre }) {
    const [imagen, setImagen] = useState(null)

    useEffect(() => {
        let activo = true
        setImagen(null)
        obtenerProducto(productoId)
            .then(producto => {
                if (activo) setImagen(urlImagen(producto.imagenUrl))
            })
            .catch(() => {
                if (activo) setImagen(null)
            })
        return () => { activo = false }
    }, [productoId])

    return imagen ? (
        <img
            className="carrito-miniatura"
            src={imagen}
            alt={nombre}
            width="72"
            height="72"
            onError={() => setImagen(null)}
        />
    ) : (
        <span className="carrito-miniatura carrito-sin-imagen">Sin imagen</span>
    )
}
