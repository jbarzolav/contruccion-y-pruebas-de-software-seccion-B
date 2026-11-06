package com.ejemplo.publicarproducto.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local de PulgaTec (Room).
 *
 * Es una COPIA PERSISTENTE de la información oficial del backend, no un
 * reemplazo de MariaDB. El backend siempre tiene prioridad: cada sincronización
 * sobrescribe la copia local con la respuesta real del servidor.
 */
@Database(
    entities = [
        EntidadNotificacion::class,
        EntidadBandeja::class,
        EntidadPedido::class,
        EntidadDetallePedido::class,
        EntidadProducto::class,
        EntidadResena::class,
        EntidadCalificacion::class,
        EntidadOperacionPendiente::class,
        EntidadMetadatoSincronizacion::class
    ],
    version = MigracionesRoom.VERSION_ACTUAL,
    exportSchema = true
)
abstract class PulgaTecDatabase : RoomDatabase() {

    abstract fun notificacionDao(): NotificacionDao
    abstract fun bandejaDao(): BandejaDao
    abstract fun pedidoDao(): PedidoDao
    abstract fun productoDao(): ProductoDao
    abstract fun operacionPendienteDao(): OperacionPendienteDao
    abstract fun metadatoSincronizacionDao(): MetadatoSincronizacionDao

    companion object {
        const val NOMBRE = "pulgatec_local.db"

        @Volatile
        private var instancia: PulgaTecDatabase? = null

        /** Base de datos de la aplicación (una sola instancia por proceso). */
        fun obtener(context: Context): PulgaTecDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulgaTecDatabase::class.java,
                    NOMBRE
                )
                    .addMigrations(*MigracionesRoom.TODAS)
                    // Sin fallbackToDestructiveMigration: la información local no se borra
                    // al actualizar la aplicación; si falta una migración, Room falla.
                    .build()
                    .also { instancia = it }
            }

        /** Base de datos aislada para pruebas. */
        fun crearEnMemoria(context: Context, nombre: String = "pulgatec_prueba"): PulgaTecDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, PulgaTecDatabase::class.java)
                .addMigrations(*MigracionesRoom.TODAS)
                .allowMainThreadQueries()
                .build()

        /**
         * Base de datos en archivo para pruebas de persistencia: permite
         * cerrar y reabrir la base (como si la aplicación se cerrara) y
         * comprobar que la información sigue ahí.
         */
        fun crearEnDisco(context: Context, nombre: String): PulgaTecDatabase =
            Room.databaseBuilder(context.applicationContext, PulgaTecDatabase::class.java, nombre)
                .addMigrations(*MigracionesRoom.TODAS)
                .allowMainThreadQueries()
                .build()

        /** Sustituye la instancia global (solo pruebas). */
        fun fijarInstancia(bd: PulgaTecDatabase?) {
            instancia = bd
        }
    }
}
