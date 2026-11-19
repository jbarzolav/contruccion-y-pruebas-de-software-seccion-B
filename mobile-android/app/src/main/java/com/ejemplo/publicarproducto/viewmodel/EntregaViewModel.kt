package com.ejemplo.publicarproducto.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.remote.*
import com.ejemplo.publicarproducto.model.repository.PagoGrupoRepositorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

class EntregaViewModel : ViewModel() {
    private val repo=PagoGrupoRepositorio()
    var resumen by mutableStateOf<PagoGrupo?>(null); private set
    var mensajes by mutableStateOf(emptyList<MensajeEntregaDto>()); private set
    var borrador by mutableStateOf("")
    var ocupado by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private fun operar(accion:suspend ()->Unit) {
        if (ocupado) return
        ocupado=true; error=null
        viewModelScope.launch { try { accion() } catch(e:CancellationException) { throw e }
            catch(e:Exception) { error="No se pudo completar la operación. Comprueba tu conexión y vuelve a intentarlo." }
            finally { ocupado=false } }
    }
    fun cargar(id:Long)=operar { try { resumen=repo.resumen(id) } catch(e:HttpException) { if(e.code()!=404) throw e; resumen=null } }
    fun leer(id:Long)=operar { mensajes=repo.mensajes(id) }
    fun solicitar(p:PagoGrupo)=operar { resumen=repo.solicitar(p) }
    fun devolver(p:PagoGrupo, aprobado:Boolean)=operar { resumen=repo.devolver(p,aprobado) }
    fun enviar(id:Long)=operar { val nuevo=repo.enviar(id,borrador.trim()); mensajes=mensajes+nuevo; borrador="" }
}
