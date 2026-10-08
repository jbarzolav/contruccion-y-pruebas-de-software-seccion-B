package com.ejemplo.publicarproducto.model

import java.math.BigDecimal

/**
 * HU 16 - Consulta enviada al chatbot.
 * Body: { "consulta": "notebook" }
 */
data class ChatbotRequest(
    val consulta: String
)

/**
 * HU 16 - Respuesta del chatbot: mensaje amigable + productos sugeridos
 * (solo disponibles: sin RETIRADO, INACTIVO ni stock 0).
 */
data class ChatbotResponse(
    val mensaje: String?,
    val productos: List<ProductoResumenResponse>?
)

/**
 * Resumen del producto sugerido: suficiente para pintar la tarjeta
 * y navegar a su detalle.
 */
data class ProductoResumenResponse(
    val id: Long?,
    val nombre: String?,
    val precio: BigDecimal?,
    val stock: Int?,
    val categoria: String?,
    val imagenUrl: String?
)
