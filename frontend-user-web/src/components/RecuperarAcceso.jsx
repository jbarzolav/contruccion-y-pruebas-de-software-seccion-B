import { useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { solicitarRecuperacion, restablecerPassword } from '../api/auth.js'

/** HU 22 - Solicitud de enlace temporal y formulario de nueva contraseña. */
export default function RecuperarAcceso({ restablecer = false }) {
  const [parametros] = useSearchParams()
  const token = parametros.get('token') || ''
  const [correo, setCorreo] = useState('')
  const [password, setPassword] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [cargando, setCargando] = useState(false)
  const [completado, setCompletado] = useState(false)
  const enviando = useRef(false)

  const cambiar = (setter, campo) => (event) => {
    setter(event.target.value)
    setErrores((prev) => ({ ...prev, [campo]: undefined }))
    setMensaje(null)
  }

  async function enviar(event) {
    event.preventDefault()
    if (enviando.current || completado) return
    const validaciones = {}
    if (restablecer) {
      if (!token.trim()) validaciones.token = 'El enlace de recuperación no contiene un token válido.'
      if (!password.trim()) validaciones.password = 'La contraseña es obligatoria'
      else if (new TextEncoder().encode(password).length > 72) {
        validaciones.password = 'La contraseña supera el límite de 72 bytes.'
      }
      if (!confirmacion.trim()) validaciones.confirmacion = 'Confirma la nueva contraseña'
      else if (password !== confirmacion) validaciones.confirmacion = 'Las contraseñas no coinciden.'
    } else {
      if (!correo.trim()) validaciones.correo = 'El correo es obligatorio'
      else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim())) {
        validaciones.correo = 'El correo debe tener un formato válido'
      }
    }
    setErrores(validaciones)
    setMensaje(null)
    if (Object.keys(validaciones).length) return
    enviando.current = true
    setCargando(true)
    try {
      const respuesta = restablecer
        ? await restablecerPassword(token, password, confirmacion)
        : await solicitarRecuperacion(correo.trim())
      setMensaje({ tipo: 'exito', texto: respuesta.mensaje })
      setCompletado(true)
      setPassword('')
      setConfirmacion('')
    } catch (error) {
      const mensajes = error.response?.data?.messages
      setMensaje({
        tipo: 'error',
        texto: error.response?.status === 400 && Array.isArray(mensajes)
          ? mensajes.join(' ')
          : 'No se pudo completar la solicitud. Inténtalo de nuevo.',
      })
    } finally {
      enviando.current = false
      setCargando(false)
    }
  }

  return (
    <section className="tarjeta login-tarjeta">
      <h2>{restablecer ? 'Nueva contraseña' : 'Recuperar acceso'}</h2>
      <p>{restablecer
        ? 'Ingresa y confirma tu nueva contraseña.'
        : 'Ingresa tu correo para solicitar un enlace temporal de recuperación.'}</p>
      {restablecer && !token.trim() && (
        <div className="alerta error" role="alert">El enlace de recuperación no contiene un token válido.</div>
      )}
      {!completado && (
        <form onSubmit={enviar} noValidate>
          <fieldset disabled={cargando} style={{ border: 0, padding: 0, margin: 0 }}>
            {restablecer ? (
              <>
                <div className="campo">
                  <label htmlFor="nueva-password">Nueva contraseña *</label>
                  <input id="nueva-password" type="password" autoComplete="new-password"
                    value={password} onChange={cambiar(setPassword, 'password')} />
                  {errores.password && <span className="error" role="alert">{errores.password}</span>}
                </div>
                <div className="campo">
                  <label htmlFor="confirmacion-password">Confirmar contraseña *</label>
                  <input id="confirmacion-password" type="password" autoComplete="new-password"
                    value={confirmacion} onChange={cambiar(setConfirmacion, 'confirmacion')} />
                  {errores.confirmacion && <span className="error" role="alert">{errores.confirmacion}</span>}
                </div>
              </>
            ) : (
              <div className="campo">
                <label htmlFor="correo-recuperacion">Correo *</label>
                <input id="correo-recuperacion" type="email" autoComplete="email"
                  placeholder="tucorreo@ejemplo.com" value={correo} onChange={cambiar(setCorreo, 'correo')} />
                {errores.correo && <span className="error" role="alert">{errores.correo}</span>}
              </div>
            )}
            <div className="botones">
              <button type="submit" className="btn-primario" disabled={cargando || (restablecer && !token.trim())}>
                {cargando ? 'Enviando...' : restablecer ? 'Guardar contraseña' : 'Solicitar enlace'}
              </button>
            </div>
          </fieldset>
        </form>
      )}
      {mensaje && (
        <div className={`alerta ${mensaje.tipo}`} role="alert">{mensaje.texto}</div>
      )}
      <p><Link to="/login">Volver a iniciar sesión</Link></p>
      {restablecer && !completado && <p><Link to="/recuperacion">Solicitar un nuevo enlace</Link></p>}
    </section>
  )
}
