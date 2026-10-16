import { useEffect, useState } from 'react'
import { asignarRol, listarUsuarios } from '../api/auth.js'
import { guardarSesion, leerSesion } from '../api/sesion.js'

/** HU 23 - Consultar usuarios y asignar roles desde la cuenta administrativa. */
export default function AdministrarRoles() {
  const [usuarios, setUsuarios] = useState([])
  const [seleccion, setSeleccion] = useState({})
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(null)
  const [mensaje, setMensaje] = useState(null)
  useEffect(() => {
    let vigente = true
    listarUsuarios().then((datos) => { if (vigente) setUsuarios(datos) })
      .catch((error) => { if (vigente) setMensaje({ error: true, texto: error.response?.data?.messages?.[0] || 'No se pudieron cargar los usuarios.' }) })
      .finally(() => { if (vigente) setCargando(false) })
    return () => { vigente = false }
  }, [])

  async function guardar(usuario) {
    if (guardando !== null) return
    const rol = seleccion[usuario.id] || usuario.rol
    setGuardando(usuario.id)
    setMensaje(null)
    try {
      const actualizado = await asignarRol(usuario.id, rol)
      setUsuarios((prev) => prev.map((u) => u.id === actualizado.id ? actualizado : u))
      setMensaje({ error: false, texto: 'Rol actualizado correctamente.' })
      if (leerSesion()?.id === actualizado.id) guardarSesion(actualizado)
    } catch (error) {
      setMensaje({ error: true, texto: error.response?.data?.messages?.[0] || 'No se pudo actualizar el rol.' })
    } finally { setGuardando(null) }
  }

  return (
    <section className="tarjeta">
      <h2>Administrar roles</h2>
      {cargando && <p>Cargando usuarios...</p>}
      {mensaje && <div className={`alerta ${mensaje.error ? 'error' : 'exito'}`} role="alert">{mensaje.texto}</div>}
      {usuarios.map((usuario) => (
        <article className="producto-item" key={usuario.id}>
          <div><strong>{usuario.correo}</strong><p>Rol actual: {usuario.rol}</p></div>
          <div className="producto-acciones">
            <label htmlFor={`rol-${usuario.id}`}>Asignar rol</label>
            <select id={`rol-${usuario.id}`} value={seleccion[usuario.id] || usuario.rol}
              disabled={guardando !== null} onChange={(e) => setSeleccion((prev) => ({ ...prev, [usuario.id]: e.target.value }))}>
              <option value="CLIENTE">Cliente</option>
              <option value="VENDEDOR">Vendedor</option>
              <option value="ADMINISTRADOR">Administrador</option>
            </select>
            <button className="btn-primario" type="button" disabled={guardando !== null || !seleccion[usuario.id] || seleccion[usuario.id] === usuario.rol}
              onClick={() => guardar(usuario)}>{guardando === usuario.id ? 'Guardando...' : 'Guardar rol'}</button>
          </div>
        </article>
      ))}
      {!cargando && !mensaje?.error && usuarios.length === 0 && <p>No hay usuarios registrados.</p>}
    </section>
  )
}
