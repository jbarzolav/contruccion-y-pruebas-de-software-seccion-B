package com.ejemplo.publicarproducto.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verificación estática de la arquitectura MVVM (R1, R2, R3, R4, R5, R6, R7,
 * R9 y R10).
 *
 * La regla del proyecto es:
 *
 *     View  →  ViewModel  →  Repository  →  Room / Retrofit
 *
 * Estas pruebas comprueban esa regla sobre el propio código fuente del
 * módulo: son la evidencia más directa de que la capa de presentación NO
 * accede a la base de datos, al cliente HTTP ni al planificador de
 * sincronización, y de que el sondeo de HU39 tiene un único responsable.
 *
 * Se leen los archivos fuente: si algún día se movieran de sitio, la prueba
 * debe FALLAR (no se omite en silencio).
 */
class CapasMvvmTest {

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    /** Raíz del código fuente del módulo (`src/main/java`). */
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

    /** Paquete base de la aplicación. */
    private fun paqueteBase(): File {
        val raiz = raizFuentes()
        val candidatos = raiz.walkTopDown()
            .filter { it.isDirectory && it.name == "publicarproducto" }
            .toList()
        if (candidatos.isEmpty()) {
            throw AssertionError("No se encontró el paquete base 'publicarproducto' bajo ${raiz.path}")
        }
        return candidatos.first()
    }

    /** Archivos `.kt` de una carpeta de la arquitectura. */
    private fun archivosDe(carpeta: String): List<File> {
        val directorio = File(paqueteBase(), carpeta)
        if (!directorio.isDirectory) {
            throw AssertionError(
                "No se encontró la carpeta '$carpeta' en ${paqueteBase().path}. " +
                    "La comprobación no se omite."
            )
        }
        return directorio.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.sortedBy { it.path }.toList()
    }

    /**
     * Código sin comentarios.
     *
     * Los comentarios se eliminan para que la comprobación examine el CÓDIGO
     * real y no la documentación que explica por qué la capa no accede a
     * Room (esas frases aparecen en los KDoc de los ViewModels).
     */
    private fun codigoSinComentarios(archivo: File): String {
        val original = archivo.readText()
        // Se protegen las URLs (`http://`) antes de quitar los comentarios
        // de línea para no truncar la línea en el `//` del esquema.
        val protegido = original.replace("://", "://__")
        val sinBloques = protegido.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        return sinBloques.lines().joinToString("\n") { linea ->
            val indice = linea.indexOf("//")
            if (indice >= 0) linea.substring(0, indice) else linea
        }
    }

    /** Código (sin comentarios) de todos los archivos de una carpeta. */
    private fun codigoDe(carpeta: String): Map<String, String> =
        archivosDe(carpeta).associate { it.name to codigoSinComentarios(it) }

    /** Busca un archivo por nombre en todo el código fuente. */
    private fun fuente(nombre: String): String {        val raiz = raizFuentes()
        val encontrados = raiz.walkTopDown()
            .filter { it.isFile && it.name == nombre }
            .toList()
        if (encontrados.isEmpty()) {
            throw AssertionError(
                "No se encontró el archivo $nombre bajo ${raiz.path}. " +
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
        return codigoSinComentarios(encontrados.single())
    }

    /**
     * Cuerpo de una función del código fuente (sin comentarios).
     *
     * Permite comprobar **dónde** se toma una decisión y no solo que la
     * cadena exista en algún sitio del archivo: una comprobación que solo
     * busca una cadena suelta puede pasar sin proteger nada.
     */
    private fun cuerpoDe(codigo: String, nombreFuncion: String): String {
        val inicio = codigo.indexOf("fun $nombreFuncion(")
        if (inicio < 0) {
            throw AssertionError(
                "No se encontró la función '$nombreFuncion' en el código fuente. " +
                    "La comprobación no se omite."
            )
        }
        val siguiente = Regex("\\n\\s*(private\\s+)?fun ").find(codigo, inicio + 1)
        return if (siguiente == null) {
            codigo.substring(inicio)
        } else {
            codigo.substring(inicio, siguiente.range.first)
        }
    }

    // ---------------------------------------------------------------------
    // R5 - Los ViewModels no dependen de la capa de presentación
    // ---------------------------------------------------------------------

    @Test
    fun ningunViewModelImportaLaCapaDePresentacion() {
        val ofensores = codigoDe("viewmodel").filterValues { codigo ->
            codigo.contains("import com.ejemplo.publicarproducto.view") ||
                codigo.contains("import com.ejemplo.publicarproducto.view.components")
        }

        assertTrue(
            "R5 - Un ViewModel no puede importar la capa `view` (dependencia invertida): " +
                ofensores.keys,
            ofensores.isEmpty()
        )
    }

    @Test
    fun ningunViewModelAccedeADaoRoomRetrofitNiWorkManager() {
        val prohibidos = listOf(
            "androidx.room" to "Room",
            "PulgaTecDatabase" to "la base de datos",
            "Retrofit" to "Retrofit",
            "UserApiClient" to "el cliente HTTP",
            "WorkManager" to "WorkManager"
        )
        val patronesDao = Regex("\\b\\w*Dao\\b")

        val ofensores = mutableListOf<String>()
        codigoDe("viewmodel").forEach { (nombre, codigo) ->
            prohibidos.forEach { (marcador, que) ->
                if (codigo.contains(marcador)) {
                    ofensores += "$nombre usa $que ($marcador)"
                }
            }
            if (patronesDao.containsMatchIn(codigo)) {
                ofensores += "$nombre accede directamente a un DAO"
            }
        }

        assertTrue(
            "R5 - Los ViewModels deben delegar en los repositorios: " + ofensores,
            ofensores.isEmpty()
        )
    }

    // ---------------------------------------------------------------------
    // Capa de presentación
    // ---------------------------------------------------------------------

    @Test
    fun ningunaVistaAccedeADaoRoomRetrofitNiWorkManager() {
        val prohibidos = listOf(
            "androidx.room" to "Room",
            "PulgaTecDatabase" to "la base de datos",
            "Retrofit" to "Retrofit",
            "UserApiClient" to "el cliente HTTP",
            "WorkManager" to "WorkManager",
            "ProveedorDatos" to "el contenedor de datos"
        )
        val patronesDao = Regex("\\b\\w*Dao\\b")

        val ofensores = mutableListOf<String>()
        codigoDe("view").forEach { (nombre, codigo) ->
            prohibidos.forEach { (marcador, que) ->
                if (codigo.contains(marcador)) {
                    ofensores += "$nombre usa $que ($marcador)"
                }
            }
            if (patronesDao.containsMatchIn(codigo)) {
                ofensores += "$nombre accede directamente a un DAO"
            }
        }

        assertTrue(
            "Las vistas solo presentan el estado del ViewModel: " + ofensores,
            ofensores.isEmpty()
        )
    }

    @Test
    fun lasVistasNoTienenBuclesDeSondeo() {
        val ofensores = mutableListOf<String>()
        codigoDe("view").forEach { (nombre, codigo) ->
            if (codigo.contains("while (true)") || codigo.contains("while(true)")) {
                ofensores += "$nombre tiene un bucle infinito"
            }
            if (codigo.contains("delay(")) {
                ofensores += "$nombre usa delay()"
            }
        }

        assertTrue(
            "R1/R6 - Ninguna vista debe hacer sondeo: el trabajo vive en el ViewModel. " +
                ofensores.joinToString("; "),
            ofensores.isEmpty()
        )
    }

    // ---------------------------------------------------------------------
    // R1/R3 - Un único responsable del sondeo de HU39
    // ---------------------------------------------------------------------

    @Test
    fun elSondeoDeConversacionesViveSoloEnLosViewModels() {
        val conSondeo = codigoDe("viewmodel").filter { (_, codigo) ->
            codigo.contains("while (true)") && codigo.contains("delay(")
        }.keys.sorted()

        assertEquals(
            "R1/R3 - El sondeo de HU39 solo puede vivir en los ViewModel que lo " +
                "declaran; cualquier otro archivo con un bucle de sondeo debe revisarse",
            listOf("AvisosConversacionViewModel.kt", "SondeoPeriodico.kt"),
            conSondeo
        )
    }

    @Test
    fun laPantallaDeConsultasActivaYSuspendeElSondeoSinHacerloElla() {
        val fuente = fuente("ConsultasScreen.kt")

        assertTrue(
            "R1 - La pantalla debe ligar el sondeo al ciclo de vida STARTED",
            fuente.contains("LifecycleStartEffect")
        )
        assertTrue(
            "R1 - La pantalla debe activar el sondeo al entrar",
            fuente.contains("iniciarSondeo()")
        )
        assertTrue(
            "R1 - La pantalla debe detener el sondeo al salir o pasar a segundo plano",
            fuente.contains("detenerSondeo()")
        )
        assertFalse(
            "R1 - La pantalla no debe hacer el sondeo por su cuenta",
            fuente.contains("delay(")
        )
    }

    @Test
    fun elViewModelDeConsultasEsElDuenoDelSondeo() {
        val fuente = fuente("ConsultasViewModel.kt")

        assertTrue(
            "R1/R3 - El ViewModel de Consultas debe usar el sondeo periódico cancelable",
            fuente.contains("SondeoPeriodico(")
        )
        assertTrue("R1 - Debe exponer la activación del sondeo", fuente.contains("fun iniciarSondeo()"))
        assertTrue("R1 - Debe exponer la parada del sondeo", fuente.contains("fun detenerSondeo()"))
    }

    @Test
    fun elViewModelDeAvisosCedeElSondeoALaPantallaDeConsultas() {
        val fuente = fuente("AvisosConversacionViewModel.kt")

        assertTrue(
            "R3 - El ViewModel de avisos debe poder ceder el sondeo",
            fuente.contains("fun pausar()")
        )
        assertTrue(
            "R3 - El ViewModel de avisos debe saber si el sondeo está cedido",
            fuente.contains("fun cedido()")
        )
    }

    @Test
    fun laNavegacionArbitraQuienSondea() {
        val fuente = fuente("AppNavegacion.kt")

        assertTrue(
            "R3 - La navegación debe ceder el sondeo al entrar en Consultas",
            fuente.contains("avisosConversacionViewModel.pausar()")
        )
        assertTrue(
            "R3 - La navegación debe retomar el sondeo al salir de Consultas",
            fuente.contains("avisosConversacionViewModel.iniciar(")
        )
        assertTrue(
            "R3 - La navegación debe delegar la decisión en el coordinador",
            fuente.contains("CoordinadorSondeoAvisos(")
        )
    }

    @Test
    fun elSondeoDelBadgeEstaLigadoAlCicloDeVidaDeLaAplicacion() {
        val navegacion = fuente("AppNavegacion.kt")
        val observador = fuente("ObservadorPrimerPlano.kt")
        val coordinador = fuente("CoordinadorSondeoAvisos.kt")

        assertTrue(
            "R1 - La navegación debe observar el primer plano de la aplicación",
            navegacion.contains("ObservadorPrimerPlano(")
        )
        assertTrue(
            "R1 - El observador debe detectar cuándo la app es visible",
            observador.contains("Lifecycle.Event.ON_START")
        )
        assertTrue(
            "R1 - El observador debe detectar el paso a segundo plano",
            observador.contains("Lifecycle.Event.ON_STOP")
        )
        assertTrue(
            "R1 - La decisión debe usar la regla de arbitraje verificada en la JVM",
            coordinador.contains("debeSonearAvisosDeConversacion(")
        )
        assertTrue(
            "R1 - La navegación debe conectar el observador con el coordinador",
            navegacion.contains("coordinador.onCambioPrimerPlano(")
        )
    }

    @Test
    fun laParadaDelSondeoSeDecideEnElCallbackDelCicloDeVida() {
        val observador = fuente("ObservadorPrimerPlano.kt")
        val coordinador = fuente("CoordinadorSondeoAvisos.kt")
        val navegacion = fuente("AppNavegacion.kt")

        assertTrue(
            "R1 - El callback del ciclo de vida debe pasar por `actualizar` en " +
                "ON_START y en ON_STOP",
            observador.contains("Lifecycle.Event.ON_START -> actualizar(true)") &&
                observador.contains("Lifecycle.Event.ON_STOP -> actualizar(false)")
        )
        assertTrue(
            "R1 - `actualizar` debe notificar el cambio de forma síncrona: " +
                "Compose no garantiza recomponer después de ON_STOP",
            cuerpoDe(observador, "actualizar").contains("alCambiar(")
        )
        assertTrue(
            "R1 - El coordinador debe aplicar la decisión al cambiar el primer plano",
            cuerpoDe(coordinador, "onCambioPrimerPlano").contains("aplicarDecision()")
        )
        assertFalse(
            "R1 - La decisión no puede depender de una recomposición: el estado de " +
                "primer plano no debe leerse desde la composición",
            navegacion.contains("primerPlano.enPrimerPlano")
        )
        assertFalse(
            "R1 - El indicador de primer plano no debe ser estado de Compose: así no " +
                "puede volver a colgar la decisión de una recomposición",
            observador.contains("mutableStateOf")
        )
    }

    @Test
    fun elArbitrajeUsaLaFuncionPuraDeConsultasResponsable() {
        val navegacion = fuente("AppNavegacion.kt")
        val regla = fuente("ArbitrajeSondeo.kt")

        assertTrue(
            "R1/M1 - Debe existir la función pura que decide si la pantalla de " +
                "Consultas es la responsable del sondeo",
            regla.contains("fun consultasSonResponsable(")
        )
        val cuerpo = cuerpoDe(regla, "consultasSonResponsable")
        assertTrue(
            "R1/M1 - La función debe comprobar la pestaña activa",
            cuerpo.contains("IndicesNavegacion.CONSULTAS")
        )
        assertTrue(
            "R1/M1 - La función debe comprobar que la pantalla sigue en pantalla: " +
                "una subpantalla abierta la saca de la composición",
            cuerpo.contains("estado.enPantallaPrincipal")
        )
        assertTrue(
            "R1/M1 - La navegación debe usar esa función, no mirar solo la pestaña",
            navegacion.contains("consultasSonResponsable(estado)")
        )
    }

    @Test
    fun laSalidaDeLaPantallaUsaLaDecisionCentralizada() {
        val navegacion = fuente("AppNavegacion.kt")
        val regla = fuente("ArbitrajeSondeo.kt")

        assertTrue(
            "R1/M2 - Debe existir la decisión centralizada de qué hacer al salir " +
                "de la pantalla autenticada",
            regla.contains("fun accionAlSalirDeLaComposicion(")
        )
        assertTrue(
            "R1/M2 - La navegación debe usar esa decisión en lugar de decidir en línea",
            navegacion.contains("accionAlSalirDeLaComposicion(")
        )
        assertTrue(
            "R1/M2 - Deben existir las dos acciones: pausar (rotación) y limpiar " +
                "(salida de sesión)",
            navegacion.contains("AccionAlSalir.PAUSAR") &&
                navegacion.contains("AccionAlSalir.DETENER")
        )
    }

    @Test
    fun laReglaDeArbitrajeDelSondeoEsUnaFuncionPuraSeparada() {
        val regla = fuente("ArbitrajeSondeo.kt")

        assertTrue(
            "R1/R3 - La regla de arbitraje debe existir como función pura",
            regla.contains("fun debeSonearAvisosDeConversacion(")
        )
    }

    // ---------------------------------------------------------------------
    // R2 - Identidad reactiva
    // ---------------------------------------------------------------------

    @Test
    fun laIdentidadDelUsuarioVieneDeUnaFuenteReactiva() {
        val session = fuente("SessionManager.kt")
        val base = fuente("BaseOfflineViewModel.kt")

        assertTrue(
            "R2 - La sesión debe publicar la identidad activa",
            session.contains("usuarioIdFlow")
        )
        assertTrue(
            "R2 - La base de los ViewModels Offline First debe observar la identidad",
            base.contains("usuarioIdFlow")
        )
        assertTrue(
            "R2 - Debe existir el gancho para limpiar el estado del usuario anterior",
            base.contains("limpiarEstadoDeUsuario")
        )
        assertTrue(
            "R2 - Debe existir el gancho para observar los datos del usuario activo",
            base.contains("observarUsuario")
        )
    }

    @Test
    fun losViewModelsOfflineFirstObservanLaIdentidadDelUsuario() {
        val offline = archivosDe("viewmodel")
            .filter { codigoSinComentarios(it).contains(": BaseOfflineViewModel()") }
            .map { it.name }
            .sorted()

        assertEquals(
            "Los ViewModels Offline First son los que observan datos protegidos",
            listOf(
                "BandejaNotificacionesViewModel.kt",
                "DetallePedidoViewModel.kt",
                "MetricasVentasViewModel.kt",
                "MisPedidosViewModel.kt",
                "PedidosRecibidosViewModel.kt",
                "UmbralesStockViewModel.kt"
            ),
            offline
        )

        offline.forEach { nombre ->
            assertTrue(
                "R2 - $nombre debe activar la observación reactiva de la identidad",
                fuente(nombre).contains("iniciarObservacionesDeUsuario()")
            )
        }
    }

    // ---------------------------------------------------------------------
    // R4 / R8 - Invalidación de sesión centralizada
    // ---------------------------------------------------------------------

    @Test
    fun laInvalidacionDeSesionEstaCentralizadaEnElViewModelDeSesion() {
        val perfil = fuente("PerfilViewModel.kt")

        assertFalse(
            "R4 - PerfilViewModel no debe invalidar la sesión por su cuenta: " +
                "publica el evento y lo gestiona SesionViewModel",
            perfil.contains("invalidarSesion")
        )
        assertTrue(
            "R4 - PerfilViewModel debe publicar el evento de sesión expirada",
            perfil.contains("EventoSesion.SesionExpirada")
        )
    }

    @Test
    fun laPoliticaDeErroresDeAutorizacionEsUnaFuncionPuraSeparada() {
        val politica = fuente("PoliticaErroresAutorizacion.kt")
        val cliente = fuente("RetrofitClient.kt")

        assertTrue(
            "R4 - La política debe decidir el código a notificar",
            politica.contains("fun codigoANotificar(")
        )
        assertTrue(
            "R4 - El interceptor debe usar la política extraída",
            cliente.contains("PoliticaErroresAutorizacion.codigoANotificar(")
        )
    }

    // ---------------------------------------------------------------------
    // R7 - Alcance de los ViewModels
    // ---------------------------------------------------------------------

    @Test
    fun laBandejaUsaElViewModelDeLaActivity() {
        val fuente = fuente("BandejaNotificacionesScreen.kt")

        assertFalse(
            "R7 - La bandeja ya no debe crear su propio ViewModelStore: " +
                "pierde filtros y paginación al salir de la pestaña",
            fuente.contains("ViewModelStoreOwner")
        )
        assertTrue(
            "R7 - La bandeja debe usar el ViewModel de la Activity",
            fuente.contains("viewModel()")
        )
    }

    // ---------------------------------------------------------------------
    // R9 - Parada explícita del observador de conectividad
    // ---------------------------------------------------------------------

    @Test
    fun elObservadorDeConectividadTieneRutaDeParada() {
        val observador = fuente("ObservadorConectividad.kt")
        val aplicacion = fuente("PulgaTecApplication.kt")

        assertTrue(
            "R9 - El observador debe poder detenerse",
            observador.contains("fun detener()")
        )
        assertFalse(
            "R9 - No debe haber un scope global sin dueño",
            observador.contains("GlobalScope")
        )
        assertTrue(
            "R9 - La aplicación debe detener el observador al terminar",
            aplicacion.contains("ObservadorConectividad.detener()")
        )
    }

    // ---------------------------------------------------------------------
    // R10 - Destino seguro de la pantalla principal
    // ---------------------------------------------------------------------

    @Test
    fun laNavegacionUsaUnDestinoSeguroParaIndicesDesconocidos() {
        val navegacion = fuente("AppNavegacion.kt")
        val destinos = fuente("DestinosNavegacion.kt")

        assertTrue(
            "R10 - Debe existir el conjunto de destinos válidos",
            destinos.contains("val TODOS: Set<Int>")
        )
        assertTrue(
            "R10 - Debe existir la función de índice seguro",
            destinos.contains("fun indicePantallaPrincipalSeguro(")
        )
        assertTrue(
            "R10 - La navegación debe usar el índice seguro",
            navegacion.contains("indicePantallaPrincipalSeguro(estado.pestana)")
        )
        assertTrue(
            "R10 - La rama de Premium debe ser explícita",
            navegacion.contains("IndicesNavegacion.PREMIUM ->")
        )
    }
}
