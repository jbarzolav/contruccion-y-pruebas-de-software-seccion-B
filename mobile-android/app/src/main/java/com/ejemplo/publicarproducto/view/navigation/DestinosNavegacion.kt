package com.ejemplo.publicarproducto.view.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.ejemplo.publicarproducto.model.UsuarioRolResponse

/**
 * Índices de navegación de la aplicación.
 *
 * La numeración es estable y forma parte del contrato de navegación: las
 * subpantallas (detalle, edición, publicar, confirmar compra y detalle de
 * pedido) se controlan aparte, en [EstadoNavegacion].
 */
object IndicesNavegacion {
    const val INICIO = 0
    const val PRODUCTOS = 1
    const val CARRITO = 2
    const val CHATBOT = 3
    const val PREMIUM = 4
    const val ROLES = 5
    const val AUDITORIA = 6
    const val PERFIL = 7
    const val CONSULTAS = 8
    const val PEDIDOS = 9
    const val VENTAS = 10
    const val NOTIFICACIONES = 11
    const val METRICAS = 12
    const val UMBRALES = 13

    /**
     * R10 - Todos los destinos válidos de la aplicación.
     *
     * Incluye las pestañas de la barra inferior y las pantallas que se
     * abren desde la barra superior (Consultas y Notificaciones).
     */
    val TODOS: Set<Int> = setOf(
        INICIO, PRODUCTOS, CARRITO, CHATBOT, PREMIUM, ROLES, AUDITORIA, PERFIL,
        CONSULTAS, PEDIDOS, VENTAS, NOTIFICACIONES, METRICAS, UMBRALES
    )
}

/**
 * R10 - Índice seguro de la pantalla principal.
 *
 * Un índice desconocido (por ejemplo, tras una restauración de estado con una
 * versión anterior de la aplicación) vuelve a Inicio en vez de mostrar una
 * pantalla incorrecta. Antes, la rama `else` del `when` de navegación
 * renderizaba Plan Premium para cualquier destino no contemplado.
 */
fun indicePantallaPrincipalSeguro(indice: Int): Int =
    if (indice in IndicesNavegacion.TODOS) indice else IndicesNavegacion.INICIO

/** Pestañas de la barra inferior: ícono Material y etiqueta corta. */
data class Pestana(
    val indice: Int,
    val etiqueta: String,
    val icono: ImageVector
)

/**
 * Catálogo de pestañas; la visibilidad se decide según permisos y rol.
 *
 * Rediseño de navegación: la barra inferior queda con pocas entradas para
 * que ninguna etiqueta se parta a mitad de palabra. El chatbot es un FAB
 * flotante y Premium, Ventas, Métricas y Umbrales se abren desde "Mi perfil".
 */
val PESTANAS = listOf(
    Pestana(IndicesNavegacion.INICIO, "Inicio", Icons.Default.Home),
    Pestana(IndicesNavegacion.PRODUCTOS, "Productos", Icons.Default.Inventory2),
    Pestana(IndicesNavegacion.CARRITO, "Carrito", Icons.Default.ShoppingCart),
    Pestana(IndicesNavegacion.ROLES, "Roles", Icons.Default.ManageAccounts),
    Pestana(IndicesNavegacion.AUDITORIA, "Auditoría", Icons.Default.History),
    // Consultas baja de la barra superior a la barra inferior.
    Pestana(IndicesNavegacion.CONSULTAS, "Consultas", Icons.Default.Forum),
    Pestana(IndicesNavegacion.PERFIL, "Mi perfil", Icons.Default.Person),
    Pestana(IndicesNavegacion.PEDIDOS, "Pedidos", Icons.Default.History)
)

/**
 * HU 23 - Pestañas visibles para el usuario indicado.
 *
 * Regla de negocio de presentación, sin acceso a datos: la decide el rol y
 * los permisos que entrega el backend en la sesión validada.
 */
fun pestanasPermitidas(usuario: UsuarioRolResponse?): List<Pestana> {
    val puedeComprar = usuario?.permisos?.contains("COMPRAR") == true
    val puedePublicar = usuario?.permisos?.contains("GESTIONAR_PRODUCTOS") == true
    val puedeAdministrar = usuario?.permisos?.contains("ASIGNAR_ROLES") == true
    return PESTANAS.filter { pestana ->
        when (pestana.indice) {
            IndicesNavegacion.PRODUCTOS -> puedePublicar
            IndicesNavegacion.CARRITO -> puedeComprar
            IndicesNavegacion.ROLES, IndicesNavegacion.AUDITORIA -> puedeAdministrar
            IndicesNavegacion.CONSULTAS -> usuario?.rol == "CLIENTE" || usuario?.rol == "VENDEDOR"
            IndicesNavegacion.PERFIL -> usuario != null
            IndicesNavegacion.PEDIDOS -> puedeComprar
            else -> true
        }
    }
}
