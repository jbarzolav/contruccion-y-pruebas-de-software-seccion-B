package com.ejemplo.publicarproducto.integracion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.LoginRequest
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.repository.ChatbotRepositorio
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grupo C - Integración REAL con el backend `backend-user-api`.
 *
 * Estas pruebas NO usan MockWebServer: hablan con el servidor real de
 * Spring Boot a través de los mismos repositorios e interfaces Retrofit que
 * usa la aplicación Android.
 *
 * Todas las llamadas son de LECTURA: no crean ni modifican datos.
 *
 * Se omiten (y no fallan) cuando la suite no está habilitada; ver
 * [EntornoBackendReal] para la configuración.
 */
@RunWith(AndroidJUnit4::class)
class PruebasIntegracionLecturaBackendReal {

    private val api get() = EntornoBackendReal.apiProducto

    private val productos: ProductosRepositorio by lazy {
        ProductosRepositorio(api) { 0L }
    }

    private val chatbot: ChatbotRepositorio by lazy {
        ChatbotRepositorio(api)
    }

    @Test
    fun elCatalogoSeConsultaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        val catalogo = assertEsExito(productos.listarCatalogo(), "listarCatalogo")

        assertNotNull("El catálogo debe venir del backend real", catalogo)
    }

    @Test
    fun laBusquedaPorNombreSeConsultaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        val resultados = assertEsExito(productos.buscar(nombre = "a"), "buscar")

        assertNotNull("La búsqueda debe venir del backend real", resultados)
    }

    /**
     * HU 09: los cuatro criterios de orden que ofrece la pantalla tienen que
     * ser aceptados por el backend real. Si el contrato cambiara, el backend
     * respondería 400 y la lista no se ordenaría.
     */
    @Test
    fun losCuatroCriteriosDeOrdenSeAceptanEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        val criterios = listOf("precio_asc", "precio_desc", "nombre_asc", "nombre_desc")
        for (criterio in criterios) {
            assertEsExito(
                productos.buscar(nombre = "a", sort = criterio),
                "buscar con sort=$criterio"
            )
        }
    }

    @Test
    fun elPlanPremiumVieneDelBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        val plan = assertEsExito(productos.consultarPlanPremium(), "consultarPlanPremium")

        assertTrue(
            "El plan premium debe traer el nombre del plan",
            !plan.nombre.isNullOrBlank()
        )
        assertTrue(
            "El plan premium debe traer sus beneficios",
            !plan.beneficios.isNullOrEmpty()
        )
    }

    @Test
    fun lasRecomendacionesVienenDelBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        val recomendaciones =
            assertEsExito(productos.listarRecomendaciones(termino = "a"), "listarRecomendaciones")

        assertNotNull("Las recomendaciones deben venir del backend real", recomendaciones)
    }

    @Test
    fun elChatbotDeRecomendacionesRespondeEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        val respuesta =
            assertEsExito(chatbot.recomendarProductos("recomienda un producto"), "recomendarProductos")

        assertTrue(
            "El chatbot debe responder con un mensaje",
            !respuesta.mensaje.isNullOrBlank()
        )
    }

    @Test
    fun elChatbotDeConsultasTecnicasRespondeEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        val respuesta = assertEsExito(
            chatbot.consultarComponenteTecnico("que es un resistor"),
            "consultarComponenteTecnico"
        )

        assertTrue(
            "El chatbot debe responder con un mensaje",
            !respuesta.mensaje.isNullOrBlank()
        )
    }

    @Test
    fun laDisponibilidadDeUnProductoVieneDelBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        val idProducto = EntornoBackendReal.idProducto
        EntornoBackendReal.assumeId(EntornoBackendReal.CLAVE_ID_PRODUCTO, idProducto)

        val disponibilidad =
            assertEsExito(productos.obtenerDisponibilidad(idProducto!!), "obtenerDisponibilidad")

        assertEquals(
            "La disponibilidad debe corresponder al producto consultado",
            idProducto,
            disponibilidad.idProducto
        )
    }

    @Test
    fun lasResenasDeUnProductoVienenDelBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        val idProducto = EntornoBackendReal.idProducto
        EntornoBackendReal.assumeId(EntornoBackendReal.CLAVE_ID_PRODUCTO, idProducto)

        val resenas =
            assertEsExito(productos.listarResenasProducto(idProducto!!), "listarResenasProducto")

        assertNotNull("Las reseñas deben venir del backend real", resenas)
    }

    @Test
    fun elResumenDeCalificacionesDelVendedorVieneDelBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        val idVendedor = EntornoBackendReal.idVendedor
        EntornoBackendReal.assumeId(EntornoBackendReal.CLAVE_ID_VENDEDOR, idVendedor)

        val resumen = assertEsExito(
            productos.resumenCalificacionesVendedor(idVendedor!!),
            "resumenCalificacionesVendedor"
        )

        assertEquals(
            "El resumen debe corresponder al vendedor consultado",
            idVendedor,
            resumen.vendedorId
        )
    }

    /**
     * HU 07 / HU 34: un producto inexistente tiene que devolver 404 y no una
     * respuesta vacía que la pantalla interpretaría como "producto válido".
     */
    @Test
    fun unProductoInexistenteDevuelve404EnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        val respuesta = api.obtenerProducto(999_999_999L)

        assertEquals("Un producto inexistente debe devolver 404", 404, respuesta.code())
    }

    /**
     * Verifica el rechazo por rol contra el backend real: una cuenta CLIENTE
     * no puede crear publicaciones (debe responder 403).
     *
     * La prueba solo se ejecuta si la cuenta configurada es realmente de
     * rol CLIENTE: con una cuenta vendedora el alta SI se aceptaría y la
     * prueba dejaría un producto publicado.
     */
    @Test
    fun unaCuentaClienteNoPuedeCrearPublicacionesEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()

        val token = iniciarSesionDePruebas()
        EntornoBackendReal.establecerToken(token)

        val sesion = EntornoBackendReal.apiAuth.obtenerSesion()
        assertTrue(
            "La sesión de pruebas debe poder consultarse (HTTP ${sesion.code()})",
            sesion.isSuccessful
        )
        val rol = sesion.body()?.rol
        if (rol != "CLIENTE") {
            throw AssumptionViolatedException(
                "Esta comprobación exige una cuenta de rol CLIENTE y la configurada es '$rol'."
            )
        }

        val respuesta = api.crearProducto(
            ProductoRequest(
                nombre = "Producto de prueba de integración",
                descripcion = "No debe publicarse: la cuenta es cliente.",
                precio = 1.0,
                stock = 1,
                categoria = "Prueba",
                estado = "DISPONIBLE",
                imagenUrl = "https://via.placeholder.com/300",
                vendedorId = 1L
            )
        )

        assertEquals(
            "Una cuenta cliente no puede crear publicaciones (debe ser 403)",
            403,
            respuesta.code()
        )
    }

    /** Inicia sesión con la cuenta de pruebas y devuelve el token. */
    private suspend fun iniciarSesionDePruebas(): String {
        val respuesta = EntornoBackendReal.apiAuth.login(
            LoginRequest(
                correo = EntornoBackendReal.correo,
                password = EntornoBackendReal.password
            )
        )
        assertTrue(
            "La cuenta de pruebas debe poder iniciar sesión (HTTP ${respuesta.code()})",
            respuesta.isSuccessful
        )
        val cuerpo = respuesta.body()
        assertNotNull("La respuesta del login debe traer el token", cuerpo)
        return cuerpo!!.token
    }

    /** Comprueba que la llamada al backend real fue exitosa y devuelve su valor. */
    private fun <T> assertEsExito(resultado: ResultadoApi<T>, operacion: String): T =
        when (resultado) {
            is ResultadoApi.Exito -> resultado.valor
            is ResultadoApi.Error -> throw AssertionError(
                "La operación '$operacion' falló contra el backend real: " +
                    "HTTP ${resultado.codigo ?: "sin respuesta"}, mensaje: ${resultado.mensaje}"
            )
        }
}
