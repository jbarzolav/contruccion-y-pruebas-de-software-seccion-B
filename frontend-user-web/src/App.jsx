import { useEffect, useState } from 'react'
import { HashRouter, Link, Route, Routes } from 'react-router-dom'
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
import RecuperarAcceso from './components/RecuperarAcceso.jsx'
import AdministrarRoles from './components/AdministrarRoles.jsx'
import { obtenerSesion } from './api/auth.js'
import { guardarSesion, leerSesion, tienePermiso, useSesion } from './api/sesion.js'
import { obtenerCarrito } from './api/productos.js'

// HU 23 - Las rutas protegidas también comprueban el permiso al abrirlas directamente.
function AccesoProtegido({ permiso, children }) {
    const sesion = useSesion()
    if (!sesion) return <section className="tarjeta"><p>Debes iniciar sesión para continuar.</p><Link to="/login">Iniciar sesión</Link></section>
    if (!sesion.rol) return <p>Validando sesión...</p>
    if (!tienePermiso(sesion, permiso)) return <section className="tarjeta"><p>No tienes permiso para acceder a esta función.</p><Link to="/">Volver al inicio</Link></section>
    return children
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
    const puedeAdministrar = tienePermiso(sesion, 'ASIGNAR_ROLES')
    const [avisoSesion, setAvisoSesion] = useState('')

    // HU 23 - Recuperar el rol actual, incluidas sesiones creadas antes de esta historia.
    useEffect(() => {
        let vigente = true
        if (sesion?.token) setAvisoSesion('')
        async function actualizarSesion() {
            const token = leerSesion()?.token
            if (!token) return
            try {
                const actual = await obtenerSesion()
                if (vigente && leerSesion()?.token === token) guardarSesion(actual)
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
        setProductoEnEdicion(null)
        setPublicando(true)
    }

    function abrirEdicion(producto) {
        setPublicando(false)
        setProductoEnEdicion(producto)
    }

    function cerrarModal() {
        setPublicando(false)
        setProductoEnEdicion(null)
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
    }

    return (
        <HashRouter>
            <main className="contenedor">
                {/* ===== Header: 3 accesos principales + acciones ===== */}
                <header className="app-header">
                    <img className="app-marca" src={`${import.meta.env.BASE_URL}logo-pulgatec.png`} alt="PulgaTec" width="88" height="88" />

                    <nav className="nav-principal" aria-label="Navegación principal">
                        <Link to="/">Inicio</Link>
                        {puedePublicar && <Link to="/mis-publicaciones">Mis Publicaciones</Link>}
                        <Link to="/premium">Plan Premium</Link>
                        {puedeAdministrar && <Link to="/administracion/roles">Administrar roles</Link>}
                        {!sesion && <Link to="/login">Iniciar sesión</Link>}
                        {sesion && <span>{sesion.correo} · {sesion.rol}</span>}
                    </nav>

                    <div className="app-acciones">
                        {/* Carrito como panel lateral (sin cambiar de página) */}
                        {puedeComprar && <button
                            type="button"
                            className="btn-carrito"
                            onClick={() => setDrawerCarritoAbierto(true)}
                            aria-label="Abrir el carrito de compras"
                        >
                            🛒
                            {itemsEnCarrito > 0 && (
                                <span className="badge-carrito">{itemsEnCarrito}</span>
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
                    <Route path="/premium" element={<PlanPremium />} />

                    {/* HU 21 - Inicio de sesión (no bloquea el resto de rutas) */}
                    <Route path="/login" element={<Login />} />

                    {/* HU 23 - Asignación administrativa de roles. */}
                    <Route path="/administracion/roles" element={<AccesoProtegido permiso="ASIGNAR_ROLES"><AdministrarRoles /></AccesoProtegido>} />

                    {/* HU 22 - Solicitud y restablecimiento mediante enlace temporal. */}
                    <Route path="/recuperacion" element={<RecuperarAcceso key="solicitar" />} />
                    <Route path="/restablecer" element={<RecuperarAcceso key="restablecer" restablecer />} />
                </Routes>

                {/* Modal flotante de Publicar (HU 01/02) o Editar (HU 03) */}
                {puedePublicar && modalAbierto && (
                    <ProductoModal
                        titulo={
                            productoEnEdicion
                                ? `Editar producto (ID ${productoEnEdicion.id})`
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
import AdministrarAuditoria from './components/AdministrarAuditoria.jsx'
                        {puedeAdministrar && <Link to="/administracion/auditoria">Auditoría</Link>}
                    {/* HU 24 - Consulta de acciones; servidor exige identidad y permiso admin. */}
                    <Route path="/administracion/auditoria" element={<AccesoProtegido permiso="ASIGNAR_ROLES"><AdministrarAuditoria /></AccesoProtegido>} />
