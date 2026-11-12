package com.ejemplo.publicarproducto.integracion

import androidx.test.platform.app.InstrumentationRegistry
import com.ejemplo.publicarproducto.model.remote.AuthApi
import com.ejemplo.publicarproducto.model.remote.ConversacionApi
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.fail
import org.junit.AssumptionViolatedException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Entorno de las pruebas de integración contra el BACKEND REAL.
 *
 * Esta clase NO usa MockWebServer: habla con el servidor real de
 * `backend-user-api` (Spring Boot) mediante las mismas interfaces Retrofit
 * que usa la aplicación (`ProductoApi`, `AuthApi`, `ConversacionApi`).
 *
 * Reglas de seguridad (para que la suite nunca se ejecute por accidente
 * contra un entorno real de producción):
 *
 * 1. La suite está DESHABILITADA si no se pasa la URL del backend. Las
 *    pruebas se marcan como omitidas (no fallan) con un mensaje que explica
 *    cómo habilitarlas.
 * 2. Solo se admiten hosts de desarrollo (`10.0.2.2`, `localhost`,
 *    `127.0.0.1`, `10.0.3.2`). Cualquier otro host exige además el argumento
 *    `permitirHostRemoto=true` de forma explícita.
 * 3. NO hay ninguna credencial escrita en el código: el correo y la
 *    contraseña llegan como argumentos del runner de instrumentación.
 * 4. Las pruebas que ESCRIBEN datos (registro, carrito, pedido, consulta,
 *    favorito) exigen además `permiteEscrituraReal=true`.
 *
 * Configuración (tarea Gradle `integracionRealDebugAndroidTest`):
 *
 * ```
 * .\gradlew.bat integracionRealDebugAndroidTest `
 *   -PurlBaseReal=http://10.0.2.2:9000/ `
 *   -PpermitirEscrituraReal=true
 * ```
 *
 * El correo y la contraseña se leen de las variables de entorno
 * `PULGATEC_CORREO_REAL` y `PULGATEC_PASSWORD_REAL` para que no queden
 * escritos en el historial de la línea de comandos.
 */
object EntornoBackendReal {

    /** URL base del backend real (argumento `urlBaseReal`). */
    const val CLAVE_URL = "urlBaseReal"

    /** Correo de la cuenta de pruebas (argumento `correoReal`). */
    const val CLAVE_CORREO = "correoReal"

    /** Contraseña de la cuenta de pruebas (argumento `passwordReal`). */
    const val CLAVE_PASSWORD = "passwordReal"

    /**
     * Correo de la cuenta vendedora de pruebas (argumento `correoVendedorReal`).
     *
     * Las pruebas que publican productos necesitan una cuenta con rol
     * VENDEDOR: el backend no permite publicar a un CLIENTE.
     */
    const val CLAVE_CORREO_VENDEDOR = "correoVendedorReal"

    /** Contraseña de la cuenta vendedora de pruebas (argumento `passwordVendedorReal`). */
    const val CLAVE_PASSWORD_VENDEDOR = "passwordVendedorReal"

    /** Permite las pruebas que escriben datos (argumento `permiteEscrituraReal`). */
    const val CLAVE_ESCRITURA = "permiteEscrituraReal"

    /** Permite apuntar a un host que no sea de desarrollo (argumento `permitirHostRemoto`). */
    const val CLAVE_HOST_REMOTO = "permitirHostRemoto"

    /** Identificador de un producto existente (argumento `idProductoReal`). */
    const val CLAVE_ID_PRODUCTO = "idProductoReal"

    /** Identificador de un vendedor existente (argumento `idVendedorReal`). */
    const val CLAVE_ID_VENDEDOR = "idVendedorReal"

    /** Identificador de un pedido propio existente (argumento `idPedidoReal`). */
    const val CLAVE_ID_PEDIDO = "idPedidoReal"

    private val argumentos: Map<String, String> = run {
        val bundle = InstrumentationRegistry.getArguments()
        bundle.keySet().associateWith { clave -> bundle.getString(clave).orEmpty() }
    }

    /** URL base configurada; vacía cuando la suite está deshabilitada. */
    val urlBase: String = argumentos[CLAVE_URL]?.trim().orEmpty()

    /** Correo de la cuenta de pruebas; vacío si no se configuró. */
    val correo: String = argumentos[CLAVE_CORREO]?.trim().orEmpty()

    /** Contraseña de la cuenta de pruebas; vacía si no se configuró. */
    val password: String = argumentos[CLAVE_PASSWORD].orEmpty()

    /** Correo de la cuenta vendedora de pruebas; vacío si no se configuró. */
    val correoVendedor: String = argumentos[CLAVE_CORREO_VENDEDOR]?.trim().orEmpty()

    /** Contraseña de la cuenta vendedora de pruebas; vacío si no se configuró. */
    val passwordVendedor: String = argumentos[CLAVE_PASSWORD_VENDEDOR].orEmpty()

    /** Identificador de producto existente usado por las pruebas que lo necesitan. */
    val idProducto: Long? = argumentos[CLAVE_ID_PRODUCTO]?.trim()?.toLongOrNull()

    /** Identificador de vendedor existente usado por las pruebas que lo necesitan. */
    val idVendedor: Long? = argumentos[CLAVE_ID_VENDEDOR]?.trim()?.toLongOrNull()

    /** Identificador de pedido propio existente usado por las pruebas que lo necesitan. */
    val idPedido: Long? = argumentos[CLAVE_ID_PEDIDO]?.trim()?.toLongOrNull()

    /** Las pruebas que escriben datos solo corren con este permiso explícito. */
    val escriturasPermitidas: Boolean =
        argumentos[CLAVE_ESCRITURA]?.trim().equals("true", ignoreCase = true)

    private val hostRemotoPermitido: Boolean =
        argumentos[CLAVE_HOST_REMOTO]?.trim().equals("true", ignoreCase = true)

    /** Hosts de desarrollo: el emulador usa `10.0.2.2` para el localhost del equipo. */
    private val HOSTS_DE_DESARROLLO =
        setOf("10.0.2.2", "localhost", "127.0.0.1", "10.0.3.2", "::1")

    @Volatile
    private var tokenSesion: String? = null

    /** Token vigente de la sesión de pruebas, si la hay. */
    fun token(): String? = tokenSesion

    /** Fija (o borra) el token que se envía en la cabecera `Authorization`. */
    fun establecerToken(token: String?) {
        tokenSesion = token
    }

    /**
     * Motivo por el que la suite no puede ejecutarse, o `null` si puede.
     *
     * Se usa para explicar con claridad por qué una prueba queda omitida.
     */
    fun motivoDeshabilitada(): String? {
        if (urlBase.isBlank()) {
            return "Las pruebas de integración con el backend real están deshabilitadas: " +
                "falta el argumento '$CLAVE_URL'. " +
                "Ejecuta la tarea 'integracionRealDebugAndroidTest' con -P$CLAVE_URL=<url> " +
                "para habilitarlas."
        }
        if (hostDe(urlBase) == null) {
            return "La URL del backend real no es válida: '$urlBase'."
        }
        if (hostDe(urlBase) !in HOSTS_DE_DESARROLLO && !hostRemotoPermitido) {
            return "El backend real '$urlBase' no es un host de desarrollo " +
                "($HOSTS_DE_DESARROLLO.joinToString()). " +
                "Para apuntar a otro host hay que pasar '$CLAVE_HOST_REMOTO=true' de forma explícita."
        }
        return null
    }

    /**
     * Omite la prueba (con el motivo) si la suite no está habilitada.
     *
     * Se usa [AssumptionViolatedException] para que el motivo de la omisión
     * quede visible en el informe de pruebas.
     */
    fun assumeHabilitada() {
        val motivo = motivoDeshabilitada()
        if (motivo != null) throw AssumptionViolatedException(motivo)
    }

    /** Omite la prueba si la suite está habilitada pero faltan las credenciales. */
    fun assumeCredenciales() {
        assumeHabilitada()
        if (correo.isBlank() || password.isBlank()) {
            throw AssumptionViolatedException(
                "Faltan las credenciales de la cuenta de pruebas: se necesitan los argumentos " +
                    "'$CLAVE_CORREO' y '$CLAVE_PASSWORD'."
            )
        }
    }

    /** Omite la prueba si no se autorizó escribir datos en el servidor. */
    fun assumeEscrituraPermitida() {
        assumeCredenciales()
        if (!escriturasPermitidas) {
            throw AssumptionViolatedException(
                "Las pruebas que escriben datos están deshabilitadas: falta el argumento " +
                    "'$CLAVE_ESCRITURA=true'."
            )
        }
    }

    /**
     * Omite la prueba si no se configuraron las credenciales de la cuenta
     * vendedora.
     *
     * Publicar un producto exige rol VENDEDOR en el backend: sin esta cuenta
     * las pruebas que necesitan un producto propio no pueden ejecutarse.
     */
    fun assumeCredencialesVendedor() {
        assumeEscrituraPermitida()
        if (correoVendedor.isBlank() || passwordVendedor.isBlank()) {
            throw AssumptionViolatedException(
                "Faltan las credenciales de la cuenta vendedora de pruebas: se necesitan los " +
                    "argumentos '$CLAVE_CORREO_VENDEDOR' y '$CLAVE_PASSWORD_VENDEDOR' " +
                    "(variables PULGATEC_CORREO_VENDEDOR_REAL y PULGATEC_PASSWORD_VENDEDOR_REAL)."
            )
        }
    }

    /** Omite la prueba si no se indicó el identificador que necesita. */
    fun assumeId(clave: String, valor: Long?) {
        if (valor == null) {
            throw AssumptionViolatedException(
                "Falta el argumento '$clave' con el identificador que usa esta prueba."
            )
        }
    }

    /**
     * Comprueba que el servidor real responde.
     *
     * Si no está disponible la prueba FALLA (no se omite): el entorno se
     * pidió de forma explícita y hay que avisar con claridad de que el
     * servidor no está en pie, en lugar de dar un falso "todo correcto".
     */
    fun assumeServidorDisponible() {
        assumeHabilitada()
        try {
            runBlocking {
                val respuesta = apiProducto.consultarPlanPremium()
                if (!respuesta.isSuccessful) {
                    fail(
                        "El backend real respondió HTTP ${respuesta.code()} en $urlBase " +
                            "a GET /api/planes/premium. La integración no se puede verificar."
                    )
                }
            }
        } catch (e: IOException) {
            fail(
                "El backend real NO está disponible en $urlBase: ${e.message}. " +
                    "Levanta el servidor (o corrige la URL) y vuelve a ejecutar la prueba."
            )
        }
    }

    private val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { cadena ->
                val original = cadena.request()
                val token = tokenSesion
                val peticion = if (token.isNullOrBlank()) {
                    original
                } else {
                    original.newBuilder().header("Authorization", "Bearer $token").build()
                }
                cadena.proceed(peticion)
            }
            .build()
    }

    /**
     * Construye el cliente Retrofit contra el backend real.
     *
     * Antes de construirlo se comprueba que la suite está habilitada: si no,
     * la prueba se OMITE (con el motivo) en lugar de fallar con un error de
     * configuración ("baseUrl must not be empty"). Esto importa porque el
     * cliente se construye también en el `@Before` de las pruebas, que se
     * ejecuta antes que el propio método de prueba.
     */
    private fun retrofit(): Retrofit {
        assumeHabilitada()
        return Retrofit.Builder()
            .baseUrl(urlBaseNormalizada())
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** Interfaz real de productos, pedidos, carrito, favoritos y notificaciones. */
    val apiProducto: ProductoApi by lazy { retrofit().create(ProductoApi::class.java) }

    /** Interfaz real de autenticación. */
    val apiAuth: AuthApi by lazy { retrofit().create(AuthApi::class.java) }

    /** Interfaz real de consultas y conversaciones. */
    val apiConversacion: ConversacionApi by lazy { retrofit().create(ConversacionApi::class.java) }

    private fun urlBaseNormalizada(): String =
        if (urlBase.endsWith("/")) urlBase else "$urlBase/"

    private fun hostDe(url: String): String? = runCatching { URI(url).host }.getOrNull()
}
