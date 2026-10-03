import { useEffect, useRef, useState } from 'react'
import { actualizarProducto, retirarProducto } from '../api/productos.js'
import { validarProducto } from '../validation/productoValidaciones.js'

const FORM_INICIAL = {
  nombre: '',
  descripcion: '',
  precio: '',
  categoria: '',
  stock: '',
  estado: 'DISPONIBLE',
}

const VENDEDOR_ACTUAL = 1

/**
 * HU 03 - Formulario de edición de productos.
 *
 * @param {object}   producto   - Producto a editar (se precarga en los campos)
 * @param {function} onGuardado - Se ejecuta con el producto actualizado
 * @param {function} onCancelar - Se ejecuta al cancelar la edición
 */
export default function EditarProducto({ producto, onGuardado, onCancelar }) {
  const [formulario, setFormulario] = useState(FORM_INICIAL)
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [cargando, setCargando] = useState(false)

  // HU 04 - Retiro de la publicación
  const [confirmarRetiro, setConfirmarRetiro] = useState(false)
  const [retirando, setRetirando] = useState(false)

  const idAnteriorRef = useRef(null)

  // Precarga de los datos actuales del producto
  useEffect(() => {
    if (!producto) return

    // El mensaje solo se limpia cuando cambia de producto (no al retirar)
    if (idAnteriorRef.current !== producto.id) {
      setMensaje(null)
    }
    idAnteriorRef.current = producto.id

    setFormulario({
      nombre: producto.nombre ?? '',
      descripcion: producto.descripcion ?? '',
      precio: producto.precio ?? '',
      categoria: producto.categoria ?? '',
      stock: producto.stock ?? '',
      estado: producto.estado ?? 'DISPONIBLE',
    })
    setErrores({})
    setConfirmarRetiro(false)
  }, [producto])

  const handleChange = (e) => {
    const { name, value } = e.target

    setFormulario((prev) => ({ ...prev, [name]: value }))

    setErrores((prev) => {
      if (!prev[name]) return prev
      const nuevo = { ...prev }
      delete nuevo[name]
      return nuevo
    })

    setMensaje(null)
  }

  const handleCancelar = () => {
    setErrores({})
    setMensaje(null)
    onCancelar?.()
  }

  // HU 04 - Retirar publicación (baja lógica, sin delete físico)
  const retirarPublicacion = async () => {
    setConfirmarRetiro(false)
    setMensaje(null)

    try {
      setRetirando(true)

      const actualizado = await retirarProducto(
        producto.id,
        producto.vendedorId ?? VENDEDOR_ACTUAL
      )

      setMensaje({
        tipo: 'exito',
        texto: `Publicación retirada (estado ${actualizado.estado}). El producto no se eliminó: es una baja lógica.`,
      })

      onGuardado?.(actualizado)
    } catch (error) {
      if (error.response?.data?.messages) {
        setMensaje({
          tipo: 'error',
          texto: error.response.data.messages.join(' | '),
        })
      } else if (error.response) {
        const estado = error.response.status
        const texto =
          estado === 404
            ? 'El producto ya no existe en el backend.'
            : estado === 403
              ? 'No tienes permiso para retirar este producto.'
              : `Error ${estado}: no se pudo retirar la publicación.`
        setMensaje({ tipo: 'error', texto })
      } else {
        setMensaje({
          tipo: 'error',
          texto: 'No hay conexión con el backend. Verifica que esté corriendo en el puerto 8080.',
        })
      }
    } finally {
      setRetirando(false)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setMensaje(null)

    // Mismas validaciones de negocio que PublicarProducto
    const errs = validarProducto(formulario)
    setErrores(errs)

    if (Object.keys(errs).length > 0) {
      setMensaje({
        tipo: 'error',
        texto: 'Corrige los campos marcados antes de guardar.',
      })
      return
    }

    const payload = {
      ...formulario,
      precio: Number(formulario.precio),
      stock: Number(formulario.stock),
      // Campos no editables que el backend espera en el cuerpo
      imagenUrl: producto.imagenUrl,
      vendedorId: producto.vendedorId ?? VENDEDOR_ACTUAL,
    }

    try {
      setCargando(true)

      const actualizado = await actualizarProducto(
        producto.id,
        payload,
        producto.vendedorId ?? VENDEDOR_ACTUAL
      )

      setMensaje({
        tipo: 'exito',
        texto: `¡Producto actualizado correctamente! (ID ${actualizado.id})`,
      })

      onGuardado?.(actualizado)
    } catch (error) {
      if (error.response?.data?.messages) {
        setMensaje({
          tipo: 'error',
          texto: error.response.data.messages.join(' | '),
        })
      } else if (error.response) {
        const estado = error.response.status
        const texto =
          estado === 404
            ? 'El producto ya no existe en el backend.'
            : estado === 403
              ? 'No tienes permiso para editar este producto.'
              : `Error ${estado}: no se pudo actualizar el producto.`
        setMensaje({ tipo: 'error', texto })
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

  if (!producto) {
    return (
      <section className="tarjeta">
        <h2>Editar producto</h2>
        <p className="alerta">
          Publica un producto y automáticamente aparecerá aquí para editarlo.
        </p>
      </section>
    )
  }

  const estaRetirado = producto.estado === 'RETIRADO'

  return (
    <section className="tarjeta">
      <h2>
        Editar producto (ID {producto.id}){' '}
        {estaRetirado && <span className="badge-retirado">RETIRADO</span>}
      </h2>

      <form onSubmit={handleSubmit} noValidate>

        <div className="campo">
          <label htmlFor="ed-nombre">Nombre *</label>
          <input
            id="ed-nombre"
            name="nombre"
            type="text"
            value={formulario.nombre}
            onChange={handleChange}
          />
          {errores.nombre && <span className="error">{errores.nombre}</span>}
        </div>

        <div className="campo">
          <label htmlFor="ed-descripcion">Descripción *</label>
          <textarea
            id="ed-descripcion"
            name="descripcion"
            rows="3"
            value={formulario.descripcion}
            onChange={handleChange}
          />
          {errores.descripcion && (
            <span className="error">{errores.descripcion}</span>
          )}
        </div>

        <div className="fila">
          <div className="campo">
            <label htmlFor="ed-precio">Precio (S/) *</label>
            <input
              id="ed-precio"
              name="precio"
              type="number"
              step="0.01"
              min="0"
              value={formulario.precio}
              onChange={handleChange}
            />
            {errores.precio && <span className="error">{errores.precio}</span>}
          </div>

          <div className="campo">
            <label htmlFor="ed-stock">Stock *</label>
            <input
              id="ed-stock"
              name="stock"
              type="number"
              min="0"
              value={formulario.stock}
              onChange={handleChange}
            />
            {errores.stock && <span className="error">{errores.stock}</span>}
          </div>
        </div>

        <div className="fila">
          <div className="campo">
            <label htmlFor="ed-categoria">Categoría *</label>
            <input
              id="ed-categoria"
              name="categoria"
              type="text"
              value={formulario.categoria}
              onChange={handleChange}
            />
            {errores.categoria && (
              <span className="error">{errores.categoria}</span>
            )}
          </div>

          <div className="campo">
            <label htmlFor="ed-estado">Estado</label>
            <select
              id="ed-estado"
              name="estado"
              value={formulario.estado}
              onChange={handleChange}
            >
              <option value="DISPONIBLE">DISPONIBLE</option>
              <option value="AGOTADO">AGOTADO</option>
              <option value="INACTIVO">INACTIVO</option>
              <option value="RETIRADO">RETIRADO</option>
            </select>
          </div>
        </div>

        <div className="botones">
          <button type="submit" className="btn-primario" disabled={cargando}>
            {cargando ? 'Guardando…' : 'Guardar cambios'}
          </button>

          <button
            type="button"
            className="btn-secundario"
            onClick={handleCancelar}
            disabled={cargando}
          >
            Cancelar
          </button>

          {/* HU 04 - Retirar publicación */}
          <button
            type="button"
            className="btn-peligro"
            onClick={() => setConfirmarRetiro(true)}
            disabled={cargando || retirando || estaRetirado}
          >
            {estaRetirado ? 'Ya retirado' : 'Retirar publicación'}
          </button>
        </div>

      </form>

      {mensaje && (
        <p
          className={mensaje.tipo === 'exito' ? 'alerta exito' : 'alerta error'}
          role="status"
        >
          {mensaje.texto}
        </p>
      )}

      {/* HU 04 - Diálogo de confirmación antes de retirar */}
      {confirmarRetiro && (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal">
            <h3>¿Retirar esta publicación?</h3>
            <p>
              El producto <strong>{producto.nombre}</strong> (ID {producto.id})
              quedará con estado <strong>RETIRADO</strong>. No se eliminará de
              la base de datos: es una <strong>baja lógica</strong>.
            </p>

            <div className="botones">
              <button
                type="button"
                className="btn-secundario"
                onClick={() => setConfirmarRetiro(false)}
                disabled={retirando}
              >
                Cancelar
              </button>

              <button
                type="button"
                className="btn-peligro"
                onClick={retirarPublicacion}
                disabled={retirando}
              >
                {retirando ? 'Retirando…' : 'Sí, retirar'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
