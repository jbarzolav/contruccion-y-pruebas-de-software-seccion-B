import { useEffect, useState } from 'react'
import { listarAuditoria } from '../api/auth.js'

const TAMANO_PAGINA = 20

function fechaVisible(valor) {
  const fecha = new Date(valor)
  return Number.isNaN(fecha.getTime()) ? 'Fecha no disponible' : new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(fecha)
}

/** HU24 - Historial paginado con campos explícitos; no representa entidades ni secretos. */
export default function AdministrarAuditoria() {
  const [pagina, setPagina] = useState(0)
  const [resultado, setResultado] = useState(null)
  const [estado, setEstado] = useState('loading')
  const [error, setError] = useState('')

  useEffect(() => {
    let vigente = true
    setEstado('loading')
    setError('')
    listarAuditoria(pagina, TAMANO_PAGINA)
      .then((datos) => { if (vigente) { setResultado(datos); setEstado('success') } })
      .catch((fallo) => {
        if (!vigente) return
        const codigo = fallo.response?.status
        setError(codigo === 403
          ? 'No tienes permiso para consultar la auditoría.'
          : fallo.response?.data?.messages?.[0] || 'No se pudo cargar el historial de auditoría.')
        setEstado('error')
      })
    return () => { vigente = false }
  }, [pagina])

  return (
    <section className="tarjeta" aria-labelledby="titulo-auditoria">
      <h2 id="titulo-auditoria">Auditoría del sistema</h2>
      {estado === 'loading' && <p role="status">Cargando registros...</p>}
      {estado === 'error' && <div className="alerta error" role="alert">{error}</div>}
      {estado === 'success' && resultado?.contenido?.length === 0 && <p>No hay registros de auditoría.</p>}
      {estado === 'success' && resultado?.contenido?.length > 0 && (
        <>
          <div className="tabla-contenedor">
            <table>
              <thead><tr><th scope="col">Acción</th><th scope="col">Usuario</th><th scope="col">Fecha</th></tr></thead>
              <tbody>
                {resultado.contenido.map((registro) => (
                  <tr key={registro.id}>
                    <td>{registro.accion}</td><td>{registro.usuario}</td><td>{fechaVisible(registro.fecha)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="producto-acciones" aria-label="Paginación de auditoría">
            <span>{resultado.totalElementos} registros · página {resultado.pagina + 1} de {Math.max(resultado.totalPaginas, 1)}</span>
            <button className="btn-secundario" type="button" disabled={pagina === 0 || estado === 'loading'}
              onClick={() => setPagina((actual) => Math.max(0, actual - 1))}>Anterior</button>
            <button className="btn-secundario" type="button" disabled={pagina + 1 >= resultado.totalPaginas || estado === 'loading'}
              onClick={() => setPagina((actual) => actual + 1)}>Siguiente</button>
          </div>
        </>
      )}
    </section>
  )
}
