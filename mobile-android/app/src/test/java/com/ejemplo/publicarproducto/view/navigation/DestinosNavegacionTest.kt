package com.ejemplo.publicarproducto.view.navigation

import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase E - HU 23: la visibilidad de las pestañas depende de los permisos y
 * del rol que entrega el backend en la sesión validada.
 *
 * La regla se extrajo de `MainActivity` a `view/navigation`, de modo que se
 * puede verificar sin levantar la interfaz.
 */
class DestinosNavegacionTest {

    private fun usuario(
        rol: String = "CLIENTE",
        permisos: Set<String> = emptySet()
    ) = UsuarioRolResponse(7L, "correo@pulga.test", rol, permisos)

    @Test
    fun `el cliente sin permisos ve inicio consultas y su perfil`() {
        // Rediseño: el chatbot es un FAB, Premium se abre desde el perfil y
        // Consultas baja de la cabecera a la barra inferior.
        val visibles = pestanasPermitidas(usuario()).map { it.indice }
        assertEquals(
            listOf(
                IndicesNavegacion.INICIO,
                IndicesNavegacion.CONSULTAS,
                IndicesNavegacion.PERFIL
            ),
            visibles
        )
    }

    @Test
    fun `el cliente con permiso de compra ve carrito y pedidos`() {
        val visibles = pestanasPermitidas(usuario(permisos = setOf("COMPRAR"))).map { it.indice }
        assertTrue(IndicesNavegacion.CARRITO in visibles)
        assertTrue(IndicesNavegacion.PEDIDOS in visibles)
        assertFalse(IndicesNavegacion.PRODUCTOS in visibles)
    }

    @Test
    fun `el vendedor ve productos en la barra y su zona vive en el perfil`() {
        // Rediseño: Ventas, Métricas y Umbrales salen de la barra inferior
        // y se abren desde el apartado del vendedor en "Mi perfil".
        val visibles = pestanasPermitidas(
            usuario(rol = "VENDEDOR", permisos = setOf("GESTIONAR_PRODUCTOS"))
        ).map { it.indice }
        assertTrue(IndicesNavegacion.PRODUCTOS in visibles)
        assertFalse(IndicesNavegacion.VENTAS in visibles)
        assertFalse(IndicesNavegacion.METRICAS in visibles)
        assertFalse(IndicesNavegacion.UMBRALES in visibles)
    }

    @Test
    fun `el administrador ve roles y auditoria`() {
        val visibles = pestanasPermitidas(
            usuario(rol = "ADMIN", permisos = setOf("ASIGNAR_ROLES"))
        ).map { it.indice }
        assertTrue(IndicesNavegacion.ROLES in visibles)
        assertTrue(IndicesNavegacion.AUDITORIA in visibles)
    }

    @Test
    fun `sin sesion solo queda la pestana de inicio`() {
        // Rediseño: Chatbot y Premium ya no son pestañas públicas de la
        // barra (el chatbot es un FAB de la sesión iniciada; Premium se
        // abre desde el perfil).
        assertEquals(
            listOf(IndicesNavegacion.INICIO),
            pestanasPermitidas(null).map { it.indice }
        )
    }

    // ------------------------------------------------------------------
    // R10 - Destino seguro de la pantalla principal.
    //
    // Antes, la rama `else` del `when` de navegación renderizaba Plan
    // Premium para cualquier destino no contemplado: tras una restauración
    // de estado con una versión anterior, el usuario veía una pantalla
    // incorrecta. Ahora un índice desconocido vuelve a Inicio.
    // ------------------------------------------------------------------

    @Test
    fun `un indice desconocido vuelve a la pantalla de inicio`() {
        listOf(-1, 99, 1_000, Int.MAX_VALUE, Int.MIN_VALUE).forEach { indice ->
            assertEquals(
                "El índice $indice no es un destino válido: debe volver a Inicio",
                IndicesNavegacion.INICIO,
                indicePantallaPrincipalSeguro(indice)
            )
        }
    }

    @Test
    fun `todos los destinos validos se conservan`() {
        IndicesNavegacion.TODOS.forEach { indice ->
            assertEquals(
                "El destino $indice es válido y debe conservarse",
                indice,
                indicePantallaPrincipalSeguro(indice)
            )
        }
    }

    @Test
    fun `el conjunto de destinos cubre las pestanas y las pantallas superiores`() {
        assertEquals(
            listOf(
                IndicesNavegacion.INICIO,
                IndicesNavegacion.PRODUCTOS,
                IndicesNavegacion.CARRITO,
                IndicesNavegacion.CHATBOT,
                IndicesNavegacion.PREMIUM,
                IndicesNavegacion.ROLES,
                IndicesNavegacion.AUDITORIA,
                IndicesNavegacion.PERFIL,
                IndicesNavegacion.CONSULTAS,
                IndicesNavegacion.PEDIDOS,
                IndicesNavegacion.VENTAS,
                IndicesNavegacion.NOTIFICACIONES,
                IndicesNavegacion.METRICAS,
                IndicesNavegacion.UMBRALES
            ),
            IndicesNavegacion.TODOS.sorted()
        )
    }
}
