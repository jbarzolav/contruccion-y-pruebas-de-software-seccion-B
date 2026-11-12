package com.ejemplo.publicarproducto.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fase 0 (P0.4) - Aislamiento de red de las pruebas instrumentadas.
 *
 * Las pruebas NUNCA deben hablar con el backend de desarrollo: si lo hacen,
 * la suite depende de que Spring (`:9000`) o Django (`:8001`) estén
 * encendidos en el host y las aserciones dejan de ser deterministas.
 *
 * Estas pruebas comprueban las dos condiciones que lo garantizan:
 *
 * 1. Todos los repositorios de red del contenedor de pruebas apuntan al
 *    servidor HTTP local: cada petición llega físicamente al servidor de
 *    pruebas, con su `Host` local (nunca `10.0.2.2`).
 * 2. Cuando no hay contenedor fijado, `ProveedorDatos` NO reconstruye el
 *    contenedor real: falla con un mensaje explícito en lugar de hacer
 *    peticiones al backend de producción.
 */
@RunWith(AndroidJUnit4::class)
class AislamientoRedPruebasTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesion = SessionManager(contexto)
        servidor = ServidorDePruebas()
    }

    @After
    fun tearDown() {
        sesion.invalidarSesion()
        ProveedorDatos.fijar(null)
        bd.close()
        servidor.cerrar()
    }

    /**
     * Cada repositorio con salida HTTP del contenedor de pruebas habla con
     * el servidor local. Antes de corregir esta fase, `admin` usaba el
     * cliente real de Django (`10.0.2.2:8001`) y sus peticiones nunca
     * llegaban al servidor de pruebas.
     */
    @Test
    fun todosLosRepositoriosDeRedHablanConElServidorLocalDePruebas() = runBlocking {
        val contenedor = ContenedorDePruebas.crear(contexto, bd, sesion, servidor)
        ProveedorDatos.fijar(contenedor)

        // Una respuesta por repositorio, en el mismo orden en que se piden.
        servidor.responder(200, "[]")
        contenedor.admin.listarUsuarios()
        servidor.responder(200, "{}")
        contenedor.auth.obtenerSesion()
        servidor.responder(200, "[]")
        contenedor.productos.listarCatalogo()
        servidor.responder(200, "[]")
        contenedor.favoritos.listarFavoritos()
        servidor.responder(200, "[]")
        contenedor.carrito.obtenerCarrito()
        servidor.responder(200, "{}")
        contenedor.chatbot.recomendarProductos("hola")
        servidor.responder(200, """{"longitudMaxima":500}""")
        contenedor.conversaciones.configuracion()

        val peticiones = servidor.peticiones()
        assertEquals(
            "Cada repositorio debe hacer su petición al servidor local: " +
                servidor.rutasRecibidas(),
            7,
            peticiones.size
        )

        val rutas = peticiones.map { it.path.orEmpty() }
        assertTrue("HU 23 roles (Django): $rutas", rutas.contains("/api/admin/usuarios"))
        assertTrue("Sesión (Spring): $rutas", rutas.contains("/api/auth/me"))
        assertTrue("Catálogo: $rutas", rutas.contains("/api/productos/catalogo"))
        assertTrue("Favoritos: $rutas", rutas.contains("/api/usuarios/me/favoritos"))
        assertTrue("Carrito: $rutas", rutas.contains("/api/carrito/items"))
        assertTrue("Chatbot: $rutas", rutas.contains("/api/chatbot/recomendaciones"))
        assertTrue(
            "Consultas: $rutas",
            rutas.contains("/api/conversaciones/configuracion")
        )

        // La cabecera `Host` lo demuestra: ninguna petición salió hacia el
        // host de desarrollo (10.0.2.2) sino al servidor local de pruebas.
        val hostLocal = "${servidor.servidor.hostName}:${servidor.servidor.port}"
        peticiones.forEach { peticion ->
            assertEquals(
                "La petición ${peticion.path} no fue al servidor local de pruebas",
                hostLocal,
                peticion.getHeader("Host")
            )
        }
    }

    /**
     * Tras liberar el contenedor de pruebas, el proveedor NO reconstruye el
     * contenedor real (que apunta al backend de producción): falla de forma
     * explícita para que la suite no dependa del servidor de desarrollo.
     */
    @Test
    fun trasLiberarElContenedorNoSeReconstruyeElContenedorReal() {
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))
        ProveedorDatos.fijar(null)

        val error = assertThrows(
            IllegalStateException::class.java,
            { ProveedorDatos.actual }
        )
        assertTrue(
            "El mensaje debe explicar que no se puede usar el backend real: ${error.message}",
            error.message?.contains("modo pruebas") == true
        )
    }

    /**
     * Sin contenedor fijado ninguna capa de presentación puede obtener un
     * repositorio: se falla antes de tocar la red.
     */
    @Test
    fun sinContenedorFijadoNoSePuedeObtenerNingunRepositorio() {
        ProveedorDatos.fijar(null)

        assertThrows(IllegalStateException::class.java) { ProveedorDatos.actual.admin }
        assertThrows(IllegalStateException::class.java) { ProveedorDatos.actual.productos }
        assertThrows(IllegalStateException::class.java) { ProveedorDatos.actual.carrito }

        // Y, por supuesto, tampoco salió ninguna petición al servidor real.
        assertEquals(0, servidor.peticionesRecibidas())
    }
}
