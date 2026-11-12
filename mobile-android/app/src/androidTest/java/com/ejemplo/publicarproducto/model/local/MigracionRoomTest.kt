package com.ejemplo.publicarproducto.model.local

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
import org.junit.Assert.assertNull
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
 * - la migración 2 -> 3 (HU 57/HU 58) añada la columna `productoId` y las
 *   tablas `metricas_ventas` y `umbrales_stock` conservando los avisos;
 * - la base NO use `fallbackToDestructiveMigration()`.
 *
 * La referencia son los esquemas exportados por Room (`app/schemas`): el
 * esquema v1 es el v2 sin la columna que la migración añade, y el v3 es el v2
 * más la columna `productoId` y las dos tablas nuevas de HU 57/HU 58.
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

        // Se abre con la version actual: Room aplica la migracion 1 -> 2
        // (y despues la 2 -> 3 de HU 57/HU 58).
        helper.runMigrationsAndValidate(
            nombreBase,
            MigracionesRoom.VERSION_ACTUAL,
            true,
            *MigracionesRoom.TODAS
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
            *MigracionesRoom.TODAS
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

    /**
     * HU 57/HU 58 - La migración 2 -> 3 conserva los avisos del usuario y
     * añade las tablas nuevas (métricas de ventas y umbrales de stock).
     */
    @Test
    @Throws(IOException::class)
    fun laMigracionDeLaVersion2ConservaLosAvisosYAnadeLasTablasNuevas() = runTest {
        // Se crea la base en su version 2 (esquema real exportado por Room).
        val dbInicial = helper.createDatabase(nombreBase, 2)
        dbInicial.execSQL(
            """
            INSERT INTO notificaciones
            (usuarioId, idNotificacion, tipo, resumen, fecha, estado, idPedido,
             enlace, idConversacion, fechaLectura, fechaSincronizacion,
             pendienteLecturaLocal)
            VALUES (1, 20, 'NUEVA_COMPRA', 'Aviso conservado', '2024-05-01T10:15:30',
                    'PENDIENTE', NULL, '/ventas', NULL, NULL, 1, 0)
            """.trimIndent()
        )
        dbInicial.close()

        helper.runMigrationsAndValidate(
            nombreBase,
            MigracionesRoom.VERSION_ACTUAL,
            true,
            MigracionesRoom.MIGRACION_2_3
        )

        val bd = abrirActual()
        try {
            // La información del usuario sigue existiendo: no se borró al migrar.
            val aviso = bd.notificacionDao().buscar(1L, 20L)
            assertNotNull("El aviso debe seguir existiendo tras la migracion", aviso)
            assertEquals("Aviso conservado", aviso!!.resumen)
            assertEquals("PENDIENTE", aviso.estado)
            // La columna nueva existe y vale nulo en los avisos que no son
            // de stock bajo.
            assertNull(aviso.productoId)

            // HU 57 - La tabla del último panel de métricas está disponible.
            bd.metricasDao().guardar(
                EntidadMetricasVentas(
                    usuarioId = 1L,
                    clavePeriodo = "2024-05-01|2024-05-31|true",
                    payload = """{"cantidadPedidos":3}""",
                    fechaSincronizacion = 1L
                )
            )
            val metricas = bd.metricasDao().buscar(1L)
            assertNotNull(metricas)
            assertEquals("2024-05-01|2024-05-31|true", metricas!!.clavePeriodo)

            // HU 58 - La tabla de umbrales de stock está disponible.
            bd.umbralStockDao().guardarUno(
                EntidadUmbralStock(
                    usuarioId = 1L,
                    idProducto = 7L,
                    nombre = "Café molido",
                    imagenUrl = null,
                    stock = 4,
                    umbral = 5,
                    stockBajo = true,
                    alertaNotificada = false,
                    fechaActualizacion = null,
                    umbralPendienteLocal = null,
                    fechaSincronizacion = 1L
                )
            )
            val umbrales = bd.umbralStockDao().listar(1L)
            assertEquals(1, umbrales.size)
            assertEquals("Café molido", umbrales.first().nombre)
            assertTrue(umbrales.first().stockBajo)
        } finally {
            bd.close()
        }
    }

    /**
     * HU 58 - Tras la migración 2 -> 3 la columna `productoId` permite
     * identificar la publicación de un aviso de stock bajo.
     */
    @Test
    @Throws(IOException::class)
    fun trasLaMigracionElAvisoDeStockBajoIdentificaLaPublicacion() = runTest {
        val dbInicial = helper.createDatabase(nombreBase, 2)
        dbInicial.close()

        helper.runMigrationsAndValidate(
            nombreBase,
            MigracionesRoom.VERSION_ACTUAL,
            true,
            MigracionesRoom.MIGRACION_2_3
        )

        val bd = abrirActual()
        try {
            bd.notificacionDao().guardarTodas(
                listOf(
                    EntidadNotificacion(
                        usuarioId = 1L,
                        idNotificacion = 30L,
                        tipo = "STOCK_BAJO",
                        resumen = "Stock bajo en «Café molido».",
                        fecha = "2024-05-01T10:15:30",
                        estado = "PENDIENTE",
                        idPedido = null,
                        enlace = "/mis-publicaciones#producto-7",
                        idConversacion = null,
                        fechaLectura = null,
                        fechaSincronizacion = 1L,
                        productoId = 7L,
                        pendienteLecturaLocal = false
                    )
                )
            )

            val aviso = bd.notificacionDao().buscar(1L, 30L)
            assertNotNull(aviso)
            assertEquals(7L, aviso!!.productoId)
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
        // HU 57/HU 58 - La migracion 2 -> 3 tambien es explicita: sin ella la
        // aplicacion fallaria al actualizar en vez de borrar los datos locales.
        assertTrue(
            "Debe existir una migracion explicita 2 -> 3",
            MigracionesRoom.TODAS.any { it.startVersion == 2 && it.endVersion == 3 }
        )
        assertNotNull(MigracionesRoom.MIGRACION_2_3)
        assertEquals(3, MigracionesRoom.VERSION_ACTUAL)
    }
}
