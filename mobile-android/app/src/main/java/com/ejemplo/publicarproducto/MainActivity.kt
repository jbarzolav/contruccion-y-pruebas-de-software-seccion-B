package com.ejemplo.publicarproducto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ejemplo.publicarproducto.ui.BuscarProductosScreen
import com.ejemplo.publicarproducto.ui.DetalleProductoScreen
import com.ejemplo.publicarproducto.ui.EditarProductoScreen
import com.ejemplo.publicarproducto.ui.MisProductosScreen
import com.ejemplo.publicarproducto.ui.PublicarProductoScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    // 0 = Publicar, 1 = Gestionar, 2 = Mis productos, 3 = Buscar
                    var pestana by remember { mutableStateOf(0) }
                    var productoIdSeleccionado by remember { mutableStateOf<Long?>(null) }

                    // HU 07 - id del producto en detalle (HU 08: llega desde una tarjeta)
                    var productoDetalleId by remember { mutableStateOf<Long?>(null) }

                    Scaffold(
                        bottomBar = {
                            // en el detalle se oculta la barra de pestañas
                            if (productoDetalleId == null) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = pestana == 0,
                                        onClick = {
                                            pestana = 0
                                            productoIdSeleccionado = null
                                        },
                                        icon = {},
                                        label = { Text("Publicar") }
                                    )
                                    NavigationBarItem(
                                        selected = pestana == 1,
                                        onClick = {
                                            pestana = 1
                                            productoIdSeleccionado = null
                                        },
                                        icon = {},
                                        label = { Text("Gestionar") }
                                    )
                                    NavigationBarItem(
                                        selected = pestana == 2,
                                        onClick = {
                                            pestana = 2
                                            productoIdSeleccionado = null
                                        },
                                        icon = {},
                                        label = { Text("Mis productos") }
                                    )
                                    NavigationBarItem(
                                        selected = pestana == 3,
                                        onClick = {
                                            pestana = 3
                                            productoIdSeleccionado = null
                                        },
                                        icon = {},
                                        label = { Text("Buscar") }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        val detalleId = productoDetalleId

                        if (detalleId != null) {
                            // HU 07 - pantalla dedicada de detalle
                            DetalleProductoScreen(
                                productoId = detalleId,
                                onVolver = { productoDetalleId = null },
                                modifier = Modifier.padding(padding)
                            )
                        } else {
                            when (pestana) {
                                0 -> PublicarProductoScreen(
                                    modifier = Modifier.padding(padding)
                                )

                                1 -> EditarProductoScreen(
                                    modifier = Modifier.padding(padding),
                                    productoId = productoIdSeleccionado
                                )

                                2 -> MisProductosScreen(
                                    modifier = Modifier.padding(padding),
                                    onEditarProducto = { id ->
                                        productoIdSeleccionado = id
                                        pestana = 1
                                    },
                                    // HU 08 - seleccionar producto propio -> detalle
                                    onVerDetalle = { id ->
                                        productoDetalleId = id
                                    }
                                )

                                else -> BuscarProductosScreen(
                                    modifier = Modifier.padding(padding),
                                    // HU 08 - seleccionar del catálogo/búsqueda -> detalle
                                    onSeleccionarProducto = { id ->
                                        productoDetalleId = id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}