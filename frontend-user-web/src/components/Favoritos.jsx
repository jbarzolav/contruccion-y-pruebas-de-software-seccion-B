import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenerFavoritos, quitarFavorito } from '../api/productos.js'
import { useSesion } from '../api/sesion.js'
import { tarjetaProducto } from './Catalogo.jsx'

const FAVORITOS_POR_PAGINA = 12

/** HU32/HU33 - lista persistente de favoritos del cliente autenticado. */
export default function Favoritos() {
    const sesion = useSesion()
    const [favoritos, setFavoritos] = useState([])
    const [cargando, setCargando] = useState(true)
    const [error, setError] = useState('')
    const [busqueda, setBusqueda] = useState('')
    const [categoria, setCategoria] = useState('')
    const [pagina, setPagina] = useState(1)
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

    const categorias = [...new Set(favoritos.map(producto => producto.categoria?.trim()).filter(Boolean))]
        .sort((a, b) => a.localeCompare(b, 'es'))
    const categoriaActiva = categorias.includes(categoria) ? categoria : ''
    const termino = busqueda.trim().toLocaleLowerCase('es')
    const resultados = favoritos.filter(producto =>
        (!categoriaActiva || producto.categoria?.trim() === categoriaActiva) &&
        producto.nombre.toLocaleLowerCase('es').includes(termino))
    const totalPaginas = Math.max(1, Math.ceil(resultados.length / FAVORITOS_POR_PAGINA))
    const paginaActual = Math.min(pagina, totalPaginas)
    const inicio = (paginaActual - 1) * FAVORITOS_POR_PAGINA

    useEffect(() => {
        if (pagina !== paginaActual) setPagina(paginaActual)
        if (categoria !== categoriaActiva) setCategoria(categoriaActiva)
    }, [pagina, paginaActual, categoria, categoriaActiva])

    if (!sesion) return <section className="tarjeta"><p>Debes iniciar sesión para ver tus favoritos.</p><Link to="/login">Iniciar sesión</Link></section>

    return <section className="tarjeta catalogo">
        <h2>Favoritos</h2>
        <p className="catalogo-ayuda">Productos guardados en tu cuenta.</p>
        {cargando && <p>Cargando favoritos...</p>}
        {error && <div className="alerta error" role="alert">{error}</div>}
        {!cargando && !error && favoritos.length === 0 && <p>No tienes productos favoritos.</p>}
        {favoritos.length > 0 && <div className="filtros-pedidos">
            <label className="campo">
                Buscar en favoritos
                <input type="search" placeholder="Nombre del producto" value={busqueda}
                    onChange={event => { setBusqueda(event.target.value); setPagina(1) }} />
            </label>
            <label className="campo">
                Categoría
                <select value={categoriaActiva}
                    onChange={event => { setCategoria(event.target.value); setPagina(1) }}>
                    <option value="">Todas las categorías</option>
                    {categorias.map(nombre => <option key={nombre} value={nombre}>{nombre}</option>)}
                </select>
            </label>
        </div>}
        {!cargando && favoritos.length > 0 && resultados.length === 0 &&
            <p role="status">No hay favoritos que coincidan con tu búsqueda y categoría.</p>}
        <div className="catalogo-grilla">
            {resultados.slice(inicio, inicio + FAVORITOS_POR_PAGINA).map((producto) => tarjetaProducto(producto, {
                puedeGuardar: false,
                esFavorito: true,
                guardando: idQuitando === producto.id,
                alGuardar: () => {},
                alQuitar: quitar,
            }))}
        </div>
        {totalPaginas > 1 && <nav className="paginacion-pedidos" aria-label="Páginas de favoritos">
            <button type="button" disabled={paginaActual === 1}
                onClick={() => setPagina(paginaActual - 1)}>Anterior</button>
            <span aria-live="polite">Página {paginaActual} de {totalPaginas}</span>
            <button type="button" disabled={paginaActual === totalPaginas}
                onClick={() => setPagina(paginaActual + 1)}>Siguiente</button>
        </nav>}
    </section>
}
