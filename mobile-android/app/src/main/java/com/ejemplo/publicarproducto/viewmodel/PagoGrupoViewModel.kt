package com.ejemplo.publicarproducto.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

class PagoGrupoViewModel : ViewModel() {
    private val repo = com.ejemplo.publicarproducto.model.repository.PagoGrupoRepositorio()
    var pago by mutableStateOf<PagoGrupo?>(null); private set
    var ocupado by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private fun ejecutar(accion: suspend () -> PagoGrupo) {
        if (ocupado) return
        ocupado=true; error=null
        viewModelScope.launch {
            try { pago=accion() }
            catch (e: CancellationException) { throw e }
            catch (e: HttpException) { error="No se pudo completar la operación (${e.code()}). Actualiza el pedido antes de reintentar." }
            catch (e: Exception) { error="No se pudo conectar. Comprueba la conexión y actualiza el pedido." }
            finally { ocupado=false }
        }
    }
    fun crear(vendedor:Long, ids:List<Long>) = ejecutar { repo.crear(vendedor,ids) }
    fun refrescar(id: Long) = ejecutar { repo.resumen(id) }
    fun simular(p: PagoGrupo, estado:String) = ejecutar { repo.simular(p,estado) }
    fun reintentar(id: Long) = ejecutar { repo.reintentar(id) }
    fun limpiar() { if (!ocupado) { pago=null; error=null } }
}
