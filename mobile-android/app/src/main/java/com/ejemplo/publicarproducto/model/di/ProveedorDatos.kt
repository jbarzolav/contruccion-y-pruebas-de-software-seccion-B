package com.ejemplo.publicarproducto.model.di

import android.content.Context
import com.ejemplo.publicarproducto.model.local.MetadatoSincronizacionDao
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.remote.AdminApiClient
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import com.ejemplo.publicarproducto.model.remote.UserApiClient
import com.ejemplo.publicarproducto.model.repository.AdminRepositorio
import com.ejemplo.publicarproducto.model.repository.AuthRepositorio
import com.ejemplo.publicarproducto.model.repository.CarritoRepositorio
import com.ejemplo.publicarproducto.model.repository.ChatbotRepositorio
import com.ejemplo.publicarproducto.model.repository.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.ConversacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.FavoritosRepositorio
import com.ejemplo.publicarproducto.model.repository.MetadatosRepositorio
import com.ejemplo.publicarproducto.model.repository.MetadatosRepositorioRoom
import com.ejemplo.publicarproducto.model.repository.MetricasRepositorio
import com.ejemplo.publicarproducto.model.repository.NotificacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.PedidosRepositorio
import com.ejemplo.publicarproducto.model.repository.ProgramacionSincronizacionRepositorio
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import com.ejemplo.publicarproducto.model.repository.UmbralStockRepositorio
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.model.sync.MonitorConectividad
import com.ejemplo.publicarproducto.model.sync.ServicioSincronizacion

/**
 * Contenedor de la capa de datos local.
 *
 * Se crea una sola vez por proceso. Los repositorios comparten la misma base
 * de Room, la misma cola y el mismo cliente Retrofit existente: no hay una
 * segunda implementación de red.
 */
class ContenedorDatos(
    val contexto: Context,
    val bd: PulgaTecDatabase,
    val cola: ColaOperacionesRepositorio,
    val notificaciones: NotificacionesRepositorio,
    val pedidos: PedidosRepositorio,
    val metricas: MetricasRepositorio,
    val umbrales: UmbralStockRepositorio,
    val metadatos: MetadatoSincronizacionDao,
    val conectividad: MonitorConectividad,
    val sesion: SessionManager,
    val sincronizacion: ServicioSincronizacion,
    // HU 21/22/23/26/27/28/29/30 - autenticación, sesión y perfil (Spring).
    // Valor por defecto para que las pruebas que arman el contenedor a mano
    // sigan compilando sin declararlo.
    val auth: AuthRepositorio = AuthRepositorio(UserApiClient.authApi, sesion),
    // HU 23/HU 24 - roles y auditoría (Django), independiente de auth.
    val admin: AdminRepositorio = AdminRepositorio(AdminApiClient.api),
    // HU 01-HU 09, HU 18, HU 19 - catálogo, publicaciones y detalles.
    // Valor por defecto para que las pruebas que arman el contenedor a mano
    // sigan compilando sin declararlo.
    val productos: ProductosRepositorio = ProductosRepositorio(UserApiClient.productoApi) {
        sesion.obtenerUsuarioId()
    },
    // HU 31/HU 33/HU 35 - favoritos del cliente, aparte del catálogo.
    val favoritos: FavoritosRepositorio = FavoritosRepositorio(UserApiClient.productoApi),
    // HU 11/HU 12/HU 13/HU 14 - carrito de compras, en línea (sin Room).
    val carrito: CarritoRepositorio = CarritoRepositorio(UserApiClient.productoApi),
    // HU 16/HU 17 - chatbot en línea (sin Room, sin cola).
    val chatbot: ChatbotRepositorio = ChatbotRepositorio(UserApiClient.productoApi),
    // HU 38/HU 39 - consultas y conversaciones en línea (sin Room, sin cola).
    val conversaciones: ConversacionesRepositorio = ConversacionesRepositorio(UserApiClient.conversacionApi),
) {
    /** API de Model para solicitar/cancelar la sincronización persistente. */
    val programacionSincronizacion: ProgramacionSincronizacionRepositorio =
        ProgramacionSincronizacionRepositorio(contexto, cola)

    /** Adaptador derivado del DAO existente para conservar compatibilidad con los contenedores de pruebas. */
    val metadatosRepositorio: MetadatosRepositorio = MetadatosRepositorioRoom(metadatos)
}

/**
 * Localizador de servicios de la capa de datos.
 *
 * Se usa en lugar de un contenedor de inyección para no añadir dependencias
 * ni alterar la arquitectura existente de la aplicación. En las pruebas se
 * puede sustituir la instancia con `fijar`.
 */
object ProveedorDatos {

    @Volatile
    private var instancia: ContenedorDatos? = null

    @Volatile
    private var contexto: Context? = null

    /**
     * Modo pruebas: el proveedor NO reconstruye el contenedor real.
     *
     * El contenedor real apunta al backend de producción (`10.0.2.2:9000` de
     * Spring y `10.0.2.2:8001` de Django). Si una prueba lo reconstruye por
     * error, sus peticiones salen del emulador hacia el servidor de
     * desarrollo y la suite depende de que ese servidor esté encendido.
     * En modo pruebas, [actual] falla con un mensaje explícito en lugar de
     * hacer esas peticiones.
     *
     * Lo activa [fijar], que solo usan las pruebas: en la aplicación el
     * valor siempre es `false` y el comportamiento no cambia.
     */
    @Volatile
    private var modoPruebas = false

    /** Crea (o reutiliza) el contenedor de datos de la aplicación. */
    fun inicializar(context: Context): ContenedorDatos {
        contexto = context.applicationContext
        return actual
    }

    /** Contenedor de datos activo. */
    val actual: ContenedorDatos
        get() = instancia ?: synchronized(this) {
            instancia ?: if (modoPruebas) {
                throw IllegalStateException(
                    "ProveedorDatos está en modo pruebas y no hay ningún contenedor " +
                        "fijado: no se puede construir el contenedor real porque " +
                        "apuntaría al backend de producción. Llama a " +
                        "ProveedorDatos.fijar(contenedor) antes de usar `actual`."
                )
            } else {
                crear().also { instancia = it }
            }
        }

    /**
     * Sustituye el contenedor activo (solo pruebas).
     *
     * Fijar un contenedor (o liberarlo con `null`) deja el proveedor en modo
     * pruebas: a partir de ese momento [actual] nunca reconstruye el
     * contenedor real, aunque se haya liberado el contenedor de pruebas.
     */
    fun fijar(contenedor: ContenedorDatos?) {
        instancia = contenedor
        modoPruebas = true
    }

    private fun crear(): ContenedorDatos {
        val app = contexto
            ?: throw IllegalStateException(
                "ProveedorDatos no está inicializado: falta llamar a inicializar(context)."
            )
        val sesion = SessionManager(app)
        val api: ProductoApi = UserApiClient.productoApi
        val bd = PulgaTecDatabase.obtener(app)
        val cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        val metadatos = bd.metadatoSincronizacionDao()
        val notificaciones = NotificacionesRepositorio(bd, api, cola)
        val pedidos = PedidosRepositorio(bd, api, cola)
        val metricas = MetricasRepositorio(bd, api)
        val umbrales = UmbralStockRepositorio(bd, api, cola, notificaciones)
        return ContenedorDatos(
            contexto = app,
            bd = bd,
            cola = cola,
            notificaciones = notificaciones,
            pedidos = pedidos,
            metricas = metricas,
            umbrales = umbrales,
            metadatos = metadatos,
            conectividad = MonitorConectividad(app),
            sesion = sesion,
            sincronizacion = ServicioSincronizacion(
                bd = bd,
                api = api,
                cola = cola,
                notificaciones = notificaciones,
                pedidos = pedidos,
                umbrales = umbrales,
                metadatos = metadatos,
                sesion = sesion
            ),
            auth = AuthRepositorio(UserApiClient.authApi, sesion),
            admin = AdminRepositorio(AdminApiClient.api),
            productos = ProductosRepositorio(api) { sesion.obtenerUsuarioId() },
            favoritos = FavoritosRepositorio(api),
            carrito = CarritoRepositorio(api),
            chatbot = ChatbotRepositorio(api),
            conversaciones = ConversacionesRepositorio(UserApiClient.conversacionApi)
        )
    }
}
