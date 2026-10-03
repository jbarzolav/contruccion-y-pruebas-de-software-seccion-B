package com.ejemplo.publicarproducto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ejemplo.publicarproducto.ui.EditarProductoScreen
import com.ejemplo.publicarproducto.ui.PublicarProductoScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    // 0 = Publicar (HU 01 / HU 02), 1 = Editar y Retirar (HU 03 / HU 04)
                    var pestana by remember { mutableStateOf(0) }

                    Scaffold { padding ->
                        Column(modifier = Modifier.padding(padding)) {

                            TabRow(selectedTabIndex = pestana) {
                                Tab(
                                    selected = pestana == 0,
                                    onClick = { pestana = 0 },
                                    text = { Text("Publicar") }
                                )
                                Tab(
                                    selected = pestana == 1,
                                    onClick = { pestana = 1 },
                                    text = { Text("Editar / Retirar") }
                                )
                            }

                            when (pestana) {
                                0 -> PublicarProductoScreen()
                                else -> EditarProductoScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
