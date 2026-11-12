package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.AsignarRolRequest
import com.ejemplo.publicarproducto.model.PaginaAuditoriaResponse
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.model.remote.AdminApi
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.remote.ejecutarPeticion

/**
 * Repositorio de administración (HU 23 roles y HU 24 auditoría).
 *
 * ES INDEPENDIENTE de [AuthRepositorio] a propósito: estos endpoints los
 * sirve Django REST Framework (`:8001`), no Spring Boot (`:9000`). Comparte
 * únicamente el `OkHttpClient` y el Bearer ya existentes; no hay una segunda
 * implementación de red ni un cliente duplicado.
 *
 * Los textos de error son exactamente los que mostraba la interfaz antes de
 * la refactorización.
 */
class AdminRepositorio(private val api: AdminApi) {

    /** Lista de usuarios con sus roles y permisos. */
    suspend fun listarUsuarios(): ResultadoApi<List<UsuarioRolResponse>> =
        ejecutarPeticion(
            sinRed = "No se pudieron cargar los usuarios.",
            fallo = "No se pudieron cargar los usuarios.",
            porCodigo = { "No se pudo completar la solicitud." },
            leerMensajes = { true }
        ) { api.listarUsuarios() }

    /** Asigna un rol a un usuario (HU 23). */
    suspend fun asignarRol(idUsuario: Long, rol: String): ResultadoApi<UsuarioRolResponse> =
        ejecutarPeticion(
            sinRed = "No se pudo actualizar el rol.",
            fallo = "No se pudo actualizar el rol.",
            porCodigo = { "No se pudo completar la solicitud." },
            leerMensajes = { true }
        ) { api.asignarRol(idUsuario, AsignarRolRequest(rol)) }

    /** Historial paginado de auditoría (HU 24). */
    suspend fun listarAuditoria(pagina: Int, tamano: Int): ResultadoApi<PaginaAuditoriaResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión para consultar la auditoría.",
            fallo = "No hay conexión para consultar la auditoría.",
            porCodigo = { codigo ->
                when (codigo) {
                    401 -> "Inicia sesión para consultar la auditoría."
                    403 -> "No tienes permiso para consultar la auditoría."
                    else -> "No se pudo cargar el historial de auditoría."
                }
            },
            leerMensajes = { true }
        ) { api.listarAuditoria(pagina, tamano) }
}
