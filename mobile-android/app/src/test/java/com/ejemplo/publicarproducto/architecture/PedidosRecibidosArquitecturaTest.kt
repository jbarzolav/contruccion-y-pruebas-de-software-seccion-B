package com.ejemplo.publicarproducto.architecture

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grupo D-4 - garantías de arquitectura de "Pedidos recibidos" (HU 48/HU 49).
 *
 * La migración de D-4 consiste en que el ViewModel deje de consultar
 * `NotificacionDao` y use `NotificacionesRepositorio.observarAvisos`. Estas
 * pruebas verifican esa garantía sobre el propio código fuente: son la
 * prueba más directa de que NO hay acceso directo al DAO, a la base de datos
 * ni a HTTP desde la capa de presentación, y de que la pantalla solo
 * presenta el estado del ViewModel.
 *
 * Se leen los archivos fuente del módulo: si algún día se movieran de
 * sitio, la prueba debe FALLAR (no se omite en silencio).
 *
 * La resolución del archivo es INDEPENDIENTE del paquete: se busca por
 * nombre en todo el árbol de `src/main/java`. Así la prueba sigue siendo
 * válida después de reorganizar `ui/` en `view/` y `viewmodel/`, que es
 * exactamente lo que exige la migración MVVM.
 */
class PedidosRecibidosArquitecturaTest {

    @Test
    fun elViewModelNoConsultaElDaoDeNotificaciones() {
        val fuente = fuente("PedidosRecibidosViewModel.kt")

        assertFalse(
            "El ViewModel no debe acceder al DAO de notificaciones: use " +
                "NotificacionesRepositorio.observarAvisos(usuarioId)",
            fuente.contains("notificacionDao")
        )
    }

    @Test
    fun elViewModelNoAccedeALaBaseDeDatosNiAHttp() {
        val fuente = fuente("PedidosRecibidosViewModel.kt")

        assertFalse(
            "El ViewModel no debe abrir la base de datos (contenedor.bd)",
            fuente.contains("contenedor.bd")
        )
        assertFalse(
            "El ViewModel no debe usar el cliente HTTP",
            fuente.contains("UserApiClient")
        )
        assertFalse(
            "El ViewModel no debe construir clientes Retrofit",
            fuente.contains("Retrofit")
        )
        assertFalse(
            "El ViewModel no debe mapear entidades locales: el mapeo es del repositorio",
            fuente.contains("Mapeadores")
        )
    }

    @Test
    fun elViewModelObtieneLosDatosDelRepositorio() {
        val fuente = fuente("PedidosRecibidosViewModel.kt")

        assertTrue(
            "Los avisos deben obtenerse con notificaciones.observarAvisos(usuarioId)",
            fuente.contains("notificaciones.observarAvisos(usuarioId)")
        )
        assertTrue(
            "Los pedidos deben obtenerse del repositorio de pedidos",
            fuente.contains("contenedor.pedidos.")
        )
    }

    @Test
    fun laPantallaSoloPresentaElEstadoDelViewModel() {
        val fuente = fuente("PedidosRecibidosScreen.kt")

        assertFalse(
            "La pantalla no debe acceder al DAO de notificaciones",
            fuente.contains("notificacionDao")
        )
        assertFalse(
            "La pantalla no debe resolver el contenedor de datos",
            fuente.contains("ProveedorDatos") || fuente.contains("contenedor")
        )
        assertFalse(
            "La pantalla no debe usar el cliente HTTP",
            fuente.contains("UserApiClient") || fuente.contains("Retrofit")
        )
        assertTrue(
            "La pantalla debe mostrar los avisos del ViewModel",
            fuente.contains("viewModel.notificaciones")
        )
    }

    /**
     * Localiza un archivo del código fuente por NOMBRE, en cualquier paquete.
     *
     * No fija la ruta del paquete: la reorganización de `ui/` a `view/` y
     * `viewmodel/` no invalida la prueba. Falla con diagnóstico si no se
     * encuentra el archivo o si hay más de uno con el mismo nombre.
     */
    private fun fuente(nombre: String): String {
        val raiz = raizFuentes()
        val encontrados = raiz.walkTopDown()
            .filter { it.isFile && it.name == nombre }
            .toList()

        if (encontrados.isEmpty()) {
            throw AssertionError(
                "No se encontró el archivo $nombre bajo ${raiz.path} " +
                    "(se buscó desde ${System.getProperty("user.dir")}). " +
                    "La comprobación no se omite: si el archivo se renombró, " +
                    "hay que actualizar esta prueba."
            )
        }
        if (encontrados.size > 1) {
            throw AssertionError(
                "Se encontró más de un archivo llamado $nombre: " +
                    encontrados.joinToString { it.path } +
                    ". La prueba no puede decidir cuál comprobar."
            )
        }
        return encontrados.single().readText()
    }

    /**
     * Localiza el directorio `src/main/java` del módulo subiendo desde el
     * directorio de trabajo hasta encontrarlo.
     */
    private fun raizFuentes(): File {
        var directorio: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (directorio != null) {
            val candidato = File(directorio, "src/main/java")
            if (candidato.isDirectory) return candidato
            directorio = directorio.parentFile
        }
        throw AssertionError(
            "No se encontró el directorio src/main/java desde " +
                "${System.getProperty("user.dir")}"
        )
    }
}
