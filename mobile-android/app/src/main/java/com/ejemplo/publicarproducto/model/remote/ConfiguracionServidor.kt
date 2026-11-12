package com.ejemplo.publicarproducto.model.remote

import com.ejemplo.publicarproducto.BuildConfig

object ConfiguracionServidor {
    fun urlImagen(ruta: String?): String? {
        if (ruta.isNullOrBlank()) return null
        if (ruta.startsWith("http://") || ruta.startsWith("https://")) return ruta

        return "${BuildConfig.BASE_URL.trimEnd('/')}/${ruta.trimStart('/')}"
    }
}
