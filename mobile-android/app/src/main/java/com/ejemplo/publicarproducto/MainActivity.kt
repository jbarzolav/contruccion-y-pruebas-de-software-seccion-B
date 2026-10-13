package com.ejemplo.publicarproducto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
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
    Pestana(4, "Premium", Icons.Default.WorkspacePremium)
)

/**
 * Rediseño UI/UX - PulgaTec Android.
 * - Barra inferior de 5 pestañas con íconos (sin textos truncados).
 * - Publicar (FAB +), Editar y Confirmar compra como sub-pantallas
 *   con botón "Volver" (la funcionalidad HU 01-19 no cambia).
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulgaTecTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // 0 = Inicio (catálogo/búsqueda), 1 = Mis productos,
                    // 2 = Carrito (HU 12), 3 = Chatbot (HU 16/17), 4 = Premium (HU 18)
                    var pestana by remember { mutableStateOf(0) }

                    // Sub-pantallas fuera de la barra inferior
                    var productoDetalleId by remember { mutableStateOf<Long?>(null) } // HU 07
                    var productoEnEdicionId by remember { mutableStateOf<Long?>(null) } // HU 03
                    var publicando by remember { mutableStateOf(false) } // HU 01/02
                    var confirmandoCompra by remember { mutableStateOf(false) } // HU 15

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
                                    PESTANAS.forEach { item ->
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
                            if (pestana == 1 && enPantallaPrincipal) {
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
                                    onVolver = { productoDetalleId = null },
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

                                // HU 18 - beneficios Premium
                                else -> PlanPremiumScreen(
                                    modifier = Modifier.padding(padding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
