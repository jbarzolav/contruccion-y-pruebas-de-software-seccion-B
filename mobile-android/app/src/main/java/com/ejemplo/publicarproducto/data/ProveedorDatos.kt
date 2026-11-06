package com.ejemplo.publicarproducto.data

import android.content.Context
import com.ejemplo.publicarproducto.local.MetadatoSincronizacionDao
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.network.ProductoApi
import com.ejemplo.publicarproducto.network.UserApiClient
import com.ejemplo.publicarproducto.session.SessionManager
import com.ejemplo.publicarproducto.sync.ServicioSincronizacion

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
    val metadatos: MetadatoSincronizacionDao,
    val conectividad: MonitorConectividad,
    val sesion: SessionManager,
    val sincronizacion: ServicioSincronizacion
)

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

    /** Crea (o reutiliza) el contenedor de datos de la aplicación. */
    fun inicializar(context: Context): ContenedorDatos {
        contexto = context.applicationContext
        return actual
    }

    /** Contenedor de datos activo. */
    val actual: ContenedorDatos
        get() = instancia ?: synchronized(this) {
            instancia ?: crear().also { instancia = it }
        }

    /** Sustituye el contenedor activo (solo pruebas). */
    fun fijar(contenedor: ContenedorDatos?) {
        instancia = contenedor
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
        val notificaciones = NotificacionesRepositorio(bd, api, cola)
        val pedidos = PedidosRepositorio(bd, api, cola)
        return ContenedorDatos(
            contexto = app,
            bd = bd,
            cola = cola,
            notificaciones = notificaciones,
            pedidos = pedidos,
            metadatos = bd.metadatoSincronizacionDao(),
            conectividad = MonitorConectividad(app),
            sesion = sesion,
            sincronizacion = ServicioSincronizacion(
                bd = bd,
                api = api,
                cola = cola,
                notificaciones = notificaciones,
                pedidos = pedidos,
                metadatos = bd.metadatoSincronizacionDao(),
                sesion = sesion
            )
        )
    }
}
