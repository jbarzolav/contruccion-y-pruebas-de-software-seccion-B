package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** HU 23 - Consulta y asignación de roles con carga, confirmación y errores. */
class AdministrarRolesViewModel : ViewModel() {
    // MVVM: los endpoints de roles viven en AdminRepositorio (Django :8001).
    private val admin get() = ProveedorDatos.actual.admin
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
                when (val resultado = admin.listarUsuarios()) {
                    is ResultadoApi.Exito -> usuarios = resultado.valor
                    is ResultadoApi.Error -> {
                        error = true
                        mensaje = resultado.mensaje
                    }
                }
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
                when (val resultado = admin.asignarRol(usuario.id, rol)) {
                    is ResultadoApi.Exito -> {
                        val actual = resultado.valor
                        usuarios = usuarios.map { if (it.id == actual.id) actual else it }
                        error = false
                        mensaje = "Rol actualizado correctamente."
                        onActualizado()
                    }

                    is ResultadoApi.Error -> {
                        error = true
                        mensaje = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = true; mensaje = "No se pudo actualizar el rol." }
            finally { guardando = null }
        }
    }
}
