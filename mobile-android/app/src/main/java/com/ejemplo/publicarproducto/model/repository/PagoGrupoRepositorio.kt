package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.*

class PagoGrupoRepositorio(private val api: CheckoutApi = UserApiClient.checkoutApi) {
    suspend fun crear(vendedor: Long, items: List<Long>) = api.crear(SeleccionGrupo(vendedor, items))
    suspend fun resumen(id: Long) = api.resumen(id)
    suspend fun reintentar(id: Long) = api.reintentar(id)
    suspend fun simular(pago: PagoGrupo, resultado: String): PagoGrupo {
        api.resolver(pago.idPedido, pago.aviso(resultado))
        return resumen(pago.idPedido)
    }
}
