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

    const val VERSION_ACTUAL = 2

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

    /** Migraciones que se aplican al abrir la base local. */
    val TODAS: Array<Migration> = arrayOf(MIGRACION_1_2)
}
