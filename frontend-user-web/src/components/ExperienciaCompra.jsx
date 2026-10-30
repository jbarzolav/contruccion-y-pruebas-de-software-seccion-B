import { useState } from 'react'
import { calificarPedido, resenarPedido } from '../api/productos.js'

export default function ExperienciaCompra({ pedido, onActualizado }) {
  const [valor, setValor] = useState(pedido.calificacion?.valor || 0)
  const [destino, setDestino] = useState('PRODUCTO')
  const [texto, setTexto] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const reseñaActual = (pedido.resenas || []).find((item) => item.destino === destino)

  async function guardarCalificacion() {
    if (!valor || guardando) return
    try {
      setGuardando(true); setError(''); setExito('')
      const actualizado = await calificarPedido(pedido.idPedido, valor)
      onActualizado?.(actualizado); setExito('Calificación guardada correctamente.')
    } catch (err) { setError(err?.response?.data?.messages?.[0] || 'No se pudo guardar la calificación.')
    } finally { setGuardando(false) }
  }

  async function guardarResena(evento) {
    evento.preventDefault()
    if (guardando || !texto.trim() || texto.length > 1000) return
    try {
      setGuardando(true); setError(''); setExito('')
      const actualizado = await resenarPedido(pedido.idPedido, destino, texto)
      onActualizado?.(actualizado); setTexto(''); setExito('Reseña publicada correctamente.')
    } catch (err) { setError(err?.response?.data?.messages?.[0] || 'No se pudo publicar la reseña.')
    } finally { setGuardando(false) }
  }

  return <section className="experiencia-compra tarjeta">
    <h3>Tu experiencia</h3>
    <div className="calificacion-compra">
      <strong>{pedido.calificacion ? 'Calificación guardada' : 'Calificar compra'}</strong>
      <div className="estrellas" role="radiogroup" aria-label="Calificación de 1 a 5">
        {[1, 2, 3, 4, 5].map((numero) => <button key={numero} type="button" disabled={!!pedido.calificacion || guardando}
          className={numero <= valor ? 'estrella activa' : 'estrella'} onClick={() => setValor(numero)}
          aria-label={`${numero} estrellas`}>★</button>)}
      </div>
      {!pedido.calificacion && <button type="button" className="btn-primario" disabled={!valor || guardando} onClick={guardarCalificacion}>
        {guardando ? 'Guardando...' : 'Guardar calificación'}
      </button>}
    </div>
    <form onSubmit={guardarResena} className="formulario-resena">
      <label htmlFor={`destino-${pedido.idPedido}`}>Reseña sobre</label>
      <select id={`destino-${pedido.idPedido}`} value={destino} disabled={!!reseñaActual || guardando} onChange={(e) => setDestino(e.target.value)}>
        <option value="PRODUCTO">Producto</option><option value="VENDEDOR">Vendedor</option>
      </select>
      {reseñaActual ? <blockquote>{reseñaActual.texto}</blockquote> : <>
        <textarea value={texto} maxLength={1000} disabled={guardando} onChange={(e) => setTexto(e.target.value)}
          placeholder="Cuéntanos tu experiencia (máximo 1000 caracteres)" rows="4" />
        <small>{texto.length} / 1000</small>
        <button className="btn-primario" disabled={guardando || !texto.trim()}>{guardando ? 'Publicando...' : 'Publicar reseña'}</button>
      </>}
    </form>
    {error && <div className="alerta error">{error}</div>}
    {exito && <div className="alerta exito">{exito}</div>}
  </section>
}
