import { useState } from 'react'
import { crearProducto } from '../api/productos.js'

const FORM_INICIAL = {
  nombre: '',
  descripcion: '',
  precio: '',
  categoria: '',
  stock: '',
  estado: 'DISPONIBLE',
}

// Campos que el backend exige pero no se piden en la interfaz
const CAMPOS_OCULTOS = {
  imagenUrl: 'https://via.placeholder.com/300',
  vendedorId: 1,
}

export default function PublicarProducto() {
  const [formulario, setFormulario] = useState(FORM_INICIAL)
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null) // { tipo: 'exito' | 'error', texto }
  const [cargando, setCargando] = useState(false)

  // ---------- Validaciones en el cliente ----------
  const validar = (datos) => {
    const errs = {}

    if (!datos.nombre.trim()) {
      errs.nombre = 'El nombre es obligatorio'
    }
    if (!datos.descripcion.trim()) {
      errs.descripcion = 'La descripción es obligatoria'
    }
    if (datos.precio === '' || datos.precio === null) {
      errs.precio = 'El precio es obligatorio'
    } else if (Number(datos.precio) <= 0) {
      errs.precio = 'El precio debe ser mayor a 0'
    }
    if (datos.stock === '' || datos.stock === null) {
      errs.stock = 'El stock es obligatorio'
    } else if (Number(datos.stock) < 0) {
      errs.stock = 'El stock no puede ser negativo'
    }
    if (!datos.categoria.trim()) {
      errs.categoria = 'La categoría es obligatoria'
    }

    return errs
  }

  const handleChange = (e) => {
    const { name, value } = e.target
    setFormulario((prev) => ({ ...prev, [name]: value }))

    // limpia el error del campo mientras el usuario escribe
    setErrores((prev) => {
      if (!prev[name]) return prev
      const nuevo = { ...prev }
      delete nuevo[name]
      return nuevo
    })
    setMensaje(null)
  }

  // ---------- Cancelar ----------
  const handleCancelar = () => {
    setFormulario(FORM_INICIAL)
    setErrores({})
    setMensaje(null)
  }

  // ---------- Enviar al backend con Axios ----------
  const handleSubmit = async (e) => {
    e.preventDefault()
    setMensaje(null)

    const errs = validar(formulario)
    setErrores(errs)
    if (Object.keys(errs).length > 0) {
      setMensaje({ tipo: 'error', texto: 'Corrige los campos marcados antes de publicar.' })
      return
    }

    const payload = {
      ...CAMPOS_OCULTOS,
      ...formulario,
      precio: Number(formulario.precio),
      stock: Number(formulario.stock),
    }

    try {
      setCargando(true)
      const creado = await crearProducto(payload)
      setMensaje({
        tipo: 'exito',
        texto: `¡Producto registrado correctamente! (ID ${creado.id})`,
      })
      setFormulario(FORM_INICIAL)
    } catch (error) {
      // Respuesta de validación del backend -> 400 { messages: [...] }
      if (error.response?.data?.messages) {
        setMensaje({ tipo: 'error', texto: error.response.data.messages.join(' | ') })
      } else if (error.response) {
        setMensaje({
          tipo: 'error',
          texto: `Error ${error.response.status}: no se pudo registrar el producto.`,
        })
      } else {
        setMensaje({
          tipo: 'error',
          texto: 'No hay conexión con el backend. Verifica que esté corriendo en el puerto 8080.',
        })
      }
    } finally {
      setCargando(false)
    }
  }

  return (
    <section className="tarjeta">
      <h2>Publicar producto</h2>

      <form onSubmit={handleSubmit} noValidate>
        <div className="campo">
          <label htmlFor="nombre">Nombre *</label>
          <input
            id="nombre"
            name="nombre"
            type="text"
            value={formulario.nombre}
            onChange={handleChange}
            placeholder="Ej. Laptop Lenovo"
          />
          {errores.nombre && <span className="error">{errores.nombre}</span>}
        </div>

        <div className="campo">
          <label htmlFor="descripcion">Descripción *</label>
          <textarea
            id="descripcion"
            name="descripcion"
            rows="3"
            value={formulario.descripcion}
            onChange={handleChange}
            placeholder="Describe el producto"
          />
          {errores.descripcion && <span className="error">{errores.descripcion}</span>}
        </div>

        <div className="fila">
          <div className="campo">
            <label htmlFor="precio">Precio (S/) *</label>
            <input
              id="precio"
              name="precio"
              type="number"
              step="0.01"
              min="0"
              value={formulario.precio}
              onChange={handleChange}
              placeholder="0.00"
            />
            {errores.precio && <span className="error">{errores.precio}</span>}
          </div>

          <div className="campo">
            <label htmlFor="stock">Stock *</label>
            <input
              id="stock"
              name="stock"
              type="number"
              min="0"
              value={formulario.stock}
              onChange={handleChange}
              placeholder="0"
            />
            {errores.stock && <span className="error">{errores.stock}</span>}
          </div>
        </div>

        <div className="fila">
          <div className="campo">
            <label htmlFor="categoria">Categoría *</label>
            <input
              id="categoria"
              name="categoria"
              type="text"
              value={formulario.categoria}
              onChange={handleChange}
              placeholder="Ej. Computadoras"
            />
            {errores.categoria && <span className="error">{errores.categoria}</span>}
          </div>

          <div className="campo">
            <label htmlFor="estado">Estado</label>
            <select id="estado" name="estado" value={formulario.estado} onChange={handleChange}>
              <option value="DISPONIBLE">DISPONIBLE</option>
              <option value="AGOTADO">AGOTADO</option>
              <option value="INACTIVO">INACTIVO</option>
            </select>
          </div>
        </div>

        <div className="botones">
          <button type="submit" className="btn-primario" disabled={cargando}>
            {cargando ? 'Publicando…' : 'Publicar'}
          </button>
          <button type="button" className="btn-secundario" onClick={handleCancelar} disabled={cargando}>
            Cancelar
          </button>
        </div>
      </form>

      {mensaje && (
        <p className={mensaje.tipo === 'exito' ? 'alerta exito' : 'alerta error'} role="status">
          {mensaje.texto}
        </p>
      )}
    </section>
  )
}
