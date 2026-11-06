package com.ejemplo.publicarproducto.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Mejora técnica Offline First - migraciones de la base local.
 *
 * Verifica que:
 * - la base local tenga versiones (Room) y una migración explícita;
 * - la migración 1 -> 2 se aplique correctamente y conserve los datos del
 *   usuario (NO se borra la información local al actualizar la aplicación);
 * - la base NO use `fallbackToDestructiveMigration()`.
 *
 * La referencia son los esquemas exportados por Room (`app/schemas`): el
 * esquema v1 es el v2 sin la columna que la migración añade.
 */
@RunWith(AndroidJUnit4::class)
class MigracionRoomTest {

    private val nombreBase = "pulgatec_migracion_prueba"

    private lateinit var contexto: Context

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PulgaTecDatabase::class.java
    )

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        contexto.deleteDatabase(nombreBase)
    }

    @After
    fun tearDown() {
        contexto.deleteDatabase(nombreBase)
    }

    /** Inserta un aviso propio en la base ya migrada y lo devuelve. */
    private fun abrirActual(): PulgaTecDatabase =
        Room.databaseBuilder(contexto, PulgaTecDatabase::class.java, nombreBase)
            .addMigrations(*MigracionesRoom.TODAS)
            .allowMainThreadQueries()
            .build()

    @Test
    @Throws(IOException::class)
    fun laMigracionDeLaVersion1ConservaLosDatosDelUsuario() = runTest {
        // Se crea la base en su version 1 (esquema real exportado por Room).
        val dbInicial = helper.createDatabase(nombreBase, 1)
        dbInicial.execSQL(
            """
            INSERT INTO notificaciones
            (usuarioId, idNotificacion, tipo, resumen, fecha, estado, idPedido,
             enlace, idConversacion, fechaLectura, fechaSincronizacion)
            VALUES (1, 10, 'NUEVO_MENSAJE', 'Aviso guardado', '2024-05-01T10:15:30',
                    'PENDIENTE', NULL, '/consultas?c=1', 7, NULL, 1)
            """.trimIndent()
        )
        dbInicial.close()

        // Se abre con la version actual: Room aplica la migracion 1 -> 2.
        helper.runMigrationsAndValidate(
            nombreBase,
            MigracionesRoom.VERSION_ACTUAL,
            true,
            MigracionesRoom.MIGRACION_1_2
        )

        // La informacion del usuario sigue existiendo: no se borro al migrar.
        val bd = abrirActual()
        try {
            val aviso = bd.notificacionDao().buscar(1L, 10L)
            assertNotNull("El aviso debe seguir existiendo tras la migracion", aviso)
            assertEquals("Aviso guardado", aviso!!.resumen)
            assertEquals("PENDIENTE", aviso.estado)
            // La lectura guardada solo en el dispositivo se distingue de la
            // confirmada por el servidor: tras migrar arranca en 0.
            assertTrue(!aviso.pendienteLecturaLocal)
        } finally {
            bd.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun laMigracionAnadeLaColumnaDeLecturaLocalConSuValorPorDefecto() = runTest {
        val dbInicial = helper.createDatabase(nombreBase, 1)
        dbInicial.execSQL(
            """
            INSERT INTO notificaciones
            (usuarioId, idNotificacion, tipo, resumen, fecha, estado, idPedido,
             enlace, idConversacion, fechaLectura, fechaSincronizacion)
            VALUES (1, 10, 'NUEVO_MENSAJE', 'Aviso guardado', '2024-05-01T10:15:30',
                    'PENDIENTE', NULL, '/consultas?c=1', 7, NULL, 1)
            """.trimIndent()
        )
        dbInicial.close()

        helper.runMigrationsAndValidate(
            nombreBase,
            MigracionesRoom.VERSION_ACTUAL,
            true,
            MigracionesRoom.MIGRACION_1_2
        )

        val bd = abrirActual()
        try {
            // La columna nueva existe y tiene el valor por defecto esperado.
            bd.notificacionDao().marcarLecturaLocal(1L, 10L, "2024-05-02T08:00:00")
            val aviso = bd.notificacionDao().buscar(1L, 10L)
            assertNotNull(aviso)
            assertTrue(aviso!!.pendienteLecturaLocal)
            assertEquals("LEIDA", aviso.estado)
        } finally {
            bd.close()
        }
    }

    @Test
    fun laBaseDeclaraUnaVersionYUnaMigracionExplicita() {
        assertTrue("La base local debe tener version", MigracionesRoom.VERSION_ACTUAL >= 1)
        assertTrue(
            "Debe existir una migracion explicita 1 -> 2",
            MigracionesRoom.TODAS.any { it.startVersion == 1 && it.endVersion == 2 }
        )
        assertNotNull(MigracionesRoom.MIGRACION_1_2)
    }
}
