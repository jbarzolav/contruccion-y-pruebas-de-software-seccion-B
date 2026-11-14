import { EVENTO_LECTURA_NOTIFICACIONES, listarBandeja, listarMensajesPendientes } from './api/notificaciones.js'
import { useEffect, useState } from 'react'
import { HashRouter, Link, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import Inicio from './components/Inicio.jsx'
import PublicarProducto from './components/PublicarProducto.jsx'
import EditarProducto from './components/EditarProducto.jsx'
import MisProductos from './components/MisProductos.jsx'
import DetalleProducto from './components/DetalleProducto.jsx'
import ConfirmarCompra from './components/ConfirmarCompra.jsx'
import PlanPremium from './components/PlanPremium.jsx'
import ProductoModal from './components/ProductoModal.jsx'
import CarritoDrawer from './components/CarritoDrawer.jsx'
import ChatbotFab from './components/ChatbotFab.jsx'
import Login from './components/Login.jsx'
import Registro from './components/Registro.jsx'
import RecuperarAcceso from './components/RecuperarAcceso.jsx'
import Perfil from './components/Perfil.jsx'
import Favoritos from './components/Favoritos.jsx'
import Conversaciones from './components/Conversaciones.jsx'
import Pedidos from './components/Pedidos.jsx'
import PedidosRecibidos from './components/PedidosRecibidos.jsx'
import DetallePedido from './components/DetallePedido.jsx'
import BandejaNotificaciones from './components/BandejaNotificaciones.jsx'
import MetricasVentas from './components/MetricasVentas.jsx'
import UmbralesStock from './components/UmbralesStock.jsx'
import { obtenerPerfil, obtenerSesion } from './api/auth.js'
import { cerrarSesion as solicitarCierre } from './api/auth.js'
import { guardarSesion, limpiarSesion, leerSesion, tienePermiso, useSesion } from './api/sesion.js'
import { obtenerCarrito } from './api/productos.js'

// HU 23 - Las rutas protegidas también comprueban el permiso al abrirlas directamente.
function AccesoProtegido({ permiso, children }) {
    const sesion = useSesion()
    if (!sesion) return <section className="tarjeta"><p>Debes iniciar sesión para continuar.</p><Link to="/login">Iniciar sesión</Link></section>
    if (!sesion.rol) return <p>Validando sesión...</p>
    if (permiso && !tienePermiso(sesion, permiso)) return <section className="tarjeta"><p>No tienes permiso para acceder a esta función.</p><Link to="/">Volver al inicio</Link></section>
    return children
}

function AccesoAutenticado({ children }) {
    const sesion = useSesion()
    if (!sesion) return <section className="tarjeta"><p>Debes iniciar sesión para continuar.</p><Link to="/login">Iniciar sesión</Link></section>
    return children
}

function AccesoVendedor({ children }) {
    const sesion = useSesion()
    if (!sesion) return <section className="tarjeta"><p>Debes iniciar sesión para continuar.</p><Link to="/login">Iniciar sesión</Link></section>
    if (sesion.rol !== 'VENDEDOR') return <section className="tarjeta"><p>Esta sección es solo para vendedores.</p><Link to="/">Volver al inicio</Link></section>
    return children
}

/**
 * Los avisos apuntan a un ancla dentro de la pantalla (por ejemplo
 * /pedidos-recibidos#pedido-12). El router cambia la URL, pero por sí solo no
 * desplaza la vista: este componente lleva la pantalla hasta el elemento
 * indicado y lo resalta para que quede claro a dónde se fue.
 */
function DesplazarAlAncla() {
    const location = useLocation()
    useEffect(() => {
        const ancla = location.hash ? decodeURIComponent(location.hash.slice(1)) : ''
        if (!ancla) return undefined
        let pendientes = 0
        let temporizador = null
        function buscar() {
            const destino = document.getElementById(ancla)
            if (destino) {
                destino.scrollIntoView({ behavior: 'smooth', block: 'center' })
                destino.classList.add('ancla-destacada')
                window.setTimeout(() => destino.classList.remove('ancla-destacada'), 2600)
                return
            }
            // El destino puede pintarse después de cargar los datos: se reintenta.
            pendientes += 1
            if (pendientes < 15) temporizador = window.setTimeout(buscar, 120)
        }
        buscar()
        return () => { if (temporizador) window.clearTimeout(temporizador) }
    }, [location.pathname, location.hash, location.key])
    return null
}

/**
 * Rediseño UI/UX - PulgaTec.
 * Header con 3 accesos (Inicio · Mis Publicaciones · Plan Premium),
 * carrito como panel lateral, publicar/editar en modal y chatbot flotante.
 */
export default function App() {
    const sesion = useSesion()
    const puedeComprar = tienePermiso(sesion, 'COMPRAR')
    const puedePublicar = tienePermiso(sesion, 'GESTIONAR_PRODUCTOS')
    const [avisoSesion, setAvisoSesion] = useState('')
    const [cerrandoSesion, setCerrandoSesion] = useState(false)

    const puedeConsultar = ['CLIENTE', 'VENDEDOR'].includes(sesion?.rol)
    const tokenNotificaciones = sesion?.token
    const [tokenConAvisosPendientes, setTokenConAvisosPendientes] = useState(null)

    useEffect(() => {
        setTokenConAvisosPendientes(null)
        if (!tokenNotificaciones) return

        let vigente = true
        let consulta = 0

        async function actualizarAvisosPendientes() {
            const actual = ++consulta
            try {
                const { pendientes } = await listarBandeja()
                if (vigente && actual === consulta) {
                    setTokenConAvisosPendientes(pendientes > 0 ? tokenNotificaciones : null)
                }
            } catch {
                // Conserva el último estado conocido hasta la siguiente consulta.
            }
        }

        actualizarAvisosPendientes()
        const intervalo = window.setInterval(actualizarAvisosPendientes, 15000)
        window.addEventListener(EVENTO_LECTURA_NOTIFICACIONES, actualizarAvisosPendientes)
        return () => {
            vigente = false
            window.clearInterval(intervalo)
            window.removeEventListener(EVENTO_LECTURA_NOTIFICACIONES, actualizarAvisosPendientes)
        }
    }, [tokenNotificaciones])

    const [mensajesPendientes, setMensajesPendientes] = useState(null)
    const pendientesConsulta = mensajesPendientes?.token === tokenNotificaciones
        ? mensajesPendientes?.avisos ?? [] : []

    useEffect(() => {
        if (!puedeConsultar || !tokenNotificaciones) return
        let vigente = true
        let consulta = 0

        async function actualizarMensajesPendientes() {
            const actual = ++consulta
            try {
                const avisos = await listarMensajesPendientes()
                if (vigente && actual === consulta) {
                    setMensajesPendientes({ token: tokenNotificaciones, avisos })
                }
            } catch {
                // Conserva el último conteo hasta la próxima actualización.
            }
        }

        actualizarMensajesPendientes()
        const intervalo = window.setInterval(actualizarMensajesPendientes, 15000)
        window.addEventListener(EVENTO_LECTURA_NOTIFICACIONES, actualizarMensajesPendientes)
        return () => {
            vigente = false
            window.clearInterval(intervalo)
            window.removeEventListener(EVENTO_LECTURA_NOTIFICACIONES, actualizarMensajesPendientes)
        }
    }, [puedeConsultar, tokenNotificaciones])

    async function cerrarSesion() {
        if (!sesion || cerrandoSesion || !window.confirm('¿Deseas cerrar la sesión?')) return
        setCerrandoSesion(true)
        let falloServidor = false
        try {
            await solicitarCierre(sesion.token)
        } catch {
            falloServidor = true
        } finally {
            limpiarSesion()
            setCerrandoSesion(false)
            window.location.hash = '#/login'
        }
        if (falloServidor) window.alert('Se cerró la sesión en este dispositivo, pero no se pudo confirmar la revocación en el servidor.')
    }

    // HU 23 - Recuperar el rol actual, incluidas sesiones creadas antes de esta historia.
    useEffect(() => {
        let vigente = true
        if (sesion?.token) setAvisoSesion('')
        async function actualizarSesion() {
            const token = leerSesion()?.token
            if (!token) return
            try {
                const actual = await obtenerSesion()
                let nombre = leerSesion()?.nombre
                try {
                    nombre = (await obtenerPerfil()).nombre
                } catch {
                    // La navegación puede conservarse aunque el perfil no responda.
                }
                if (vigente && leerSesion()?.token === token) {
                    guardarSesion({ ...actual, nombre })
                }
            } catch (error) {
                if (vigente && !error.response) setAvisoSesion('No se pudo verificar la sesión. Revisa la conexión.')
            }
        }
        function manejarError(evento) {
            setAvisoSesion(evento.detail === 401 ? 'Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.' : 'No tienes permiso para realizar esta acción.')
            if (evento.detail === 403) actualizarSesion()
        }
        actualizarSesion()
        window.addEventListener('focus', actualizarSesion)
        window.addEventListener('pulgatec-autorizacion', manejarError)
        return () => {
            vigente = false
            window.removeEventListener('focus', actualizarSesion)
            window.removeEventListener('pulgatec-autorizacion', manejarError)
        }
    }, [sesion?.token])
    const [productoEnEdicion, setProductoEnEdicion] = useState(null)
    const [confirmarRetiroInicial, setConfirmarRetiroInicial] = useState(false)
    const [publicando, setPublicando] = useState(false)
    const [drawerCarritoAbierto, setDrawerCarritoAbierto] = useState(false)
    const [actualizarMisProductos, setActualizarMisProductos] = useState(0)

    // HU 11 - total de unidades en el carrito (badge del header)
    const [itemsEnCarrito, setItemsEnCarrito] = useState(0)

    useEffect(() => {
        recargarCarrito()
        setPublicando(false)
        setProductoEnEdicion(null)
        setDrawerCarritoAbierto(false)
    }, [sesion?.id, sesion?.token, puedeComprar, puedePublicar])

    // GET /api/carrito/items → suma de unidades para el badge
    const recargarCarrito = async () => {
        if (!puedeComprar) { setItemsEnCarrito(0); return }
        try {
            const items = await obtenerCarrito()
            const total = items.reduce((suma, item) => suma + item.cantidad, 0)
            setItemsEnCarrito(total)
        } catch {
            // Backend apagado: el badge queda en 0 sin romper la vista
            setItemsEnCarrito(0)
        }
    }

    // ------------------------------------------------------------
    // Modal de publicar (HU 01/02) y editar (HU 03)
    // ------------------------------------------------------------
    const modalAbierto = publicando || productoEnEdicion !== null

    function abrirPublicacion() {
        setConfirmarRetiroInicial(false)
        setProductoEnEdicion(null)
        setPublicando(true)
    }

    function abrirEdicion(producto, retirar = false) {
        setConfirmarRetiroInicial(retirar)
        setPublicando(false)
        setProductoEnEdicion(producto)
    }

    function cerrarModal() {
        setPublicando(false)
        setProductoEnEdicion(null)
        setConfirmarRetiroInicial(false)
    }

    const refrescarPublicaciones = () => {
        setActualizarMisProductos((valor) => valor + 1)
    }

    // Al publicar, el modal pasa al formulario de edición del producto creado.
    const handleProductoCreado = (producto) => {
        refrescarPublicaciones()
        setProductoEnEdicion(producto)
    }

    const handleProductoGuardado = (producto) => {
        refrescarPublicaciones()
        setProductoEnEdicion(producto)
        setConfirmarRetiroInicial(false)
    }

    return (
        <HashRouter>
            <DesplazarAlAncla />
            <main className="contenedor">
                {/* ===== Header: 3 accesos principales + acciones ===== */}
                <header className="app-header">
                    <img className="app-marca" src={`${import.meta.env.BASE_URL}logo-pulgatec.png`} alt="PulgaTec" width="88" height="88" />

                    <nav id="navegacion-principal" className="nav-principal" aria-label="Navegación principal">
                        <NavLink to="/" end>Inicio</NavLink>
                        {puedePublicar && <NavLink to="/mis-publicaciones">Mis Publicaciones</NavLink>}
                        {sesion?.rol === 'VENDEDOR' && <NavLink to="/premium">Plan Premium</NavLink>}
                        {!sesion && <NavLink to="/login">Iniciar sesión</NavLink>}
                        {sesion && <NavLink to="/perfil">Mi perfil</NavLink>}
                        {puedeConsultar && <NavLink to="/consultas">
                            Consultas
                            {pendientesConsulta.length > 0 &&
                                <span className="notificaciones-punto" role="img" aria-label="Hay mensajes sin leer" />}
                        </NavLink>}
                        {sesion && <NavLink to="/notificaciones">
                            Notificaciones
                            {tokenNotificaciones && tokenConAvisosPendientes === tokenNotificaciones &&
                                <span className="notificaciones-punto" role="img" aria-label="Hay notificaciones sin leer" />}
                        </NavLink>}
                        {sesion && tienePermiso(sesion, 'COMPRAR') && <NavLink to="/favoritos">Favoritos</NavLink>}
                        {sesion && tienePermiso(sesion, 'COMPRAR') && <NavLink to="/pedidos">Mis compras</NavLink>}
                        {sesion?.rol === 'VENDEDOR' && <NavLink to="/pedidos-recibidos">Pedidos recibidos</NavLink>}
                        {/* HU 57 - Panel de métricas de ventas del vendedor. */}
                        {sesion?.rol === 'VENDEDOR' && <NavLink to="/metricas-ventas">Métricas</NavLink>}
                        {/* HU 58 - Umbrales de stock y avisos de stock bajo. */}
                        {sesion?.rol === 'VENDEDOR' && <NavLink to="/umbrales-stock">Alertas de stock</NavLink>}
                        {sesion && <span className="app-usuario-nombre">{sesion.nombre || 'Mi cuenta'}</span>}
                        {sesion && <button className="btn-secundario" type="button" disabled={cerrandoSesion}
                            onClick={cerrarSesion}>{cerrandoSesion ? 'Cerrando sesión...' : 'Cerrar sesión'}</button>}
                    </nav>

                    <div className="app-acciones">
                        {/* Carrito como panel lateral (sin cambiar de página) */}
                        {puedeComprar && <button
                            type="button"
                            className="btn-carrito"
                            onClick={() => setDrawerCarritoAbierto(true)}
                            aria-label={`Abrir carrito de compras, ${itemsEnCarrito} unidades`}
                        >
                            {itemsEnCarrito > 0 && (
                                <span className="badge-carrito" aria-hidden="true">{itemsEnCarrito}</span>
                            )}
                        </button>}

                        {puedePublicar && <button
                            type="button"
                            className="btn-primario btn-publicar"
                            onClick={abrirPublicacion}
                        >
                            + Publicar
                        </button>}
                    </div>
                </header>

                {avisoSesion && <div className="alerta error" role="alert">{avisoSesion}
                    <button type="button" className="btn-secundario" onClick={() => setAvisoSesion('')}>Cerrar aviso</button>
                </div>}
                <Routes>
                    <Route path="/consultas" element={<AccesoAutenticado><Conversaciones key={sesion?.token} avisosPendientes={pendientesConsulta} /></AccesoAutenticado>} />
                    {/* HU 53/HU 54 - Bandeja unificada con filtros y lectura de avisos. */}
                    <Route path="/notificaciones" element={<AccesoAutenticado><BandejaNotificaciones key={sesion?.token} /></AccesoAutenticado>} />
                    {/* Inicio: catálogo con búsqueda (HU 06) y orden (HU 09)
                        + insignias Destacado/Agotado (HU 07/HU 19) */}
                    <Route
                        path="/"
                        element={<Inicio actualizar={actualizarMisProductos} />}
                    />

                    {/* HU 05 + HU 19 - gestión del vendedor (ver, editar, destacar) */}
                    <Route
                        path="/mis-publicaciones"
                        element={
                            <AccesoProtegido permiso="GESTIONAR_PRODUCTOS"><MisProductos
                                actualizar={actualizarMisProductos}
                                onEditar={abrirEdicion}
                                onActualizado={refrescarPublicaciones}
                            /></AccesoProtegido>
                        }
                    />

                    {/* HU 07 - detalle de producto (HU 11 avisa al badge tras agregar) */}
                    <Route
                        path="/producto/:idProducto"
                        element={
                            <DetalleProducto
                                onCarritoActualizado={recargarCarrito}
                                onVerCarrito={() => setDrawerCarritoAbierto(true)}
                            />
                        }
                    />

                    {/* HU 15 - checkout: resumen y registro del pedido */}
                    <Route
                        path="/confirmar-compra"
                        element={
                            <AccesoProtegido permiso="COMPRAR"><ConfirmarCompra onCarritoActualizado={recargarCarrito} /></AccesoProtegido>
                        }
                    />

                    {/* HU 18 - Beneficios Premium */}
                    <Route path="/premium" element={<AccesoVendedor><PlanPremium /></AccesoVendedor>} />

                    {/* HU 21 - Inicio de sesión (no bloquea el resto de rutas) */}
                    <Route path="/login" element={<Login />} />

                    {/* HU 26 - Registro de cuenta. */}
                    <Route path="/registro" element={<Registro />} />

                    {/* HU 22 - Solicitud y restablecimiento mediante enlace temporal. */}
                    <Route path="/recuperacion" element={<RecuperarAcceso key="solicitar" />} />
                    <Route path="/restablecer" element={<RecuperarAcceso key="restablecer" restablecer />} />

                    {/* HU 27 - Perfil del usuario autenticado. */}
                    <Route path="/perfil" element={
                        <AccesoAutenticado><Perfil /></AccesoAutenticado>
                    } />

                    {/* HU32/HU33 - lista y eliminación de favoritos. */}
                    <Route path="/favoritos" element={
                        <AccesoProtegido permiso="COMPRAR"><Favoritos /></AccesoProtegido>
                    } />
                    <Route path="/pedidos" element={<AccesoProtegido permiso="COMPRAR"><Pedidos /></AccesoProtegido>} />
                    {/* HU 50 - Detalle del pedido propio con productos y totales. */}
                    <Route path="/pedidos/:idPedido" element={<AccesoProtegido permiso="COMPRAR"><DetallePedido /></AccesoProtegido>} />
                    <Route path="/pedidos-recibidos" element={<AccesoVendedor><PedidosRecibidos /></AccesoVendedor>} />

                    {/* HU 57 - Métricas de ventas del vendedor (ingresos, pedidos,
                        promedio, productos más vendidos y comparación de periodos). */}
                    <Route path="/metricas-ventas" element={<AccesoVendedor><MetricasVentas /></AccesoVendedor>} />

                    {/* HU 58 - Umbrales de stock y avisos de stock bajo del vendedor. */}
                    <Route path="/umbrales-stock" element={<AccesoVendedor><UmbralesStock onEditar={abrirEdicion} actualizar={actualizarMisProductos} /></AccesoVendedor>} />
                </Routes>

                {/* Modal flotante de Publicar (HU 01/02) o Editar (HU 03) */}
                {puedePublicar && productoEnEdicion && confirmarRetiroInicial && (
                    <EditarProducto
                        producto={productoEnEdicion}
                        confirmarRetiroInicial
                        onGuardado={() => {
                            refrescarPublicaciones()
                            cerrarModal()
                        }}
                        onCancelar={cerrarModal}
                    />
                )}
                {puedePublicar && modalAbierto && !confirmarRetiroInicial && (
                    <ProductoModal
                        titulo={
                            productoEnEdicion
                                ? 'Editar publicación'
                                : 'Publicar producto'
                        }
                        onCerrar={cerrarModal}
                    >
                        {productoEnEdicion ? (
                            <EditarProducto
                                producto={productoEnEdicion}
                                onGuardado={handleProductoGuardado}
                                onCancelar={cerrarModal}
                            />
                        ) : (
                            <PublicarProducto onProductoCreado={handleProductoCreado} />
                        )}
                    </ProductoModal>
                )}

                {/* Panel lateral del carrito (HU 11 / 12 / 13 / 14) */}
                {puedeComprar && <CarritoDrawer
                        cantidad={itemsEnCarrito}
                    abierto={drawerCarritoAbierto}
                    onCerrar={() => setDrawerCarritoAbierto(false)}
                    onCarritoActualizado={recargarCarrito}
                />}

                {/* HU 16 / HU 17 - asistente IA como widget flotante */}
                <ChatbotFab />
            </main>
        </HashRouter>
    )
}
