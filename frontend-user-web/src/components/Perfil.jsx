import { useEffect, useRef, useState } from 'react'
import { guardarSesion, leerSesion } from '../api/sesion.js'
import {
  actualizarPerfil,
  actualizarFotoPerfil,
  obtenerPerfil,
  urlFotoPerfil,
} from '../api/auth.js'

/**
 * HU 27 - Visualizar perfil del usuario.
 *
 * Consume GET /api/usuarios/me, requiere sesión válida, muestra solo datos
 * autorizados (sin contraseñas ni hashes). Incluye avatar predeterminado.
 *
 * HU 28 - Modo edición de los campos personales habilitados (nombre, correo,
 * teléfono) con validación local, Guardar/Cancelary refresco con el backend.
 */

const CORREO_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const TELEFONO_REGEX = /^\+?[0-9][0-9 \-]{5,17}[0-9]$/

export default function Perfil() {
  const [perfil, setPerfil] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  // HU 28 - estado de la edición de datos personales
  const [editando, setEditando] = useState(false)
  const [borrador, setBorrador] = useState({ nombre: '', correo: '', telefono: '' })
  const [erroresCampo, setErroresCampo] = useState({})
  const [guardando, setGuardando] = useState(false)
  const [aviso, setAviso] = useState(null)
  // HU 30 - selección temporal y vista previa de foto de perfil.
  const [fotoSeleccionada, setFotoSeleccionada] = useState(null)
  const [subiendoFoto, setSubiendoFoto] = useState(false)
  const [avisoFoto, setAvisoFoto] = useState('')
  const [previsualizacion, setPrevisualizacion] = useState('')
  const selectorFoto = useRef(null)

  useEffect(() => {
    if (!fotoSeleccionada) { setPrevisualizacion(''); return undefined }
    const url = URL.createObjectURL(fotoSeleccionada)
    setPrevisualizacion(url)
    return () => URL.revokeObjectURL(url)
  }, [fotoSeleccionada])

  function seleccionarFoto(evento) {
    const archivo = evento.target.files?.[0]
    evento.target.value = ''
    setAvisoFoto('')
    if (!archivo) return
    if (!['image/jpeg', 'image/png'].includes(archivo.type)) {
      setAvisoFoto('Selecciona una imagen JPG o PNG.')
      return
    }
    if (archivo.size > 2 * 1024 * 1024) {
      setAvisoFoto('La foto no puede superar 2 MB.')
      return
    }
    setFotoSeleccionada(archivo)
  }

  async function guardarFoto() {
    if (!fotoSeleccionada || subiendoFoto) return
    setSubiendoFoto(true)
    setAvisoFoto('')
    try {
      const actualizado = await actualizarFotoPerfil(fotoSeleccionada)
      setPerfil(actualizado)
      setFotoSeleccionada(null)
      setAvisoFoto('Foto de perfil actualizada correctamente.')
    } catch (err) {
      setAvisoFoto(err?.response?.data?.messages?.[0] || 'No se pudo actualizar la foto. Inténtalo de nuevo.')
    } finally {
      setSubiendoFoto(false)
    }
  }

  async function cargarPerfil() {
    try {
      setCargando(true)
      setError('')
      const datos = await obtenerPerfil()
      setPerfil(datos)
      return datos
    } catch (e) {
      if (e?.response?.status === 401) {
        setError('Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.')
      } else if (e?.response?.status === 403) {
        setError('No tienes permiso para ver tu perfil.')
      } else if (e?.request) {
        setError('No se pudo conectar con el servidor.')
      } else {
        setError('No se pudo cargar el perfil.')
      }
      return null
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    cargarPerfil()
  }, [])

  // HU 28 - entrar en modo edición con una copia de los datos actuales.
  function iniciarEdicion() {
    setBorrador({
      nombre: perfil?.nombre ?? '',
      correo: perfil?.correo ?? '',
      telefono: perfil?.telefono ?? '',
    })
    setErroresCampo({})
    setAviso(null)
    setEditando(true)
  }

  // HU 28 - Cancelar restaura la vista con los datos previos (sin persistir nada).
  function cancelarEdicion() {
    setEditando(false)
    setErroresCampo({})
    setAviso(null)
  }

  function cambiarBorrador(campo, valor) {
    setBorrador((prev) => ({ ...prev, [campo]: valor }))
    setErroresCampo((prev) => {
      if (!prev[campo]) return prev
      const nuevo = { ...prev }
      delete nuevo[campo]
      return nuevo
    })
  }

  // HU 28 - validación local antes de llamar al backend.
  function validarBorrador() {
    const errs = {}
    if (!borrador.nombre.trim()) {
      errs.nombre = 'El nombre es obligatorio'
    } else if (borrador.nombre.trim().length > 100) {
      errs.nombre = 'El nombre no puede superar 100 caracteres'
    }
    if (!borrador.correo.trim()) {
      errs.correo = 'El correo es obligatorio'
    } else if (!CORREO_REGEX.test(borrador.correo.trim())) {
      errs.correo = 'El correo debe tener un formato válido'
    }
    if (borrador.telefono.trim() && !TELEFONO_REGEX.test(borrador.telefono.trim())) {
      errs.telefono = 'El teléfono debe contener entre 7 y 15 dígitos (se permite +, espacios y guiones)'
    }
    return errs
  }

  // HU 28 - guardar: PATCH con solo los campos habilitados y refresco del perfil.
  async function guardarCambios(e) {
    e.preventDefault()
    const errs = validarBorrador()
    setErroresCampo(errs)
    setAviso(null)
    if (Object.keys(errs).length > 0) return

    setGuardando(true)
    try {
      const actualizado = await actualizarPerfil({
        nombre: borrador.nombre.trim(),
        correo: borrador.correo.trim(),
        telefono: borrador.telefono.trim(),
      })
      setPerfil(actualizado)
      setEditando(false)
      setAviso({ tipo: 'exito', texto: 'Datos actualizados correctamente.' })
      // El encabezado usa el nombre actualizado de la sesión.
      const sesion = leerSesion()
      if (sesion && (sesion.correo !== actualizado.correo || sesion.nombre !== actualizado.nombre)) {
        guardarSesion({ ...sesion, correo: actualizado.correo, nombre: actualizado.nombre })
      }
    } catch (err) {
      const estado = err?.response?.status
      if (estado === 409) {
        setErroresCampo({ correo: 'El correo ya está registrado por otra cuenta' })
      } else if (estado === 400) {
        setAviso({ tipo: 'error', texto: 'Revisa los datos ingresados: algunos campos son inválidos.' })
      } else if (estado === 401) {
        setAviso({ tipo: 'error', texto: 'Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.' })
      } else if (err?.request) {
        setAviso({ tipo: 'error', texto: 'No se pudo conectar con el servidor.' })
      } else {
        setAviso({ tipo: 'error', texto: 'No se pudo actualizar el perfil.' })
      }
    } finally {
      setGuardando(false)
    }
  }

  const nombreVisible = perfil?.nombre?.trim() || 'Completa tu perfil'
  const inicial = nombreVisible[0]?.toUpperCase() || 'U'

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
          {aviso && (
            <div className={`alerta ${aviso.tipo}`} role="alert">
              {aviso.texto}
            </div>
          )}

          <header className="perfil-encabezado">
            <div className="perfil-avatar" aria-label="Avatar de usuario">
              {(previsualizacion || perfil.fotoUrl) ? (
                <img className="perfil-avatar-foto" src={previsualizacion || urlFotoPerfil(perfil.fotoUrl)} alt="Foto de perfil" />
              ) : inicial}
            </div>
            <div className="perfil-identidad">
              <h3 className="perfil-correo">{nombreVisible}</h3>
              <div className="perfil-foto-controles">
                <input ref={selectorFoto} hidden type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png"
                  onChange={seleccionarFoto} disabled={subiendoFoto || guardando} />
                {fotoSeleccionada ? (
                  <>
                    <button type="button" className="btn-primario" onClick={guardarFoto} disabled={subiendoFoto || guardando}>
                      {subiendoFoto ? 'Guardando foto…' : 'Guardar foto'}
                    </button>
                    <button type="button" className="btn-secundario" disabled={subiendoFoto}
                      onClick={() => { setFotoSeleccionada(null); setAvisoFoto('') }}>
                      Descartar foto
                    </button>
                  </>
                ) : (
                  <button type="button" className="btn-secundario" onClick={() => selectorFoto.current?.click()} disabled={guardando}>
                    {perfil.fotoUrl ? 'Cambiar foto' : 'Agregar foto'}
                  </button>
                )}
              </div>
              <small className="perfil-foto-ayuda">JPG o PNG · Hasta 2 MB</small>
            </div>
          </header>
          {avisoFoto && <p className="perfil-foto-aviso" role="status">{avisoFoto}</p>}

          {!editando && (
            <section aria-labelledby="perfil-datos-titulo">
              <div className="perfil-datos-cabecera">
                <h3 id="perfil-datos-titulo">Datos personales</h3>
                <button type="button" className="btn-secundario" onClick={iniciarEdicion}>
                  Editar datos personales
                </button>
              </div>
              <dl className="perfil-datos">
                <div>
                  <dt>Nombre</dt>
                  <dd>{perfil.nombre || 'Agrega tu nombre al perfil'}</dd>
                </div>
                <div>
                  <dt>Correo electrónico</dt>
                  <dd>{perfil.correo}</dd>
                </div>
                <div>
                  <dt>Teléfono</dt>
                  <dd>{perfil.telefono || 'Sin teléfono registrado'}</dd>
                </div>
              </dl>

            </section>
          )}

          {/* HU 28 - modo edición de los campos personales habilitados */}
          {editando && (
            <form className="perfil-formulario" onSubmit={guardarCambios} noValidate>
              <h3>Editar datos personales</h3>

              <div className="campo">
                <label htmlFor="perfil-nombre">Nombre</label>
                <input
                  id="perfil-nombre"
                  autoComplete="name"
                  type="text"
                  value={borrador.nombre}
                  onChange={(e) => cambiarBorrador('nombre', e.target.value)}
                  disabled={guardando}
                  aria-invalid={!!erroresCampo.nombre}
                />
                {erroresCampo.nombre && (
                  <span className="error" role="alert">{erroresCampo.nombre}</span>
                )}
              </div>

              <div className="campo">
                <label htmlFor="perfil-correo-input">Correo electrónico</label>
                <input
                  id="perfil-correo-input"
                  autoComplete="email"
                  type="email"
                  value={borrador.correo}
                  onChange={(e) => cambiarBorrador('correo', e.target.value)}
                  disabled={guardando}
                  aria-invalid={!!erroresCampo.correo}
                />
                {erroresCampo.correo && (
                  <span className="error" role="alert">{erroresCampo.correo}</span>
                )}
              </div>

              <div className="campo">
                <label htmlFor="perfil-telefono">Teléfono (opcional)</label>
                <input
                  id="perfil-telefono"
                  autoComplete="tel"
                  type="tel"
                  value={borrador.telefono}
                  onChange={(e) => cambiarBorrador('telefono', e.target.value)}
                  disabled={guardando}
                  aria-invalid={!!erroresCampo.telefono}
                />
                {erroresCampo.telefono && (
                  <span className="error" role="alert">{erroresCampo.telefono}</span>
                )}
              </div>

              <div className="botones">
                <button type="submit" className="btn-primario" disabled={guardando || subiendoFoto}>
                  {guardando ? 'Guardando...' : 'Guardar cambios'}
                </button>
                <button
                  type="button"
                  className="btn-secundario"
                  onClick={cancelarEdicion}
                  disabled={guardando}
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}

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
