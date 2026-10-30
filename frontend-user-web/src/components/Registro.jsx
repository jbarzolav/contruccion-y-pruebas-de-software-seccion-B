import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login, registrar } from '../api/auth.js'
import { guardarSesion } from '../api/sesion.js'

/**
 * HU 26 - Registro de cuenta.
 *
 * Consume POST /api/auth/registro e inicia sesión tras crear la cuenta.
 * El rol lo asigna siempre el backend y la contraseña no se almacena.
 */
export default function Registro() {
  const [nombre, setNombre] = useState('')
  const [correo, setCorreo] = useState('')
  const [password, setPassword] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [cargando, setCargando] = useState(false)

  // Corta el doble envío de forma SÍNCRONA, igual que el login.
  const enviandoRef = useRef(false)
  const navigate = useNavigate()

  // HU 26 - Validación local antes de llamar al backend.
  const validar = () => {
    const errs = {}

    if (!nombre.trim()) {
      errs.nombre = 'El nombre es obligatorio'
    } else if (nombre.trim().length > 100) {
      errs.nombre = 'El nombre no puede superar 100 caracteres'
    }

    if (!correo.trim()) {
      errs.correo = 'El correo es obligatorio'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim())) {
      errs.correo = 'El correo debe tener un formato válido'
    }

    if (!password) {
      errs.password = 'La contraseña es obligatoria'
    } else if (password.length < 8) {
      errs.password = 'La contraseña debe tener al menos 8 caracteres'
    } else if (new TextEncoder().encode(password).length > 72) {
      errs.password = 'La contraseña supera el límite de 72 bytes.'
    }

    if (!confirmacion) {
      errs.confirmacion = 'La confirmación de contraseña es obligatoria'
    } else if (password !== confirmacion) {
      errs.confirmacion = 'Las contraseñas no coinciden.'
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

    if (enviandoRef.current) return

    const errs = validar()
    setErrores(errs)
    setMensaje(null)

    if (Object.keys(errs).length > 0) {
      return
    }

    enviandoRef.current = true
    setCargando(true)

    let cuentaCreada = false
    try {
      await registrar(nombre.trim(), correo.trim(), password, confirmacion)
      cuentaCreada = true
      const sesion = await login(correo.trim(), password)
      guardarSesion({ ...sesion, nombre: nombre.trim() })
      navigate('/')
    } catch (error) {
      if (cuentaCreada) {
        navigate('/login', { state: { mensaje: 'Cuenta creada correctamente. Inicia sesión para continuar.' } })
        return
      }

      const estado = error.response?.status
      const mensajes = error.response?.data?.messages

      if (estado === 409) {
        setMensaje({ tipo: 'error', texto: 'El correo ya está registrado.' })
      } else if (estado === 400) {
        setMensaje({
          tipo: 'error',
          texto: Array.isArray(mensajes) ? mensajes.join(' ') : 'Revisa los datos ingresados.',
        })
      } else {
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
      <h2>Crear cuenta</h2>

      <form onSubmit={handleSubmit} noValidate>
        <div className="campo">
          <label htmlFor="nombre">Nombre completo *</label>
          <input
            id="nombre"
            name="nombre"
            type="text"
            autoComplete="name"
            maxLength={100}
            placeholder="Tu nombre completo"
            value={nombre}
            onChange={handleChange(setNombre, 'nombre')}
          />
          {errores.nombre && <span className="error">{errores.nombre}</span>}
        </div>

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
            autoComplete="new-password"
            value={password}
            onChange={handleChange(setPassword, 'password')}
          />
          <small className="muted">Mínimo 8 caracteres.</small>
          {errores.password && <span className="error">{errores.password}</span>}
        </div>

        <div className="campo">
          <label htmlFor="confirmacion">Confirmar contraseña *</label>
          <input
            id="confirmacion"
            name="confirmacion"
            type="password"
            autoComplete="new-password"
            value={confirmacion}
            onChange={handleChange(setConfirmacion, 'confirmacion')}
          />
          {errores.confirmacion && <span className="error">{errores.confirmacion}</span>}
        </div>

        <div className="botones">
          <button
            type="submit"
            className="btn-primario"
            disabled={cargando}
          >
            {cargando ? 'Creando cuenta...' : 'Crear cuenta'}
          </button>
        </div>
      </form>

      <p><Link to="/login">Ya tengo una cuenta</Link></p>

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
