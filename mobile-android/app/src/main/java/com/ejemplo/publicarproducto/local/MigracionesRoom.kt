package com.ejemplo.publicarproducto.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones explícitas de la base local.
 *
 * PulgaTec NO usa `fallbackToDestructiveMigration()`: si una versión futura
 * llega sin migración, Room falla de forma ruidosa en lugar de borrar la
 * información local del usuario.
 */
object MigracionesRoom {

    const val VERSION_ACTUAL = 3

    /**
     * v1 -> v2: la lectura de un aviso puede quedar guardada en el dispositivo
     * sin que el servidor la haya confirmado todavía. La columna permite
     * distinguir "leída en el servidor" de "lectura pendiente de sincronizar".
     */
    val MIGRACION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE notificaciones ADD COLUMN pendienteLecturaLocal INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v2 -> v3 (HU 57/HU 58):
     * - los avisos de stock bajo identifican la publicación: se añade la
     *   columna `productoId` a la tabla de notificaciones (nula en el resto
     *   de avisos);
     * - se crea la tabla con el último resultado válido de las métricas de
     *   ventas;
     * - se crea la tabla de umbrales de stock del vendedor.
     *
     * Ninguna tabla existente se borra: la información local del usuario se
     * conserva al actualizar la aplicación.
     */
    val MIGRACION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notificaciones ADD COLUMN productoId INTEGER")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `metricas_ventas` (
                    `usuarioId` INTEGER NOT NULL,
                    `clavePeriodo` TEXT NOT NULL,
                    `payload` TEXT NOT NULL,
                    `fechaSincronizacion` INTEGER NOT NULL,
                    PRIMARY KEY(`usuarioId`)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `umbrales_stock` (
                    `usuarioId` INTEGER NOT NULL,
                    `idProducto` INTEGER NOT NULL,
                    `nombre` TEXT,
                    `imagenUrl` TEXT,
                    `stock` INTEGER,
                    `umbral` INTEGER,
                    `stockBajo` INTEGER NOT NULL,
                    `alertaNotificada` INTEGER NOT NULL,
                    `fechaActualizacion` TEXT,
                    `umbralPendienteLocal` INTEGER,
                    `fechaSincronizacion` INTEGER NOT NULL,
                    PRIMARY KEY(`usuarioId`, `idProducto`)
                )
                """.trimIndent()
            )
        }
    }

    /** Migraciones que se aplican al abrir la base local. */
    val TODAS: Array<Migration> = arrayOf(MIGRACION_1_2, MIGRACION_2_3)
}
