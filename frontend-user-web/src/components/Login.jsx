import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login } from '../api/auth.js'

/**
 * HU 21 - Inicio de sesión seguro.
 *
 * Consume POST /api/auth/login y guarda la sesión en localStorage
 * (token + id + correo). Nunca muestra, registra ni almacena la
 * contraseña. No protege ninguna ruta: las demás páginas siguen
 * abiertas (la autorización global llega en historias posteriores).
 */
export default function Login() {
  const [correo, setCorreo] = useState('')
  const [password, setPassword] = useState('')
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [cargando, setCargando] = useState(false)

  // Corta el doble envío de forma SÍNCRONA: aunque dos clics lleguen
  // antes de que React repinte el botón deshabilitado, solo sale uno
  const enviandoRef = useRef(false)

  const navigate = useNavigate()

  // Validación local antes de llamar al backend
  const validar = () => {
    const errs = {}

    if (!correo.trim()) {
      errs.correo = 'El correo es obligatorio'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim())) {
      errs.correo = 'El correo debe tener un formato válido'
    }

    if (!password) {
      errs.password = 'La contraseña es obligatoria'
    }

    return errs
  }

  const handleChange = (setter, campo) => (e) => {
    setter(e.target.value)

    setErrores((prev) => {
      if (!prev[campo]) return prev
      const nuevo = { ...prev }
      delete nuevo[campo]
      return nuevo
    })

    setMensaje(null)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()

    // Doble clic: el botón se deshabilita (UI) y el ref corta aquí
    if (enviandoRef.current) return

    const errs = validar()
    setErrores(errs)
    setMensaje(null)

    if (Object.keys(errs).length > 0) {
      return
    }

    enviandoRef.current = true
    setCargando(true)

    try {
      const respuesta = await login(correo.trim(), password)

      // HU 21 - sesión: solo token e id del usuario (nunca la contraseña)
      localStorage.setItem('pulgatec_token', respuesta.token)
      localStorage.setItem('pulgatec_usuario_id', String(respuesta.id))
      localStorage.setItem('pulgatec_usuario_correo', respuesta.correo)

      setMensaje({ tipo: 'exito', texto: '¡Sesión iniciada!' })
      navigate('/')
    } catch (error) {
      if (error.response?.status === 401) {
        // Mismo mensaje genérico: no revela si el correo existe
        setMensaje({
          tipo: 'error',
          texto: 'Correo o contraseña incorrectos.',
        })
      } else {
        // Backend apagado o sin respuesta
        setMensaje({
          tipo: 'error',
          texto: 'No hay conexión con el servidor (localhost:9000). Inténtalo de nuevo.',
        })
      }
    } finally {
      enviandoRef.current = false
      setCargando(false)
    }
  }

  return (
    <section className="tarjeta login-tarjeta">
      <h2>Iniciar sesión</h2>

      <form onSubmit={handleSubmit} noValidate>
        <div className="campo">
          <label htmlFor="correo">Correo *</label>
          <input
            id="correo"
            name="correo"
            type="email"
            autoComplete="email"
            placeholder="tucorreo@ejemplo.com"
            value={correo}
            onChange={handleChange(setCorreo, 'correo')}
          />
          {errores.correo && <span className="error">{errores.correo}</span>}
        </div>

        <div className="campo">
          <label htmlFor="password">Contraseña *</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={handleChange(setPassword, 'password')}
          />
          {errores.password && <span className="error">{errores.password}</span>}
        </div>

        <div className="botones">
          <button
            type="submit"
            className="btn-primario"
            disabled={cargando}
          >
            {cargando ? 'Iniciando sesión...' : 'Iniciar sesión'}
          </button>
        </div>
      </form>

      {mensaje && (
        <div
          className={mensaje.tipo === 'exito' ? 'alerta exito' : 'alerta error'}
          role="alert"
        >
          {mensaje.texto}
        </div>
      )}
    </section>
  )
}
