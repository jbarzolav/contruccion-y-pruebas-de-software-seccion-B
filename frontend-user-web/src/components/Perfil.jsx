import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenerPerfil } from '../api/auth.js'

/**
 * HU 27 - Visualizar perfil del usuario.
 *
 * Consume GET /api/usuarios/me, requiere sesión válida, muestra solo datos
 * autorizados (sin contraseñas ni hashes). Incluye avatar predeterminado.
 */
export default function Perfil() {
  const [perfil, setPerfil] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let vigente = true

    async function cargarPerfil() {
      try {
        setCargando(true)
        setError('')
        const datos = await obtenerPerfil()
        if (vigente) {
          setPerfil(datos)
        }
      } catch (e) {
        if (vigente) {
          if (e?.response?.status === 401) {
            setError('Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.')
          } else if (e?.response?.status === 403) {
            setError('No tienes permiso para ver tu perfil.')
          } else if (e?.request) {
            setError('No se pudo conectar con el servidor.')
          } else {
            setError('No se pudo cargar el perfil.')
          }
        }
      } finally {
        if (vigente) {
          setCargando(false)
        }
      }
    }

    cargarPerfil()
    return () => {
      vigente = false
    }
  }, [])

  const inicial = perfil?.correo?.trim()[0]?.toUpperCase() || 'U'
  const permisos = Array.isArray(perfil?.permisos) ? perfil.permisos : []

  return (
    <section className="tarjeta perfil-tarjeta">
      <h2>Mi perfil</h2>

      {cargando && <p>Cargando información...</p>}

      {error && (
        <div className="alerta error" role="alert">
          {error}
        </div>
      )}

      {!cargando && !error && perfil && (
        <div className="perfil-contenido">
          <div className="perfil-encabezado">
            <div className="perfil-avatar" aria-label="Avatar de usuario">
              {inicial}
            </div>
            <div>
              <h3 className="perfil-correo">{perfil.correo}</h3>
              <p className="perfil-rol">Rol: {perfil.rol}</p>
            </div>
          </div>

          <dl className="perfil-datos">
            <div>
              <dt>ID de usuario</dt>
              <dd>{perfil.id}</dd>
            </div>
            <div>
              <dt>Correo electrónico</dt>
              <dd>{perfil.correo}</dd>
            </div>
            <div>
              <dt>Rol</dt>
              <dd>{perfil.rol}</dd>
            </div>
            <div>
              <dt>Permisos</dt>
              <dd>
                {permisos.length === 0 ? (
                  <span>No hay permisos asignados</span>
                ) : (
                  <ul className="perfil-permisos">
                    {permisos.map((p) => (
                      <li key={p}>{p}</li>
                    ))}
                  </ul>
                )}
              </dd>
            </div>
          </dl>

          <p className="perfil-nota">
            Este perfil muestra únicamente tus datos autorizados.
          </p>

          <div className="botones">
            <Link to="/" className="btn-secundario">
              Volver al inicio
            </Link>
          </div>
        </div>
      )}

      {!cargando && !error && !perfil && (
        <div className="alerta error" role="alert">
          No se pudo recuperar la información del perfil.
        </div>
      )}
    </section>
  )
}
