package com.ejemplo.publicarproducto.view.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.view.AdministrarRolesScreen
import com.ejemplo.publicarproducto.view.AuditoriaScreen
import com.ejemplo.publicarproducto.view.BandejaNotificacionesScreen
import com.ejemplo.publicarproducto.view.BuscarProductosScreen
import com.ejemplo.publicarproducto.view.CarritoScreen
import com.ejemplo.publicarproducto.view.ChatbotScreen
import com.ejemplo.publicarproducto.view.ConfirmarCompraScreen
import com.ejemplo.publicarproducto.view.ConsultasScreen
import com.ejemplo.publicarproducto.view.DetallePedidoScreen
import com.ejemplo.publicarproducto.view.DetalleProductoScreen
import com.ejemplo.publicarproducto.view.EditarProductoScreen
import com.ejemplo.publicarproducto.view.MetricasVentasScreen
import com.ejemplo.publicarproducto.view.MisPedidosScreen
import com.ejemplo.publicarproducto.view.MisProductosScreen
import com.ejemplo.publicarproducto.view.PerfilScreen
import com.ejemplo.publicarproducto.view.PedidosRecibidosScreen
import com.ejemplo.publicarproducto.view.PlanPremiumScreen
import com.ejemplo.publicarproducto.view.PublicarProductoScreen
import com.ejemplo.publicarproducto.view.UmbralesStockScreen
import com.ejemplo.publicarproducto.viewmodel.AvisosConversacionViewModel
import com.ejemplo.publicarproducto.viewmodel.SesionViewModel

/**
 * Navegación principal de la aplicación (usuario con sesión iniciada).
 *
 * Concentra la barra superior, la barra inferior, el FAB de publicación y el
 * despacho de pantallas. Solo dibuja y navega: el estado y los datos llegan
 * desde los ViewModels ([SesionViewModel] y [AvisosConversacionViewModel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavegacion(
    usuario: UsuarioRolResponse,
    sesionViewModel: SesionViewModel,
    avisosConversacionViewModel: AvisosConversacionViewModel,
    onCerrarSesion: () -> Unit = {}
) {
    val estado = rememberEstadoNavegacion(usuario.id to usuario.rol)
    val pestanasPermitidas = remember(usuario.id, usuario.rol) { pestanasPermitidas(usuario) }
    val puedeComprar = usuario.permisos.contains("COMPRAR")
    val puedePublicar = usuario.permisos.contains("GESTIONAR_PRODUCTOS")
    val avisoMensaje = avisosConversacionViewModel.avisoMensaje

    // HU39 - Avisa en la navegación cuando llega un mensaje de otro participante.
    //
    // R1/R3 - El sondeo de `GET /api/conversaciones` tiene UN solo responsable
    // y solo corre EN PRIMER PLANO:
    // - con la pestaña Consultas activa, sondea el ViewModel de la pantalla
    //   (que además refresca la conversación abierta);
    // - en el resto de pestañas, sondea este ViewModel de avisos;
    // - con la aplicación en segundo plano, no sondea nadie (no hay peticiones
    //   HTTP en background).
    //
    // Aquí solo se decide a quién le toca; el trabajo se cancela al salir de
    // la sesión autenticada.
    //
    // R1/M1 - "Consultas activa" NO es solo "pestaña Consultas": cuando se
    // abre una subpantalla sobre ella (detalle de producto, edición,
    // publicación…), la pantalla de Consultas sale de la composición y su
    // sondeo se detiene. Si el arbitraje no lo tuviera en cuenta, en ese
    // momento no sondearía nadie y el aviso de la barra superior se quedaría
    // congelado hasta volver a la pestaña.
    //
    // La condición vive en `consultasSonResponsable(estado)`, una función pura
    // verificada en la JVM sobre el estado real de navegación.
    val consultasActiva = consultasSonResponsable(estado)

    // R1 - Estado de primer plano de la aplicación.
    //
    // Se observa el ciclo de vida del `Activity` (el `LifecycleOwner` local
    // de Compose). `ON_START` significa "la app es visible"; `ON_STOP`,
    // "la app está en segundo plano". Mientras esté en segundo plano, el
    // sondeo se cancela: así no se hacen peticiones HTTP cada 15 s con la
    // aplicación oculta.
    val ownerCicloVida = LocalLifecycleOwner.current

    // R1 - El coordinador aplica la regla de arbitraje.
    //
    // El estado de "Consultas visible" y la identidad del usuario se consultan
    // EN EL MOMENTO de decidir (no se guarda una copia): al volver al primer
    // plano se decide con lo que haya en ese instante, sin depender de que
    // Compose llegue a recomponer.
    val coordinador = remember(avisosConversacionViewModel, estado) {
        CoordinadorSondeoAvisos(
            usuarioActivoAhora = { sesionViewModel.usuario?.id },
            consultasActivasAhora = { consultasSonResponsable(estado) },
            alIniciarSondeo = { usuarioId -> avisosConversacionViewModel.iniciar(usuarioId) },
            alPausarSondeo = { avisosConversacionViewModel.pausar() }
        )
    }

    // R1 - El observador notifica el cambio de primer plano DE FORMA
    // SÍNCRONA, dentro del callback del ciclo de vida: Compose no garantiza
    // recomponer después de `ON_STOP`, así que la cancelación del sondeo no
    // puede depender de una recomposición.
    val primerPlano = remember(ownerCicloVida.lifecycle, coordinador) {
        ObservadorPrimerPlano(ownerCicloVida.lifecycle) { enPrimerPlano ->
            coordinador.onCambioPrimerPlano(enPrimerPlano)
        }
    }
    DisposableEffect(primerPlano) {
        primerPlano.iniciar()
        onDispose { primerPlano.detener() }
    }

    // El contexto de navegación (identidad del usuario y pantalla visible) se
    // propaga al coordinador, que vuelve a aplicar la regla de arbitraje.
    LaunchedEffect(usuario.id, consultasActiva, coordinador) {
        coordinador.reevaluar()
    }
    // La limpieza total se hace al SALIR de la sesión autenticada (cierre de
    // sesión o destrucción de la Activity), no al cambiar de usuario: así el
    // cambio de cuenta lo gestiona `iniciar()` (que reinicia la memoria de
    // mensajes vistos) sin depender del orden en que Compose aplique los
    // cambios.
    //
    // R1/M2 - En una recreación de la Activity (rotación) la sesión sigue
    // abierta: ahí solo se pausa el sondeo y se conserva la memoria de los
    // mensajes vistos, para no perder el aviso de la barra superior ni los
    // mensajes que lleguen durante la recreación.
    DisposableEffect(Unit) {
        onDispose {
            when (accionAlSalirDeLaComposicion(usuarioActivo = sesionViewModel.usuario != null)) {
                AccionAlSalir.PAUSAR -> avisosConversacionViewModel.pausar()
                AccionAlSalir.DETENER -> avisosConversacionViewModel.detener()
            }
        }
    }

    Scaffold(
        topBar = {
            if (estado.enPantallaPrincipal) {
                TopAppBar(
                    actions = {
                        if (estado.pestana != IndicesNavegacion.CHATBOT) {
                            IconButton(onClick = { estado.irA(IndicesNavegacion.CHATBOT) }) {
                                Icon(Icons.Default.SmartToy, contentDescription = "Chatbot")
                            }
                        }
                        // HU 53/54 - bandeja de notificaciones como campanita
                        // en la esquina (antes era un enlace de texto).
                        IconButton(onClick = { estado.irA(IndicesNavegacion.NOTIFICACIONES) }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notificaciones"
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "PulgaTec",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        bottomBar = {
            // en sub-pantallas (detalle, editar, publicar, confirmar) se oculta
            if (estado.enPantallaPrincipal) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    pestanasPermitidas.forEach { item ->
                        NavigationBarItem(
                            selected = estado.pestana == item.indice,
                            onClick = {
                                // HU39 - al entrar a Consultas se consume el
                                // aviso de mensaje nuevo (como antes).
                                if (item.indice == IndicesNavegacion.CONSULTAS) {
                                    estado.limpiarConversacion()
                                    avisosConversacionViewModel.limpiarAviso()
                                }
                                estado.irA(item.indice)
                            },
                            icon = {
                                // HU39 - punto de aviso cuando llegó un mensaje.
                                if (item.indice == IndicesNavegacion.CONSULTAS && avisoMensaje != null) {
                                    BadgedBox(badge = { Badge { Text("!") } }) {
                                        Icon(
                                            imageVector = item.icono,
                                            contentDescription = item.etiqueta
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = item.icono,
                                        contentDescription = item.etiqueta
                                    )
                                }
                            },
                            label = { Text(item.etiqueta) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (estado.enPantallaPrincipal) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // HU 01 - publicar desde "Mis productos" sin ocupar una pestaña
                    if (puedePublicar && estado.pestana == IndicesNavegacion.PRODUCTOS) {
                        FloatingActionButton(
                            onClick = { estado.abrirPublicacion() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Publicar producto"
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        val detalleId = estado.productoDetalleId
        val edicionId = estado.productoEnEdicionId

        when {
            // HU 07 - pantalla dedicada de detalle
            detalleId != null -> {
                DetalleProductoScreen(
                    productoId = detalleId,
                    puedeComprar = puedeComprar,
                    onVolver = { estado.cerrarProducto() },
                    onIrACarrito = {
                        estado.cerrarProducto()
                        estado.irA(IndicesNavegacion.CARRITO)
                    },
                    modifier = Modifier.padding(padding)
                )
            }

            // HU 03/04 - edición con el producto ya cargado
            edicionId != null -> {
                EditarProductoScreen(
                    productoId = edicionId,
                    onVolver = { estado.cerrarEdicion() },
                    modifier = Modifier.padding(padding)
                )
            }

            // HU 01/02 - publicar como sub-pantalla
            estado.publicando -> {
                PublicarProductoScreen(
                    onVolver = { estado.cerrarPublicacion() },
                    modifier = Modifier.padding(padding)
                )
            }

            // HU 15 - confirmar la compra como sub-pantalla del carrito
            estado.confirmandoCompra -> {
                ConfirmarCompraScreen(
                    onVolver = { estado.cerrarConfirmacionCompra() },
                    modifier = Modifier.padding(padding)
                )
            }

            // HU 50 - detalle del pedido propio (productos, precios y total)
            estado.pedidoDetalleId != null -> {
                DetallePedidoScreen(
                    idPedido = estado.pedidoDetalleId!!,
                    onVolver = { estado.cerrarPedido() },
                    modifier = Modifier.padding(padding)
                )
            }

            else -> when (indicePantallaPrincipalSeguro(estado.pestana)) {
                IndicesNavegacion.CONSULTAS -> key(usuario.id, sesionViewModel.token()) {
                    ConsultasScreen(
                        esVendedor = usuario.rol == "VENDEDOR",
                        conversacionInicial = estado.conversacionPendienteId,
                        modifier = Modifier.padding(padding),
                        onVerProducto = { estado.abrirProducto(it) }
                    )
                }

                // HU 53/54 - bandeja unificada con filtros y lectura de avisos
                IndicesNavegacion.NOTIFICACIONES -> BandejaNotificacionesScreen(
                    onVerPedido = { id -> estado.abrirPedido(id) },
                    onVerVentas = { estado.irA(IndicesNavegacion.VENTAS) },
                    onVerConversacion = { id ->
                        estado.abrirConversacion(id)
                        estado.irA(IndicesNavegacion.CONSULTAS)
                    },
                    // HU 58 - El aviso de stock bajo abre la publicación.
                    onVerPublicacion = { id ->
                        estado.abrirProducto(id)
                    },
                    modifier = Modifier.padding(padding)
                )

                // HU 06/07/08/09 - Inicio: catálogo con búsqueda y orden
                IndicesNavegacion.INICIO -> BuscarProductosScreen(
                    modifier = Modifier.padding(padding),
                    puedeGuardarFavoritos = puedeComprar,
                    onSeleccionarProducto = { id ->
                        estado.abrirProducto(id)
                    }
                )

                // HU 05 + HU 19 - gestión del vendedor
                IndicesNavegacion.PRODUCTOS -> MisProductosScreen(
                    modifier = Modifier.padding(padding),
                    onEditarProducto = { id ->
                        estado.abrirEdicion(id)
                    },
                    onVerDetalle = { id ->
                        estado.abrirProducto(id)
                    }
                )

                // HU 11/12/13 - carrito -> HU 15 confirmar
                IndicesNavegacion.CARRITO -> CarritoScreen(
                    modifier = Modifier.padding(padding),
                    onConfirmarCompra = { estado.abrirConfirmacionCompra() }
                )

                // HU 16/17 - chatbot -> detalle del producto sugerido
                IndicesNavegacion.CHATBOT -> ChatbotScreen(
                    onSeleccionarProducto = { id ->
                        estado.abrirProducto(id)
                    },
                    modifier = Modifier.padding(padding)
                )

                // HU 23 - Pantalla exclusiva de administración.
                IndicesNavegacion.ROLES -> AdministrarRolesScreen(
                    onRolActualizado = { sesionViewModel.actualizar() },
                    modifier = Modifier.padding(padding)
                )

                // HU24 - Historial exclusivo para el rol administrativo.
                IndicesNavegacion.AUDITORIA -> AuditoriaScreen(modifier = Modifier.padding(padding))

                // HU 27 - Mi perfil (con los accesos a Premium y a la zona
                // del vendedor que antes estaban en la barra inferior).
                IndicesNavegacion.PERFIL -> PerfilScreen(
                    onVolver = { estado.irA(IndicesNavegacion.INICIO) },
                    onNavegarA = { estado.irA(it) },
                    onCerrarSesion = onCerrarSesion,
                    modifier = Modifier.padding(padding)
                )

                // HU 46 - pedidos propios y detalle con edición de reseñas.
                IndicesNavegacion.PEDIDOS -> MisPedidosScreen(
                    onVolver = { estado.irA(IndicesNavegacion.INICIO) },
                    onVerDetalle = { id -> estado.abrirPedido(id) },
                    modifier = Modifier.padding(padding)
                )

                // HU 48 - pedidos recibidos con solo los productos del vendedor.
                IndicesNavegacion.VENTAS -> PedidosRecibidosScreen(modifier = Modifier.padding(padding))

                // HU 57 - métricas de ventas del vendedor.
                IndicesNavegacion.METRICAS -> MetricasVentasScreen(modifier = Modifier.padding(padding))

                // HU 58 - umbrales de stock y avisos de stock bajo.
                IndicesNavegacion.UMBRALES -> UmbralesStockScreen(
                    modifier = Modifier.padding(padding),
                    onEditarPublicacion = { id -> estado.abrirEdicion(id) },
                    onVerPublicacion = { id -> estado.abrirProducto(id) }
                )

                // HU 18 - beneficios Premium
                IndicesNavegacion.PREMIUM -> PlanPremiumScreen(
                    modifier = Modifier.padding(padding)
                )

                // R10 - Fallback seguro: un índice desconocido vuelve a Inicio
                // en vez de mostrar una pantalla incorrecta.
                else -> BuscarProductosScreen(
                    modifier = Modifier.padding(padding),
                    puedeGuardarFavoritos = puedeComprar,
                    onSeleccionarProducto = { id ->
                        estado.abrirProducto(id)
                    }
                )
            }
        }
    }
}
