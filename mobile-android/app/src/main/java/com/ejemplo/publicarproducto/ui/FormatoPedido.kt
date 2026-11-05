package com.ejemplo.publicarproducto.ui

import com.ejemplo.publicarproducto.model.NotificacionResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun estadoPedidoVisible(estado: String?): String = when (estado) {
    "REGISTRADO" -> "Registrado"
    "EN_PROCESO" -> "En proceso"
    "ENTREGADO" -> "Entregado"
    "CANCELADO" -> "Cancelado"
    null -> "Registrado"
    else -> estado.replace('_', ' ').lowercase(Locale("es", "PE"))
        .replaceFirstChar { it.titlecase(Locale("es", "PE")) }
}

internal fun fechaHoraPedidoVisible(valor: String?): String {
    if (valor.isNullOrBlank()) return "-"
    return runCatching {
        LocalDateTime.parse(valor).format(
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "PE"))
        )
    }.getOrDefault(valor)
}

internal fun resumenNotificacionVisible(aviso: NotificacionResponse): String {
    val idPedido = aviso.idPedido ?: "-"
    if (aviso.tipo == "NUEVA_COMPRA") {
        return "Nueva compra recibida · N.º de compra: $idPedido."
    }

    val estado = Regex("cambió a\\s+(.+?)[.]?$", RegexOption.IGNORE_CASE)
        .find(aviso.resumen.orEmpty())?.groupValues?.get(1)?.removeSuffix(".")
    return if (estado != null) {
        "La compra N.º $idPedido cambió a ${estadoPedidoVisible(estado)}."
    } else {
        "El estado de la compra N.º $idPedido fue actualizado."
    }
}
