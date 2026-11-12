package com.ejemplo.publicarproducto.model.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Sesión persistida: token, identidad y permisos. */
data class SesionGuardada(
    val token: String? = null,
    val usuarioId: Long = 0L,
    val correo: String = "",
    val rol: String = "",
    val permisos: Set<String> = emptySet()
)

/** Persistencia de la sesión, sustituible en las pruebas. */
interface SesionStore {
    fun leer(): SesionGuardada
    fun guardar(sesion: SesionGuardada)
    fun limpiar()
}

/** Implementación en memoria, independiente de Android. */
class SesionStoreEnMemoria : SesionStore {
    private var sesion = SesionGuardada()

    override fun leer(): SesionGuardada = sesion.copy(permisos = sesion.permisos.toSet())

    override fun guardar(sesion: SesionGuardada) {
        this.sesion = sesion.copy(permisos = sesion.permisos.toSet())
    }

    override fun limpiar() {
        sesion = SesionGuardada()
    }
}

/** Cifrado del token de sesión. */
interface CifradorToken {
    fun cifrar(plano: String): String?
    fun descifrar(cifrado: String): String?
}

/** AES/GCM respaldado por una clave no exportable del Android Keystore. */
class CifradorAndroidKeyStore : CifradorToken {
    override fun cifrar(plano: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerClave())
        val cifrado = cipher.doFinal(plano.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + cifrado, Base64.NO_WRAP)
    }.getOrNull()

    override fun descifrar(cifrado: String): String? = runCatching {
        val bytes = Base64.decode(cifrado, Base64.NO_WRAP)
        require(bytes.size > TAMANO_IV) { "Criptograma incompleto." }
        val iv = bytes.copyOfRange(0, TAMANO_IV)
        val contenido = bytes.copyOfRange(TAMANO_IV, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, obtenerClave(), GCMParameterSpec(ETIQUETA_BITS, iv))
        String(cipher.doFinal(contenido), Charsets.UTF_8)
    }.getOrNull()

    private fun obtenerClave(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVEEDOR_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS)) {
            return keyStore.getKey(ALIAS, null) as SecretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "pulgatec_token_key"
        const val PROVEEDOR_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val TAMANO_IV = 12
        const val ETIQUETA_BITS = 128
    }
}

/**
 * Cifrador XOR + Base64 determinista para pruebas JVM.
 *
 * No es criptografía de producción: permite comprobar la abstracción y el
 * round-trip sin Android ni Keystore. La codificación Base64 local evita
 * depender de `android.util.Base64` (stub en JVM) o de APIs nuevas en minSdk.
 */
class CifradorEnMemoria(
    clave: ByteArray = ByteArray(32) { it.toByte() }
) : CifradorToken {
    private val clave = clave.copyOf().also { require(it.isNotEmpty()) }

    override fun cifrar(plano: String): String {
        val bytes = plano.toByteArray(Charsets.UTF_8)
        return codificarBase64(ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor clave[i % clave.size].toInt()).toByte()
        })
    }

    override fun descifrar(cifrado: String): String? = runCatching {
        val bytes = decodificarBase64(cifrado)
        val plano = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor clave[i % clave.size].toInt()).toByte()
        }
        String(plano, Charsets.UTF_8)
    }.getOrNull()

    private fun codificarBase64(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val resultado = StringBuilder(((bytes.size + 2) / 3) * 4)
        var indice = 0
        while (indice < bytes.size) {
            val restante = bytes.size - indice
            val a = bytes[indice].toInt() and 0xff
            val b = if (restante > 1) bytes[indice + 1].toInt() and 0xff else 0
            val c = if (restante > 2) bytes[indice + 2].toInt() and 0xff else 0
            resultado.append(ALFABETO[a ushr 2])
            resultado.append(ALFABETO[((a and 0x03) shl 4) or (b ushr 4)])
            resultado.append(if (restante > 1) ALFABETO[((b and 0x0f) shl 2) or (c ushr 6)] else '=')
            resultado.append(if (restante > 2) ALFABETO[c and 0x3f] else '=')
            indice += 3
        }
        return resultado.toString()
    }

    private fun decodificarBase64(texto: String): ByteArray {
        require(texto.length % 4 == 0) { "Base64 inválido." }
        if (texto.isEmpty()) return ByteArray(0)
        val salida = ArrayList<Byte>((texto.length / 4) * 3)
        for (i in texto.indices step 4) {
            val esUltimo = i + 4 == texto.length
            val a = valorBase64(texto[i])
            val b = valorBase64(texto[i + 1])
            val cPadding = texto[i + 2] == '='
            val dPadding = texto[i + 3] == '='
            require(!cPadding || (dPadding && esUltimo)) { "Padding Base64 inválido." }
            require(!dPadding || esUltimo) { "Padding Base64 inválido." }
            val c = if (cPadding) 0 else valorBase64(texto[i + 2])
            val d = if (dPadding) 0 else valorBase64(texto[i + 3])
            salida += ((a shl 2) or (b ushr 4)).toByte()
            if (!cPadding) salida += (((b and 0x0f) shl 4) or (c ushr 2)).toByte()
            if (!dPadding) salida += (((c and 0x03) shl 6) or d).toByte()
        }
        return salida.toByteArray()
    }

    private fun valorBase64(caracter: Char): Int {
        val valor = ALFABETO.indexOf(caracter)
        require(valor >= 0) { "Carácter Base64 inválido." }
        return valor
    }

    private companion object {
        const val ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    }
}

/** SharedPreferences que cifra el token y conserva los campos y claves actuales. */
class SesionStorePreferencias(
    context: Context,
    nombrePreferencias: String = "pulgatec_sesion",
    private val cifrador: CifradorToken = CifradorAndroidKeyStore()
) : SesionStore {
    private val prefs = context.getSharedPreferences(nombrePreferencias, Context.MODE_PRIVATE)

    override fun leer(): SesionGuardada {
        val tokenGuardado = prefs.getString(CLAVE_TOKEN, null)
        val token = when {
            tokenGuardado == null -> null
            tokenGuardado.startsWith(PREFIJO_CIFRADO) ->
                cifrador.descifrar(tokenGuardado.removePrefix(PREFIJO_CIFRADO))
            // Compatibilidad con tokens en claro guardados por versiones anteriores.
            else -> tokenGuardado
        }
        return SesionGuardada(
            token = token,
            usuarioId = prefs.getLong(CLAVE_USUARIO_ID, 0L),
            correo = prefs.getString(CLAVE_CORREO, "").orEmpty(),
            rol = prefs.getString(CLAVE_ROL, "").orEmpty(),
            permisos = prefs.getStringSet(CLAVE_PERMISOS, emptySet())?.toSet().orEmpty()
        )
    }

    override fun guardar(sesion: SesionGuardada) {
        val editor = prefs.edit()
        if (sesion.token == null) {
            editor.remove(CLAVE_TOKEN)
        } else {
            val cifrado = cifrador.cifrar(sesion.token)
                ?: run {
                    // Si había un token heredado en claro, no lo dejamos persistido
                    // cuando ya sabemos que el Keystore no puede protegerlo.
                    prefs.edit().remove(CLAVE_TOKEN).apply()
                    throw IllegalStateException(
                        "No se pudo cifrar el token de sesión; por seguridad no se guardará en claro."
                    )
                }
            editor.putString(CLAVE_TOKEN, PREFIJO_CIFRADO + cifrado)
        }
        editor.putLong(CLAVE_USUARIO_ID, sesion.usuarioId)
            .putString(CLAVE_CORREO, sesion.correo)
            .putString(CLAVE_ROL, sesion.rol)
            .putStringSet(CLAVE_PERMISOS, sesion.permisos.toSet())
        editor.apply()
    }

    override fun limpiar() {
        prefs.edit().clear().apply()
    }

    /** Valor tal cual está guardado (solo pruebas: verifica que no esté en claro). */
    fun valorCrudo(clave: String): String? = prefs.getString(clave, null)

    private companion object {
        const val CLAVE_TOKEN = "pulgatec_token"
        const val CLAVE_USUARIO_ID = "pulgatec_usuario_id"
        const val CLAVE_CORREO = "pulgatec_usuario_correo"
        const val CLAVE_ROL = "pulgatec_rol"
        const val CLAVE_PERMISOS = "pulgatec_permisos"
        const val PREFIJO_CIFRADO = "v1:"
    }
}
