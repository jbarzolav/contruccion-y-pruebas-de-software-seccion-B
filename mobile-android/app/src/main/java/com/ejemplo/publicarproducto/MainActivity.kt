package com.ejemplo.publicarproducto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ejemplo.publicarproducto.view.navigation.PantallaRaiz
import com.ejemplo.publicarproducto.view.theme.PulgaTecTheme
import com.ejemplo.publicarproducto.viewmodel.AvisosConversacionViewModel
import com.ejemplo.publicarproducto.viewmodel.SesionViewModel

/**
 * Punto de entrada de PulgaTec.
 *
 * `MainActivity` solo cumple tres funciones:
 * 1. recibir el enlace profundo de restablecimiento de contraseña (HU 22);
 * 2. crear los ViewModels de sesión y de avisos de conversación;
 * 3. montar la interfaz (`PantallaRaiz`) con el tema de la aplicación.
 *
 * La navegación, las pantallas, el polling y el manejo de la sesión viven en
 * las capas `view/` y `viewmodel/`; aquí no hay acceso a datos ni a HTTP.
 */
class MainActivity : ComponentActivity() {

    private val sesionViewModel: SesionViewModel by viewModels()
    private val avisosConversacionViewModel: AvisosConversacionViewModel by viewModels()

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
        sesionViewModel.recargarYValidar()
        setContent {
            PulgaTecTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaRaiz(
                        sesionViewModel = sesionViewModel,
                        avisosConversacionViewModel = avisosConversacionViewModel,
                        tokenRecuperacion = tokenRecuperacion,
                        recuperando = recuperando,
                        registrando = registrando,
                        onEstadoRecuperacion = { token, activa ->
                            tokenRecuperacion = token
                            recuperando = activa
                        },
                        onEstadoRegistro = { activa -> registrando = activa }
                    )
                }
            }
        }
    }
}
