package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.local.EntidadMetadatoSincronizacion
import com.ejemplo.publicarproducto.model.local.MetadatoSincronizacionDao
import kotlinx.coroutines.flow.Flow

/** Acceso de datos a los resultados de sincronización por recurso. */
interface MetadatosRepositorio {
    fun observar(usuarioId: Long, recurso: String): Flow<EntidadMetadatoSincronizacion?>

    suspend fun buscar(usuarioId: Long, recurso: String): EntidadMetadatoSincronizacion?

    suspend fun guardar(metadato: EntidadMetadatoSincronizacion)

    suspend fun borrarUsuario(usuarioId: Long)
}

/** Adaptador del DAO existente; conserva Room como única fuente local. */
class MetadatosRepositorioRoom(
    private val dao: MetadatoSincronizacionDao
) : MetadatosRepositorio {
    override fun observar(usuarioId: Long, recurso: String) = dao.observar(usuarioId, recurso)

    override suspend fun buscar(usuarioId: Long, recurso: String) = dao.buscar(usuarioId, recurso)

    override suspend fun guardar(metadato: EntidadMetadatoSincronizacion) = dao.guardar(metadato)

    override suspend fun borrarUsuario(usuarioId: Long) = dao.borrarUsuario(usuarioId)
}
