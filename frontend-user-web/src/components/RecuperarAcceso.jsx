import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { solicitarRecuperacion, verificarCodigoRecuperacion, restablecerPassword } from '../api/auth.js'

/**
 * HU 22 - Recuperación en dos pasos: primero se solicita y valida el código
 * recibido por correo; solo cuando el código es correcto se abre el
 * formulario de nueva contraseña.
 */
export default function RecuperarAcceso({ restablecer = false }) {
  const [parametros] = useSearchParams()
  const tokenEnlace = parametros.get('token') || ''
  const [codigo, setCodigo] = useState(tokenEnlace)
  const [codigoVerificado, setCodigoVerificado] = useState(false)
  const [correo, setCorreo] = useState('')
  const [password, setPassword] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [cargando, setCargando] = useState(false)
  const [completado, setCompletado] = useState(false)
  const enviando = useRef(false)
  const validadoAlEntrar = useRef(false)

  const cambiar = (setter, campo) => (event) => {
    setter(event.target.value)
    setErrores((prev) => ({ ...prev, [campo]: undefined }))
    setMensaje(null)
  }

  // Si el usuario llega por el enlace del correo, el código se valida igual
  // que si lo escribe a mano: primero se comprueba y después se muestra la
  // ventana de nueva contraseña.
  useEffect(() => {
    if (restablecer && tokenEnlace && !validadoAlEntrar.current) {
      validadoAlEntrar.current = true
      validarCodigo(tokenEnlace)
    }
  }, [])

  async function validarCodigo(valor) {
    if (enviando.current) return
    const limpio = (valor || '').trim()
    if (!limpio) {
      setErrores((prev) => ({ ...prev, codigo: 'Ingresa el código de verificación que enviamos a tu correo.' }))
      return
    }
    enviando.current = true
    setCargando(true)
    setErrores((prev) => ({ ...prev, codigo: undefined }))
    setMensaje(null)
    try {
      await verificarCodigoRecuperacion(limpio)
      setCodigo(limpio)
      setCodigoVerificado(true)
    } catch (error) {
      const mensajes = error.response?.data?.messages
      setCodigoVerificado(false)
      setErrores((prev) => ({
        ...prev,
        codigo: Array.isArray(mensajes) && mensajes.length
          ? mensajes.join(' ')
          : 'El código no es válido o ya venció. Solicita uno nuevo.',
      }))
    } finally {
      enviando.current = false
      setCargando(false)
    }
  }

  async function enviar(event) {
    event.preventDefault()
    if (enviando.current || completado) return
    if (restablecer && !codigoVerificado) {
      await validarCodigo(codigo)
      return
    }
    const validaciones = {}
    if (restablecer) {
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
        ? await restablecerPassword(codigo.trim(), password, confirmacion)
        : await solicitarRecuperacion(correo.trim())
      setMensaje({ tipo: 'exito', texto: respuesta.mensaje })
      setCompletado(true)
      setPassword('')
      setConfirmacion('')
    } catch (error) {
      const mensajes = error.response?.data?.messages
      const texto = error.response?.status === 400 && Array.isArray(mensajes) && mensajes.length
        ? mensajes.join(' ')
        : 'No se pudo completar la solicitud. Inténtalo de nuevo.'
      // Si el código dejó de servir (venció o se agotaron los intentos),
      // se vuelve al paso del código.
      if (restablecer && /c[oó]digo/i.test(texto)) {
        setCodigoVerificado(false)
        setErrores({ codigo: texto })
      }
      setMensaje({ tipo: 'error', texto })
    } finally {
      enviando.current = false
      setCargando(false)
    }
  }

  return (
    <section className="tarjeta login-tarjeta">
      <h2>{restablecer ? 'Nueva contraseña' : 'Recuperar acceso'}</h2>
      <p>{restablecer
        ? (codigoVerificado
          ? 'Código verificado. Ingresa y confirma tu nueva contraseña.'
          : 'Ingresa el código de verificación que enviamos a tu correo.')
        : 'Ingresa tu correo para recibir un código temporal de recuperación.'}</p>
      {!completado && (
        <form onSubmit={enviar} noValidate>
          <fieldset disabled={cargando} style={{ border: 0, padding: 0, margin: 0 }}>
            {restablecer ? (
              codigoVerificado ? (
                <>
                  <p style={{ margin: '0 0 12px' }}>
                    Código verificado: <strong>{codigo.trim()}</strong>
                  </p>
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
                  <label htmlFor="codigo-recuperacion">Código de verificación *</label>
                  <input id="codigo-recuperacion" type="text" inputMode="numeric" maxLength={6}
                    autoComplete="one-time-code" placeholder="123456" value={codigo}
                    onChange={cambiar(setCodigo, 'codigo')} />
                  {errores.codigo && <span className="error" role="alert">{errores.codigo}</span>}
                </div>
              )
            ) : (
              <div className="campo">
                <label htmlFor="correo-recuperacion">Correo *</label>
                <input id="correo-recuperacion" type="email" autoComplete="email"
                  placeholder="tucorreo@ejemplo.com" value={correo} onChange={cambiar(setCorreo, 'correo')} />
                {errores.correo && <span className="error" role="alert">{errores.correo}</span>}
              </div>
            )}
            <div className="botones">
              <button type="submit" className="btn-primario" disabled={cargando}>
                {cargando
                  ? (restablecer && !codigoVerificado ? 'Verificando...' : 'Enviando...')
                  : restablecer
                    ? (codigoVerificado ? 'Guardar contraseña' : 'Verificar código')
                    : 'Solicitar código'}
              </button>
            </div>
            {restablecer && codigoVerificado && (
              <p>
                <button type="button" onClick={() => {
                  setCodigoVerificado(false)
                  setPassword('')
                  setConfirmacion('')
                  setErrores({})
                  setMensaje(null)
                }} style={{ background: 'none', border: 0, padding: 0, color: 'inherit', textDecoration: 'underline', cursor: 'pointer' }}>Usar otro código</button>
              </p>
            )}
          </fieldset>
        </form>
      )}
      {mensaje && (
        <div className={`alerta ${mensaje.tipo}`} role="alert">{mensaje.texto}</div>
      )}
      {!restablecer && completado && (
        <div className="botones">
          <Link className="btn-primario" to="/restablecer">Ingresar el código</Link>
        </div>
      )}
      <p><Link to="/login">Volver a iniciar sesión</Link></p>
      {restablecer && !completado && <p><Link to="/recuperacion">Solicitar un nuevo código</Link></p>}
    </section>
  )
}
