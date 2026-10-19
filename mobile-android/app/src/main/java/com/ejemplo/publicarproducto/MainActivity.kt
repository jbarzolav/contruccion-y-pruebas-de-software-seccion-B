package com.ejemplo.publicarproducto

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.History
import com.ejemplo.publicarproducto.ui.SesionViewModel
import com.ejemplo.publicarproducto.ui.AdministrarRolesScreen
import com.ejemplo.publicarproducto.ui.AuditoriaScreen
import com.ejemplo.publicarproducto.ui.LogoutUiState
import com.ejemplo.publicarproducto.network.UserApiClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.ejemplo.publicarproducto.ui.BuscarProductosScreen
import com.ejemplo.publicarproducto.ui.CarritoScreen
import com.ejemplo.publicarproducto.ui.ChatbotScreen
import com.ejemplo.publicarproducto.ui.ConfirmarCompraScreen
import com.ejemplo.publicarproducto.ui.DetalleProductoScreen
import com.ejemplo.publicarproducto.ui.EditarProductoScreen
import com.ejemplo.publicarproducto.ui.LoginScreen
import com.ejemplo.publicarproducto.ui.PerfilScreen
import com.ejemplo.publicarproducto.ui.RecuperacionScreen
import com.ejemplo.publicarproducto.ui.RegistroScreen
import com.ejemplo.publicarproducto.ui.MisProductosScreen
import com.ejemplo.publicarproducto.ui.PlanPremiumScreen
import com.ejemplo.publicarproducto.ui.PublicarProductoScreen
import com.ejemplo.publicarproducto.ui.theme.PulgaTecTheme

/** Pestañas de la barra inferior: máximo 5, con ícono Material y etiqueta corta. */
private data class Pestana(
    val indice: Int,
    val etiqueta: String,
    val icono: ImageVector
)

private val PESTANAS = listOf(
    Pestana(0, "Inicio", Icons.Default.Search),
    Pestana(1, "Productos", Icons.Default.Inventory2),
    Pestana(2, "Carrito", Icons.Default.ShoppingCart),
    Pestana(3, "Chatbot", Icons.Default.SmartToy),
    Pestana(4, "Premium", Icons.Default.WorkspacePremium),
    Pestana(5, "Roles", Icons.Default.ManageAccounts),
    Pestana(6, "Auditoría", Icons.Default.History),
    Pestana(7, "Mi perfil", Icons.Default.Person)
)

/**
 * Rediseño UI/UX - PulgaTec Android.
 * - Barra inferior de 5 pestañas con íconos (sin textos truncados).
 * - Publicar (FAB +), Editar y Confirmar compra como sub-pantallas
 *   con botón "Volver" (la funcionalidad HU 01-19 no cambia).
 * - HU 21: sin token guardado la entrada es LoginScreen; con token se
 *   entra directo al flujo principal (control de UI local, sin
 *   validación remota del token).
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val sesionViewModel: SesionViewModel by viewModels()

    // HU 22 - Enlace recibido al iniciar la app o cuando ya está abierta.
    private var tokenRecuperacion by mutableStateOf<String?>(null)
    private var recuperando by mutableStateOf(false)

    // HU 26 - Registro de cuenta desde la pantalla de inicio de sesión.
    private var registrando by mutableStateOf(false)

    private fun recibirEnlace(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && uri.scheme == "pulgatec" && uri.host == "restablecer") {
            tokenRecuperacion = uri.getQueryParameter("token") ?: ""
            recuperando = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recibirEnlace(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recibirEnlace(intent)
        UserApiClient.configurarSesion(sesionViewModel.sesion) { codigo ->
            runOnUiThread { sesionViewModel.manejarError(codigo) }
        }
        sesionViewModel.recargarYValidar()
        setContent {
            PulgaTecTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // HU 23 - La sesión validada determina pestañas y acciones disponibles.
                    val usuario = sesionViewModel.usuario
                    val puedeComprar = usuario?.permisos?.contains("COMPRAR") == true
                    val puedePublicar = usuario?.permisos?.contains("GESTIONAR_PRODUCTOS") == true
                    val puedeAdministrar = usuario?.permisos?.contains("ASIGNAR_ROLES") == true
                    val pestanasPermitidas = PESTANAS.filter {
                        when (it.indice) {
                            1 -> puedePublicar
                            2 -> puedeComprar
                            5, 6 -> puedeAdministrar
                            7 -> usuario != null
                            else -> true
                        }
                    }
                    var mostrarDialogoLogout by remember { mutableStateOf(false) }
                    if (mostrarDialogoLogout) {
                        AlertDialog(
                            onDismissRequest = {
                                if (sesionViewModel.logoutState !is LogoutUiState.Loading) {
                                    mostrarDialogoLogout = false
                                    sesionViewModel.reiniciarEstadoLogout()
                                }
                            },
                            title = { Text("Cerrar sesión") },
                            text = {
                                when (val estado = sesionViewModel.logoutState) {
                                    LogoutUiState.Initial -> Text("¿Quieres cerrar tu sesión de forma segura?")
                                    LogoutUiState.Loading -> Text("Cerrando sesión...")
                                    LogoutUiState.Success -> Text("Sesión cerrada correctamente.")
                                    is LogoutUiState.Error -> Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            confirmButton = {
                                when (sesionViewModel.logoutState) {
                                    LogoutUiState.Initial -> TextButton(onClick = sesionViewModel::cerrarSesion) { Text("Cerrar sesión") }
                                    LogoutUiState.Loading -> Unit
                                    LogoutUiState.Success, is LogoutUiState.Error -> TextButton(onClick = {
                                        mostrarDialogoLogout = false
                                        sesionViewModel.reiniciarEstadoLogout()
                                    }) { Text("Continuar") }
                                }
                            },
                            dismissButton = {
                                if (sesionViewModel.logoutState == LogoutUiState.Initial) TextButton(onClick = {
                                    mostrarDialogoLogout = false
                                }) { Text("Cancelar") }
                            }
                        )
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (usuario != null) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    sesionViewModel.reiniciarEstadoLogout()
                                    mostrarDialogoLogout = true
                                }) { Text("Cerrar sesión") }
                            }
                        }
                        sesionViewModel.aviso?.let { aviso ->
                            Text(aviso, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { sesionViewModel.cerrarAviso() }) { Text("Cerrar aviso") }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                    if (recuperando) {
                        RecuperacionScreen(
                            token = tokenRecuperacion,
                            onVolver = {
                                tokenRecuperacion = null
                                recuperando = false
                                sesionViewModel.volverAlLogin()
                            },
                            onSolicitarEnlace = { tokenRecuperacion = null }
                        )
                    } else if (sesionViewModel.validando) {
                        Text("Validando sesión...")
                    } else if (registrando) {
                        // HU 26 - Registro: al volver se descarta el estado y se ofrece el login.
                        RegistroScreen(
                            onVolver = {
                                registrando = false
                                sesionViewModel.volverAlLogin()
                            }
                        )
                    } else if (usuario == null) {
                        // Entrada de la app sin token
                        LoginScreen(
                            onLoginExitoso = { sesionViewModel.recargarYValidar() },
                            onRecuperarAcceso = {
                                tokenRecuperacion = null
                                recuperando = true
                            },
                            onCrearCuenta = { registrando = true }
                        )
                    } else {

                    // 0 = Inicio (catálogo/búsqueda), 1 = Mis productos,
                    // 2 = Carrito (HU 12), 3 = Chatbot (HU 16/17), 4 = Premium (HU 18)
                    var pestana by remember(usuario.id, usuario.rol) { mutableStateOf(0) }

                    // Sub-pantallas fuera de la barra inferior
                    var productoDetalleId by remember(usuario.id, usuario.rol) { mutableStateOf<Long?>(null) } // HU 07
                    var productoEnEdicionId by remember(usuario.id, usuario.rol) { mutableStateOf<Long?>(null) } // HU 03
                    var publicando by remember(usuario.id, usuario.rol) { mutableStateOf(false) } // HU 01/02
                    var confirmandoCompra by remember(usuario.id, usuario.rol) { mutableStateOf(false) } // HU 15

                    val enPantallaPrincipal = productoDetalleId == null
                            && productoEnEdicionId == null
                            && !publicando
                            && !confirmandoCompra

                    Scaffold(
                        topBar = {
                            if (enPantallaPrincipal) {
                                TopAppBar(
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
                            if (enPantallaPrincipal) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    pestanasPermitidas.forEach { item ->
                                        NavigationBarItem(
                                            selected = pestana == item.indice,
                                            onClick = { pestana = item.indice },
                                            icon = {
                                                Icon(
                                                    imageVector = item.icono,
                                                    contentDescription = item.etiqueta
                                                )
                                            },
                                            label = { Text(item.etiqueta) },
                                            alwaysShowLabel = true
                                        )
                                    }
                                }
                            }
                        },
                        floatingActionButton = {
                            // HU 01 - publicar desde "Mis productos" sin ocupar una pestaña
                            if (puedePublicar && pestana == 1 && enPantallaPrincipal) {
                                FloatingActionButton(
                                    onClick = { publicando = true },
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
                    ) { padding ->
                        val detalleId = productoDetalleId
                        val edicionId = productoEnEdicionId

                        when {
                            // HU 07 - pantalla dedicada de detalle
                            detalleId != null -> {
                                DetalleProductoScreen(
                                    productoId = detalleId,
                                    puedeComprar = puedeComprar,
                                    onVolver = { productoDetalleId = null },
                                    onIrACarrito = {
                                        productoDetalleId = null
                                        pestana = 2
                                    },
                                    modifier = Modifier.padding(padding)
                                )
                            }

                            // HU 03/04 - edición con el producto ya cargado
                            edicionId != null -> {
                                EditarProductoScreen(
                                    productoId = edicionId,
                                    onVolver = { productoEnEdicionId = null },
                                    modifier = Modifier.padding(padding)
                                )
                            }

                            // HU 01/02 - publicar como sub-pantalla
                            publicando -> {
                                PublicarProductoScreen(
                                    onVolver = { publicando = false },
                                    modifier = Modifier.padding(padding)
                                )
                            }

                            // HU 15 - confirmar la compra como sub-pantalla del carrito
                            confirmandoCompra -> {
                                ConfirmarCompraScreen(
                                    onVolver = { confirmandoCompra = false },
                                    modifier = Modifier.padding(padding)
                                )
                            }

                            else -> when (pestana) {
                                // HU 06/07/08/09 - Inicio: catálogo con búsqueda y orden
                                0 -> BuscarProductosScreen(
                                    modifier = Modifier.padding(padding),
                                    puedeGuardarFavoritos = puedeComprar,
                                    onSeleccionarProducto = { id ->
                                        productoDetalleId = id
                                    }
                                )

                                // HU 05 + HU 19 - gestión del vendedor
                                1 -> MisProductosScreen(
                                    modifier = Modifier.padding(padding),
                                    onEditarProducto = { id ->
                                        productoEnEdicionId = id
                                    },
                                    onVerDetalle = { id ->
                                        productoDetalleId = id
                                    }
                                )

                                // HU 11/12/13 - carrito -> HU 15 confirmar
                                2 -> CarritoScreen(
                                    modifier = Modifier.padding(padding),
                                    onConfirmarCompra = { confirmandoCompra = true }
                                )

                                // HU 16/17 - chatbot -> detalle del producto sugerido
                                3 -> ChatbotScreen(
                                    onSeleccionarProducto = { id ->
                                        productoDetalleId = id
                                    },
                                    modifier = Modifier.padding(padding)
                                )

                                // HU 23 - Pantalla exclusiva de administración.
                                5 -> AdministrarRolesScreen(
                                    onRolActualizado = { sesionViewModel.actualizar() },
                                    modifier = Modifier.padding(padding)
                                )

                                // HU24 - Historial exclusivo para el rol administrativo.
                                6 -> AuditoriaScreen(modifier = Modifier.padding(padding))

                                // HU 27 - Mi perfil
                                7 -> PerfilScreen(
                                    onVolver = { pestana = 0 },
                                    modifier = Modifier.padding(padding)
                                )

                                // HU 18 - beneficios Premium
                                else -> PlanPremiumScreen(
                                    modifier = Modifier.padding(padding)
                                )
                            }
                        }
                    }

                    } // fin del flujo principal (sesión iniciada)
                        } // Contenido de la sesión
                    } // Avisos y contenido
                }
            }
        }
    }
}
