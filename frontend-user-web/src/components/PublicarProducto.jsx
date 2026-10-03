import { useEffect, useState } from 'react'
import { crearProducto, subirImagenProducto } from '../api/productos.js'
import { validarProducto } from '../validation/productoValidaciones.js'

const FORM_INICIAL = {
  nombre: '',
  descripcion: '',
  precio: '',
  categoria: '',
  stock: '',
  estado: 'DISPONIBLE',
}

const CAMPOS_OCULTOS = {
  imagenUrl: 'https://via.placeholder.com/300',
  vendedorId: 1,
}

const FORMATOS_PERMITIDOS = ['image/jpeg', 'image/png']

export default function PublicarProducto({ onProductoCreado }) {
  const [formulario, setFormulario] = useState(FORM_INICIAL)
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [cargando, setCargando] = useState(false)

  // HU02 - Imagen seleccionada y vista previa
  const [imagen, setImagen] = useState(null)
  const [vistaPrevia, setVistaPrevia] = useState(null)

  // Libera la memoria del blob de la vista previa al cambiar o al desmontar
  useEffect(() => {
    return () => {
      if (vistaPrevia) {
        URL.revokeObjectURL(vistaPrevia)
      }
    }
  }, [vistaPrevia])

  const validar = (datos) => {
    const errs = validarProducto(datos)

    if (!imagen) {
      errs.imagen = 'La imagen es obligatoria'
    }

    return errs
  }

  const handleChange = (e) => {
    const { name, value } = e.target

    setFormulario((prev) => ({
      ...prev,
      [name]: value,
    }))

    setErrores((prev) => {
      if (!prev[name]) return prev

      const nuevo = { ...prev }
      delete nuevo[name]
      return nuevo
    })

    setMensaje(null)
  }

  // HU02 - Seleccionar imagen
  const handleImagen = (e) => {
    const archivo = e.target.files[0]

    if (!archivo) {
      return
    }

    if (!FORMATOS_PERMITIDOS.includes(archivo.type)) {
      setErrores((prev) => ({
        ...prev,
        imagen: 'Solo se permiten imágenes JPG, JPEG o PNG',
      }))
      return
    }

    setImagen(archivo)
    setVistaPrevia(URL.createObjectURL(archivo))

    setErrores((prev) => {
      const nuevo = { ...prev }
      delete nuevo.imagen
      return nuevo
    })
  }

  // HU02 - Quitar imagen (libera el blob)
  const quitarImagen = () => {
    if (vistaPrevia) {
      URL.revokeObjectURL(vistaPrevia)
    }
    setImagen(null)
    setVistaPrevia(null)
  }

  const handleCancelar = () => {
    if (vistaPrevia) {
      URL.revokeObjectURL(vistaPrevia)
    }
    setFormulario(FORM_INICIAL)
    setErrores({})
    setMensaje(null)
    setImagen(null)
    setVistaPrevia(null)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setMensaje(null)

    const errs = validar(formulario)
    setErrores(errs)

    if (Object.keys(errs).length > 0) {
      setMensaje({
        tipo: 'error',
        texto: 'Corrige los campos marcados antes de publicar.',
      })
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

      // HU01 - Crear producto
      const creado = await crearProducto(payload)

      // HU02 - Subir el archivo REAL de la imagen (multipart)
      await subirImagenProducto(creado.id, imagen, CAMPOS_OCULTOS.vendedorId)

      const productoFinalizado = {
        ...creado,
        imagenUrl: `producto: ${imagen.name}`,
      }

      setMensaje({
        tipo: 'exito',
        texto: `¡Producto y imagen registrados correctamente! (ID ${creado.id})`,
      })

      // HU03 - Ofrece el producto recién creado al formulario de edición
      onProductoCreado?.(productoFinalizado)

      setFormulario(FORM_INICIAL)
      quitarImagen()
    } catch (error) {
      if (error.response?.data?.messages) {
        setMensaje({
          tipo: 'error',
          texto: error.response.data.messages.join(' | '),
        })
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
            {errores.nombre && (
                <span className="error">{errores.nombre}</span>
            )}
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
            {errores.descripcion && (
                <span className="error">{errores.descripcion}</span>
            )}
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
              {errores.precio && (
                  <span className="error">{errores.precio}</span>
              )}
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
              {errores.stock && (
                  <span className="error">{errores.stock}</span>
              )}
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
              {errores.categoria && (
                  <span className="error">{errores.categoria}</span>
              )}
            </div>

            <div className="campo">
              <label htmlFor="estado">Estado</label>
              <select
                  id="estado"
                  name="estado"
                  value={formulario.estado}
                  onChange={handleChange}
              >
                <option value="DISPONIBLE">DISPONIBLE</option>
                <option value="AGOTADO">AGOTADO</option>
                <option value="INACTIVO">INACTIVO</option>
              </select>
            </div>

          </div>

          {/* HU02 - Selector de imagen */}
          <div className="campo">
            <label htmlFor="imagen">Imagen del producto *</label>

            <input
                id="imagen"
                type="file"
                accept=".jpg,.jpeg,.png"
                onChange={handleImagen}
            />

            {errores.imagen && (
                <span className="error">{errores.imagen}</span>
            )}

            {vistaPrevia && (
                <div>
                  <p>Vista previa:</p>

                  <img
                      src={vistaPrevia}
                      alt="Vista previa del producto"
                      width="200"
                  />

                  <br />

                  <button
                      type="button"
                      className="btn-secundario"
                      onClick={quitarImagen}
                  >
                    Quitar imagen
                  </button>
                </div>
            )}
          </div>

          <div className="botones">
            <button
                type="submit"
                className="btn-primario"
                disabled={cargando}
            >
              {cargando ? 'Publicando…' : 'Publicar'}
            </button>

            <button
                type="button"
                className="btn-secundario"
                onClick={handleCancelar}
                disabled={cargando}
            >
              Cancelar
            </button>
          </div>

        </form>

        {mensaje && (
            <p
                className={
                  mensaje.tipo === 'exito'
                      ? 'alerta exito'
                      : 'alerta error'
                }
                role="status"
            >
              {mensaje.texto}
            </p>
        )}

      </section>
  )
}
