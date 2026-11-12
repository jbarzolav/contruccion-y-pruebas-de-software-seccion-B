package com.ejemplo.publicarproducto.model.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R4 - Política de notificación de los errores de autorización.
 *
 * La regla vive en una función pura (`PoliticaErroresAutorizacion`), fuera
 * del interceptor de OkHttp, para poder verificar en la JVM toda la matriz
 * de casos sin levantar un servidor.
 *
 * Reglas verificadas:
 * - Las rutas públicas (login, recuperación, restablecimiento) NUNCA
 *   notifican expiración de sesión: un 401 del login significa "correo o
 *   contraseña incorrectos", no "sesión caducada".
 * - Un 401 se notifica cuando la petición se hizo con la misma sesión que
 *   sigue vigente.
 * - Un 401 también se notifica cuando NO había token (R4): el backend
 *   rechazó la petición y la interfaz debe volver al login.
 * - Un 403 solo se notifica cuando la sesión es la misma que envió la
 *   petición.
 * - Cuando la sesión cambió mientras la petición viajaba, la respuesta
 *   pertenece a otra sesión: no se invalida la actual.
 */
class PoliticaErroresAutorizacionTest {

    private val tokenVigente = "token-vigente"

    @Test
    fun lasRutasPublicasNoNotifican401() {
        listOf(
            "/api/auth/login",
            "/api/auth/recuperacion",
            "/api/auth/restablecer"
        ).forEach { ruta ->
            assertNull(
                "La ruta pública $ruta no debe notificar una expiración de sesión",
                PoliticaErroresAutorizacion.codigoANotificar(
                    ruta = ruta,
                    tokenEnviado = null,
                    tokenVigente = null,
                    codigo = 401
                )
            )
            assertTrue(PoliticaErroresAutorizacion.esRutaPublica(ruta))
        }
    }

    @Test
    fun unaRutaPrivadaNoEsPublica() {
        assertFalse(PoliticaErroresAutorizacion.esRutaPublica("/api/conversaciones"))
        assertFalse(PoliticaErroresAutorizacion.esRutaPublica("/api/pedidos"))
    }

    @Test
    fun un401ConLaMismaSesionSeNotifica() {
        assertEquals(
            401,
            PoliticaErroresAutorizacion.codigoANotificar(
                ruta = "/api/conversaciones",
                tokenEnviado = tokenVigente,
                tokenVigente = tokenVigente,
                codigo = 401
            )
        )
    }

    @Test
    fun un401SinTokenSeNotifica() {
        assertEquals(
            "R4 - Un 401 sin token también debe notificarse: el backend rechazó la petición",
            401,
            PoliticaErroresAutorizacion.codigoANotificar(
                ruta = "/api/pedidos",
                tokenEnviado = null,
                tokenVigente = null,
                codigo = 401
            )
        )
    }

    @Test
    fun un401ConTokenDistintoNoSeNotifica() {
        assertNull(
            "La respuesta pertenece a otra sesión: no se invalida la vigente",
            PoliticaErroresAutorizacion.codigoANotificar(
                ruta = "/api/conversaciones",
                tokenEnviado = "token-antiguo",
                tokenVigente = tokenVigente,
                codigo = 401
            )
        )
    }

    @Test
    fun un401SinTokenEnviadoPeroConSesionVigenteNoSeNotifica() {
        assertNull(
            "No se puede invalidar una sesión que la petición no usó",
            PoliticaErroresAutorizacion.codigoANotificar(
                ruta = "/api/conversaciones",
                tokenEnviado = null,
                tokenVigente = tokenVigente,
                codigo = 401
            )
        )
    }

    @Test
    fun un403ConLaMismaSesionSeNotifica() {
        assertEquals(
            403,
            PoliticaErroresAutorizacion.codigoANotificar(
                ruta = "/api/pedidos",
                tokenEnviado = tokenVigente,
                tokenVigente = tokenVigente,
                codigo = 403
            )
        )
    }

    @Test
    fun un403ConTokenDistintoNoSeNotifica() {
        assertNull(
            PoliticaErroresAutorizacion.codigoANotificar(
                ruta = "/api/pedidos",
                tokenEnviado = "token-antiguo",
                tokenVigente = tokenVigente,
                codigo = 403
            )
        )
    }

    @Test
    fun un403SinTokenNoSeNotifica() {
        assertNull(
            PoliticaErroresAutorizacion.codigoANotificar(
                ruta = "/api/pedidos",
                tokenEnviado = null,
                tokenVigente = null,
                codigo = 403
            )
        )
    }

    @Test
    fun losExitosYLosErroresNoDeAutorizacionNoSeNotifican() {
        listOf(200, 201, 204, 400, 404, 409, 422, 500, 503).forEach { codigo ->
            assertNull(
                "HTTP $codigo no es un error de autorización",
                PoliticaErroresAutorizacion.codigoANotificar(
                    ruta = "/api/pedidos",
                    tokenEnviado = tokenVigente,
                    tokenVigente = tokenVigente,
                    codigo = codigo
                )
            )
        }
    }
}
