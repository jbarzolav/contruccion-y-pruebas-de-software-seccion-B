import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenerFavoritos, quitarFavorito } from '../api/productos.js'
import { useSesion } from '../api/sesion.js'
import { tarjetaProducto } from './Catalogo.jsx'

/** HU32/HU33 - lista persistente de favoritos del cliente autenticado. */
export default function Favoritos() {
    const sesion = useSesion()
    const [favoritos, setFavoritos] = useState([])
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [idQuitando, setIdQuitando] = useState(null)

    async function cargar() {
        setCargando(true)
        setError('')
        try {
            setFavoritos(await obtenerFavoritos())
        } catch {
            setError('No se pudieron cargar tus favoritos.')
        } finally {
            setCargando(false)
        }
    }

    useEffect(() => {
        if (sesion?.token) cargar()
    }, [sesion?.token])

    async function quitar(producto) {
        if (!producto?.id || idQuitando != null) return
        setIdQuitando(producto.id)
        try {
            await quitarFavorito(producto.id)
            setFavoritos((actuales) => actuales.filter((item) => item.id !== producto.id))
        } catch (err) {
            setError(err?.response?.status === 404
                ? 'El favorito ya no existe; actualizando la lista.'
                : 'No se pudo quitar el favorito.')
            if (err?.response?.status === 404) cargar()
        } finally {
            setIdQuitando(null)
        }
    }

    if (!sesion) return <section className="tarjeta"><p>Debes iniciar sesión para ver tus favoritos.</p><Link to="/login">Iniciar sesión</Link></section>

    return <section className="tarjeta catalogo">
        <h2>Favoritos</h2>
        <p className="catalogo-ayuda">Productos guardados en tu cuenta.</p>
        {cargando && <p>Cargando favoritos...</p>}
        {error && <div className="alerta error" role="alert">{error}</div>}
        {!cargando && !error && favoritos.length === 0 && <p>No tienes productos favoritos.</p>}
        <div className="catalogo-grilla">
            {favoritos.map((producto) => tarjetaProducto(producto, {
                puedeGuardar: false,
                esFavorito: true,
                guardando: idQuitando === producto.id,
                alGuardar: () => {},
                alQuitar: quitar,
            }))}
        </div>
    </section>
}
