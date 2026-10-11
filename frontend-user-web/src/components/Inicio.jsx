import { useState } from 'react'
import Catalogo from './Catalogo.jsx'

/**
 * Rediseño UI/UX - Página de Inicio: catálogo público con la barra
 * de búsqueda (HU 06) y el filtro de ordenamiento (HU 09) arriba.
 */
export default function Inicio({ actualizar }) {
    const [nombre, setNombre] = useState('')
    const [sort, setSort] = useState('')

    return (
        <section className="inicio">
            <div className="barra-catalogo">
                <input
                    type="search"
                    placeholder="🔍 Buscar productos por nombre..."
                    value={nombre}
                    onChange={(evento) => setNombre(evento.target.value)}
                    aria-label="Buscar productos por nombre"
                />

                <select
                    value={sort}
                    onChange={(evento) => setSort(evento.target.value)}
                    aria-label="Ordenar por"
                >
                    <option value="">Orden por defecto</option>
                    <option value="precio_asc">Precio: menor a mayor</option>
                    <option value="precio_desc">Precio: mayor a menor</option>
                    <option value="nombre_asc">Nombre: A-Z</option>
                    <option value="nombre_desc">Nombre: Z-A</option>
                </select>
            </div>

            <Catalogo nombre={nombre} sort={sort} actualizar={actualizar} />
        </section>
    )
}
