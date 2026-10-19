import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { guardarSesion, leerSesion } from '../api/sesion.js'
import {
  actualizarPerfil,
  actualizarFotoPerfil,
  guardarPerfilVendedor,
  obtenerPerfil,
  obtenerPerfilVendedor,
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
 *
 * HU 29 - Sección "Información del vendedor" visible solo para el rol
 * VENDEDOR, con carga, edición y guardado del perfil comercial.
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
      // El header usa la sesión local: se refresca el correo si cambió.
      const sesion = leerSesion()
      if (sesion && sesion.correo !== actualizado.correo) {
        guardarSesion({ ...sesion, correo: actualizado.correo })
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
          {aviso && (
            <div className={`alerta ${aviso.tipo}`} role="alert">
              {aviso.texto}
            </div>
          )}

          <div className="perfil-encabezado">
            <div className="perfil-avatar" aria-label="Avatar de usuario">
              {(previsualizacion || perfil.fotoUrl) ? (
                <img className="perfil-avatar-foto" src={previsualizacion || urlFotoPerfil(perfil.fotoUrl)} alt="Foto de perfil" />
              ) : inicial}
            </div>
            <div>
              <h3 className="perfil-correo">{perfil.correo}</h3>
              <p className="perfil-rol">Rol: {perfil.rol}</p>
            </div>
          </div>

          {/* HU 30 - selección, vista previa y actualización de la foto. */}
          <div className="perfil-foto-controles">
            <label className="btn-secundario" htmlFor="perfil-foto">Elegir foto JPG o PNG</label>
            <input id="perfil-foto" type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" onChange={seleccionarFoto} />
            {fotoSeleccionada && (
              <button type="button" className="btn-primario" onClick={guardarFoto} disabled={subiendoFoto}>
                {subiendoFoto ? 'Actualizando foto…' : 'Actualizar foto'}
              </button>
            )}
            {avisoFoto && <p className="perfil-foto-aviso" role="status">{avisoFoto}</p>}
          </div>

          {!editando && (
            <>
              <dl className="perfil-datos">
                <div>
                  <dt>Nombre</dt>
                  <dd>{perfil.nombre || 'Sin nombre registrado'}</dd>
                </div>
                <div>
                  <dt>Correo electrónico</dt>
                  <dd>{perfil.correo}</dd>
                </div>
                <div>
                  <dt>Teléfono</dt>
                  <dd>{perfil.telefono || 'Sin teléfono registrado'}</dd>
                </div>
                <div>
                  <dt>ID de usuario</dt>
                  <dd>{perfil.id}</dd>
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
                <button type="button" className="btn-primario" onClick={iniciarEdicion}>
                  Editar datos personales
                </button>
                <Link to="/" className="btn-secundario">
                  Volver al inicio
                </Link>
              </div>
            </>
          )}

          {/* HU 28 - modo edición de los campos personales habilitados */}
          {editando && (
            <form className="perfil-formulario" onSubmit={guardarCambios} noValidate>
              <h4>Editar datos personales</h4>

              <div className="campo">
                <label htmlFor="perfil-nombre">Nombre</label>
                <input
                  id="perfil-nombre"
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
                <label htmlFor="perfil-telefono">Teléfono</label>
                <input
                  id="perfil-telefono"
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

              <p className="perfil-nota">
                El rol, los permisos y la contraseña no se pueden editar aquí.
              </p>

              <div className="botones">
                <button type="submit" className="btn-primario" disabled={guardando}>
                  {guardando ? 'Guardando...' : 'Guardar'}
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

          {/* HU 29 - información comercial visible únicamente para VENDEDOR */}
          {perfil.rol === 'VENDEDOR' && (
            <PerfilVendedor />
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

/**
 * HU 29 - Sección "Información del vendedor" dentro del perfil.
 * Carga el perfil comercial con GET, permite completarlo/editarlo y
 * guardarlo con PATCH. Un CLIENTE nunca ve esta sección.
 */
function PerfilVendedor() {
  const [perfilVendedor, setPerfilVendedor] = useState(null)
  const [cargandoVendedor, setCargandoVendedor] = useState(true)
  const [errorVendedor, setErrorVendedor] = useState('')
  const [editandoVendedor, setEditandoVendedor] = useState(false)
  const [borradorVendedor, setBorradorVendedor] = useState({ nombreComercial: '', descripcion: '', telefonoContacto: '' })
  const [erroresVendedor, setErroresVendedor] = useState({})
  const [guardandoVendedor, setGuardandoVendedor] = useState(false)
  const [avisoVendedor, setAvisoVendedor] = useState(null)

  useEffect(() => {
    let vigente = true

    async function cargarVendedor() {
      try {
        setCargandoVendedor(true)
        setErrorVendedor('')
        const datos = await obtenerPerfilVendedor()
        if (vigente) setPerfilVendedor(datos)
      } catch (e) {
        if (vigente) {
          if (e?.response?.status === 403) {
            setErrorVendedor('No tienes permiso para ver la información del vendedor.')
          } else if (e?.request) {
            setErrorVendedor('No se pudo conectar con el servidor.')
          } else {
            setErrorVendedor('No se pudo cargar la información del vendedor.')
          }
        }
      } finally {
        if (vigente) setCargandoVendedor(false)
      }
    }

    cargarVendedor()
    return () => {
      vigente = false
    }
  }, [])

  function iniciarEdicionVendedor() {
    setBorradorVendedor({
      nombreComercial: perfilVendedor?.nombreComercial ?? '',
      descripcion: perfilVendedor?.descripcion ?? '',
      telefonoContacto: perfilVendedor?.telefonoContacto ?? '',
    })
    setErroresVendedor({})
    setAvisoVendedor(null)
    setEditandoVendedor(true)
  }

  function cambiarVendedor(campo, valor) {
    setBorradorVendedor((prev) => ({ ...prev, [campo]: valor }))
    setErroresVendedor((prev) => {
      if (!prev[campo]) return prev
      const nuevo = { ...prev }
      delete nuevo[campo]
      return nuevo
    })
  }

  function validarVendedor() {
    const errs = {}
    if (!borradorVendedor.nombreComercial.trim()) {
      errs.nombreComercial = 'El nombre comercial es obligatorio'
    } else if (borradorVendedor.nombreComercial.trim().length > 80) {
      errs.nombreComercial = 'El nombre comercial no puede superar 80 caracteres'
    }
    if (!borradorVendedor.descripcion.trim()) {
      errs.descripcion = 'La descripción es obligatoria'
    } else if (borradorVendedor.descripcion.trim().length > 500) {
      errs.descripcion = 'La descripción no puede superar 500 caracteres'
    }
    if (!borradorVendedor.telefonoContacto.trim()) {
      errs.telefonoContacto = 'El teléfono de contacto es obligatorio'
    } else if (!TELEFONO_REGEX.test(borradorVendedor.telefonoContacto.trim())) {
      errs.telefonoContacto = 'El teléfono debe contener entre 7 y 15 dígitos (se permite +, espacios y guiones)'
    }
    return errs
  }

  async function guardarVendedor(e) {
    e.preventDefault()
    const errs = validarVendedor()
    setErroresVendedor(errs)
    setAvisoVendedor(null)
    if (Object.keys(errs).length > 0) return

    setGuardandoVendedor(true)
    try {
      const guardado = await guardarPerfilVendedor({
        nombreComercial: borradorVendedor.nombreComercial.trim(),
        descripcion: borradorVendedor.descripcion.trim(),
        telefonoContacto: borradorVendedor.telefonoContacto.trim(),
      })
      setPerfilVendedor(guardado)
      setEditandoVendedor(false)
      setAvisoVendedor({ tipo: 'exito', texto: 'Información del vendedor guardada correctamente.' })
    } catch (err) {
      const estado = err?.response?.status
      if (estado === 400) {
        setAvisoVendedor({ tipo: 'error', texto: 'Revisa los datos ingresados: algunos campos son inválidos.' })
      } else if (estado === 401) {
        setAvisoVendedor({ tipo: 'error', texto: 'Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.' })
      } else if (estado === 403) {
        setAvisoVendedor({ tipo: 'error', texto: 'Solo los vendedores pueden gestionar esta información.' })
      } else if (err?.request) {
        setAvisoVendedor({ tipo: 'error', texto: 'No se pudo conectar con el servidor.' })
      } else {
        setAvisoVendedor({ tipo: 'error', texto: 'No se pudo guardar la información del vendedor.' })
      }
    } finally {
      setGuardandoVendedor(false)
    }
  }

  const sinCompletar = perfilVendedor && !perfilVendedor.nombreComercial?.trim()

  return (
    <div className="perfil-vendedor">
      <h4>Información del vendedor</h4>

      {cargandoVendedor && <p>Cargando información del vendedor...</p>}

      {errorVendedor && (
        <div className="alerta error" role="alert">
          {errorVendedor}
        </div>
      )}

      {avisoVendedor && (
        <div className={`alerta ${avisoVendedor.tipo}`} role="alert">
          {avisoVendedor.texto}
        </div>
      )}

      {!cargandoVendedor && !errorVendedor && perfilVendedor && !editandoVendedor && (
        <>
          {sinCompletar && (
            <p className="perfil-nota">
              Aún no has completado tu información comercial. Complétala para generar más confianza en tus clientes.
            </p>
          )}
          <dl className="perfil-datos">
            <div>
              <dt>Nombre comercial</dt>
              <dd>{perfilVendedor.nombreComercial || 'Sin completar'}</dd>
            </div>
            <div>
              <dt>Descripción</dt>
              <dd>{perfilVendedor.descripcion || 'Sin completar'}</dd>
            </div>
            <div>
              <dt>Teléfono de contacto</dt>
              <dd>{perfilVendedor.telefonoContacto || 'Sin completar'}</dd>
            </div>
          </dl>
          <div className="botones">
            <button type="button" className="btn-primario" onClick={iniciarEdicionVendedor}>
              {sinCompletar ? 'Completar información' : 'Editar información'}
            </button>
          </div>
        </>
      )}

      {!cargandoVendedor && !errorVendedor && editandoVendedor && (
        <form className="perfil-formulario" onSubmit={guardarVendedor} noValidate>
          <div className="campo">
            <label htmlFor="vendedor-nombre">Nombre comercial</label>
            <input
              id="vendedor-nombre"
              type="text"
              value={borradorVendedor.nombreComercial}
              onChange={(e) => cambiarVendedor('nombreComercial', e.target.value)}
              disabled={guardandoVendedor}
              aria-invalid={!!erroresVendedor.nombreComercial}
            />
            {erroresVendedor.nombreComercial && (
              <span className="error" role="alert">{erroresVendedor.nombreComercial}</span>
            )}
          </div>

          <div className="campo">
            <label htmlFor="vendedor-descripcion">Descripción</label>
            <textarea
              id="vendedor-descripcion"
              rows="3"
              value={borradorVendedor.descripcion}
              onChange={(e) => cambiarVendedor('descripcion', e.target.value)}
              disabled={guardandoVendedor}
              aria-invalid={!!erroresVendedor.descripcion}
            />
            {erroresVendedor.descripcion && (
              <span className="error" role="alert">{erroresVendedor.descripcion}</span>
            )}
          </div>

          <div className="campo">
            <label htmlFor="vendedor-telefono">Teléfono de contacto</label>
            <input
              id="vendedor-telefono"
              type="tel"
              value={borradorVendedor.telefonoContacto}
              onChange={(e) => cambiarVendedor('telefonoContacto', e.target.value)}
              disabled={guardandoVendedor}
              aria-invalid={!!erroresVendedor.telefonoContacto}
            />
            {erroresVendedor.telefonoContacto && (
              <span className="error" role="alert">{erroresVendedor.telefonoContacto}</span>
            )}
          </div>

          <div className="botones">
            <button type="submit" className="btn-primario" disabled={guardandoVendedor}>
              {guardandoVendedor ? 'Guardando...' : 'Guardar'}
            </button>
            <button
              type="button"
              className="btn-secundario"
              onClick={() => setEditandoVendedor(false)}
              disabled={guardandoVendedor}
            >
              Cancelar
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
