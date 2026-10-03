/**
 * Validaciones de negocio compartidas (HU 01 - HU 03).
 * Se usan tanto en PublicarProducto como en EditarProducto.
 *
 * @param {object} datos - nombre, descripcion, precio, stock, categoria
 * @returns {object} errores - mapa campo -> mensaje
 */
export function validarProducto(datos) {
  const errores = {}

  if (!datos.nombre || !String(datos.nombre).trim()) {
    errores.nombre = 'El nombre es obligatorio'
  }

  if (!datos.descripcion || !String(datos.descripcion).trim()) {
    errores.descripcion = 'La descripción es obligatoria'
  }

  if (datos.precio === '' || datos.precio === null || datos.precio === undefined) {
    errores.precio = 'El precio es obligatorio'
  } else if (Number(datos.precio) <= 0) {
    errores.precio = 'El precio debe ser mayor a 0'
  }

  if (datos.stock === '' || datos.stock === null || datos.stock === undefined) {
    errores.stock = 'El stock es obligatorio'
  } else if (Number(datos.stock) < 0) {
    errores.stock = 'El stock no puede ser negativo'
  }

  if (!datos.categoria || !String(datos.categoria).trim()) {
    errores.categoria = 'La categoría es obligatoria'
  }

  return errores
}

export default validarProducto
