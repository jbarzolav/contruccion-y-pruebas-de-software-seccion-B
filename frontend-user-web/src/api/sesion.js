import { useEffect, useState } from 'react'

// HU 23 - Sesión compartida por las pantallas; el backend valida los permisos.
export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:9000'
export function leerSesion() {
  const token = localStorage.getItem('pulgatec_token')
  if (!token) return null
  let permisos = []
  try { permisos = JSON.parse(localStorage.getItem('pulgatec_permisos') || '[]') } catch { /* Sesión antigua. */ }
  return { token, id: Number(localStorage.getItem('pulgatec_usuario_id')),
    correo: localStorage.getItem('pulgatec_usuario_correo'),
    nombre: localStorage.getItem('pulgatec_usuario_nombre'),
    rol: localStorage.getItem('pulgatec_rol'), permisos: Array.isArray(permisos) ? permisos : [] }
}
export function guardarSesion(usuario) {
  const tokenAnterior = localStorage.getItem('pulgatec_token')
  const nuevaSesion = usuario.token && usuario.token !== tokenAnterior
  if (usuario.token) localStorage.setItem('pulgatec_token', usuario.token)
  localStorage.setItem('pulgatec_usuario_id', String(usuario.id))
  localStorage.setItem('pulgatec_usuario_correo', usuario.correo)
  if (usuario.nombre != null) localStorage.setItem('pulgatec_usuario_nombre', usuario.nombre)
  else if (nuevaSesion) localStorage.removeItem('pulgatec_usuario_nombre')
  localStorage.setItem('pulgatec_rol', usuario.rol)
  localStorage.setItem('pulgatec_permisos', JSON.stringify(usuario.permisos || []))
  window.dispatchEvent(new Event('pulgatec-sesion'))
}
export function limpiarSesion() {
  for (const clave of ['token', 'usuario_id', 'usuario_correo', 'usuario_nombre', 'rol', 'permisos']) {
    localStorage.removeItem(`pulgatec_${clave}`)
  }
  window.dispatchEvent(new Event('pulgatec-sesion'))
}
export function useSesion() {
  const [sesion, setSesion] = useState(leerSesion)
  useEffect(() => {
    const actualizar = () => setSesion(leerSesion())
    window.addEventListener('pulgatec-sesion', actualizar)
    window.addEventListener('storage', actualizar)
    return () => {
      window.removeEventListener('pulgatec-sesion', actualizar)
      window.removeEventListener('storage', actualizar)
    }
  }, [])
  return sesion
}
export function tienePermiso(sesion, permiso) { return !!sesion?.permisos.includes(permiso) }
export function usuarioActualId() { return leerSesion()?.id }

// HU 23 - Token y avisos centralizados, sin alterar errores de negocio como Premium.
export function configurarSesion(api) {
  api.interceptors.request.use((config) => {
    const token = leerSesion()?.token
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  })
  api.interceptors.response.use((respuesta) => respuesta, (error) => {
    if (error.response?.status === 401 ||
        (error.response?.status === 403 && error.response.data?.error === 'Autorización')) {
      // Una respuesta de una cuenta anterior no debe borrar una sesión nueva.
      if (error.config?.headers?.Authorization === `Bearer ${leerSesion()?.token}`) {
        if (error.response.status === 401) limpiarSesion()
        window.dispatchEvent(new CustomEvent('pulgatec-autorizacion', { detail: error.response.status }))
      }
    }
    return Promise.reject(error)
  })
  return api
}
