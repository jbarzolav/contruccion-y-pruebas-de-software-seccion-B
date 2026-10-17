package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.AsignarRolRequest
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.network.AdminApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response

/** HU 23 - Consulta y asignación de roles con carga, confirmación y errores. */
class AdministrarRolesViewModel : ViewModel() {
    var usuarios by mutableStateOf<List<UsuarioRolResponse>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set
    var guardando by mutableStateOf<Long?>(null)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf(false)
        private set

    fun cargar() {
        if (cargando) return
        cargando = true
        usuarios = emptyList()
        mensaje = null
        viewModelScope.launch {
            try {
                val respuesta = AdminApiClient.api.listarUsuarios()
                if (respuesta.isSuccessful) usuarios = respuesta.body().orEmpty()
                else mostrarError(respuesta)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = true; mensaje = "No se pudieron cargar los usuarios." }
            finally { cargando = false }
        }
    }
    fun guardar(usuario: UsuarioRolResponse, rol: String, onActualizado: () -> Unit) {
        if (guardando != null) return
        guardando = usuario.id
        mensaje = null
        viewModelScope.launch {
            try {
                val respuesta = AdminApiClient.api.asignarRol(usuario.id, AsignarRolRequest(rol))
                val actual = respuesta.body()
                if (respuesta.isSuccessful && actual != null) {
                    usuarios = usuarios.map { if (it.id == actual.id) actual else it }
                    error = false
                    mensaje = "Rol actualizado correctamente."
                    onActualizado()
                } else mostrarError(respuesta)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = true; mensaje = "No se pudo actualizar el rol." }
            finally { guardando = null }
        }
    }
    private fun mostrarError(respuesta: Response<*>) {
        error = true
        mensaje = runCatching {
            Gson().fromJson(respuesta.errorBody()?.string(), ErrorResponse::class.java)?.messages?.joinToString(" ")
        }.getOrNull() ?: "No se pudo completar la solicitud."
    }
}
