import React, { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  cambiarRol, cerrarSesion, iniciarSesion, listarAuditoria, listarUsuarios,
  mensajeError, restaurarSesion,
} from './api.js'
import './styles.css'

const ROLES = ['CLIENTE', 'VENDEDOR', 'ADMINISTRADOR']

function Login({ onLogin }) {
  const [correo, setCorreo] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('')
    try { onLogin(await iniciarSesion(correo, password)) }
    catch (err) { setError(mensajeError(err)) }
    finally { setBusy(false); setPassword('') }
  }
  return <main className="login-shell"><form className="login-card" onSubmit={submit}>
    <div className="brand-mark">P</div><p className="eyebrow">PULGATEC · CONTROL CENTRAL</p>
    <h1>Administración</h1><p className="muted">Ingresa con tu cuenta PulgaTec de administrador.</p>
    <label>Correo<input autoComplete="username" type="email" required value={correo} onChange={e => setCorreo(e.target.value)} /></label>
    <label>Contraseña<input autoComplete="current-password" type="password" required value={password} onChange={e => setPassword(e.target.value)} /></label>
    {error && <Notice type="error">{error}</Notice>}
    <button className="primary full" disabled={busy}>{busy ? 'Validando…' : 'Ingresar al panel'}</button>
    <small>La autenticación y los permisos se validan en el servicio de usuarios.</small>
  </form></main>
}

function Notice({ type = 'info', children }) { return <div className={`notice ${type}`} role="status">{children}</div> }

function RolesView() {
  const [users, setUsers] = useState([])
  const [roles, setRoles] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(null)
  async function load() {
    setLoading(true); setError('')
    try { const data = await listarUsuarios(); setUsers(data); setRoles(Object.fromEntries(data.map(u => [u.id, u.rol]))) }
    catch (err) { setError(mensajeError(err)) }
    finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])
  async function update(user) {
    setSaving(user.id); setMessage(''); setError('')
    try {
      const updated = await cambiarRol(user.id, roles[user.id])
      setUsers(current => current.map(item => item.id === updated.id ? updated : item))
      setRoles(current => ({ ...current, [updated.id]: updated.rol }))
      setMessage(`Rol actualizado para ${updated.correo}.`)
    } catch (err) { setError(mensajeError(err)) }
    finally { setSaving(null) }
  }
  return <section><div className="section-heading"><div><p className="eyebrow">HU23 · ACCESOS</p><h2>Gestión de usuarios y roles</h2><p className="muted">Asigna el rol que define los permisos de cada cuenta.</p></div><button className="secondary" onClick={load}>Actualizar lista</button></div>
    {error && <Notice type="error">{error}</Notice>}{message && <Notice type="success">{message}</Notice>}
    {loading ? <div className="loading">Cargando usuarios…</div> : users.length === 0 ? <div className="empty">No hay usuarios registrados.</div> :
      <div className="table-wrap"><table><thead><tr><th>Usuario</th><th>Correo</th><th>Rol actual</th><th>Nuevo rol</th><th></th></tr></thead><tbody>
        {users.map(user => <tr key={user.id}><td><span className="avatar">{user.correo?.[0]?.toUpperCase()}</span>Cuenta #{user.id}</td><td>{user.correo}</td><td><span className="role-pill">{user.rol}</span></td><td><select aria-label={`Nuevo rol de ${user.correo}`} value={roles[user.id] || user.rol} onChange={e => setRoles(current => ({ ...current, [user.id]: e.target.value }))}>{ROLES.map(role => <option key={role}>{role}</option>)}</select></td><td><button className="primary small" disabled={saving === user.id || roles[user.id] === user.rol} onClick={() => update(user)}>{saving === user.id ? 'Guardando…' : 'Guardar'}</button></td></tr>)}
      </tbody></table></div>}
  </section>
}

function AuditView() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    listarAuditoria(page).then(result => { if (active) setData(result) })
      .catch(err => { if (active) setError(mensajeError(err)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page])
  return <section><div className="section-heading"><div><p className="eyebrow">HU24 · TRAZABILIDAD</p><h2>Auditoría del sistema</h2><p className="muted">Eventos de seguridad y cambios administrativos recientes.</p></div></div>
    {error && <Notice type="error">{error}</Notice>}
    {loading ? <div className="loading">Cargando auditoría…</div> : data?.contenido?.length ? <>
      <div className="table-wrap"><table><thead><tr><th>Acción</th><th>Usuario</th><th>Fecha</th></tr></thead><tbody>{data.contenido.map(item => <tr key={item.id}><td><span className="event-dot" />{item.accion}</td><td>{item.usuario}</td><td>{new Date(item.fecha).toLocaleString('es-PE')}</td></tr>)}</tbody></table></div>
      <div className="pagination"><span>{data.totalElementos} eventos · Página {page + 1} de {Math.max(data.totalPaginas, 1)}</span><div><button className="secondary" disabled={page <= 0} onClick={() => setPage(p => p - 1)}>Anterior</button><button className="secondary" disabled={page + 1 >= data.totalPaginas} onClick={() => setPage(p => p + 1)}>Siguiente</button></div></div>
    </> : <div className="empty"><div className="empty-icon">◎</div>No hay registros de auditoría.</div>}
  </section>
}

function App() {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(false)
  const [tab, setTab] = useState('roles')
  const [sessionError, setSessionError] = useState('')
  useEffect(() => { restaurarSesion().then(setUser).catch(err => setSessionError(mensajeError(err))).finally(() => setReady(true)) }, [])
  async function logout() { try { await cerrarSesion() } finally { setUser(null) } }
  if (!ready) return <div className="loading boot">Comprobando sesión…</div>
  if (!user) return <><Login onLogin={setUser} />{sessionError && <div className="session-error"><Notice type="error">{sessionError}</Notice></div>}</>
  return <div className="app-shell"><aside className="sidebar"><div className="brand"><span className="brand-mark">P</span><span>PulgaTec<small>ADMIN CONSOLE</small></span></div>
    <p className="nav-label">ESPACIO DE TRABAJO</p><button className={`nav-item ${tab === 'roles' ? 'active' : ''}`} onClick={() => setTab('roles')}><span>♙</span> Usuarios y roles</button><button className={`nav-item ${tab === 'audit' ? 'active' : ''}`} onClick={() => setTab('audit')}><span>◷</span> Auditoría</button>
    <div className="sidebar-bottom"><div className="user-mini"><div className="avatar admin-avatar">{user.correo?.[0]?.toUpperCase()}</div><div className="user-mini-info"><strong>{user.correo}</strong><small>ADMINISTRADOR</small></div><button className="logout" title="Cerrar sesión" onClick={logout}>↗</button></div></div>
  </aside><main className="main-area"><header className="topbar"><span>Panel administrativo <span className="slash">/</span> {tab === 'roles' ? 'Usuarios y roles' : 'Auditoría'}</span><span className="secure"><i /> Sesión segura</span></header><div className="content">{tab === 'roles' ? <RolesView /> : <AuditView />}</div></main></div>
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>)
