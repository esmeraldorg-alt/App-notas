package com.notasseguras.app

import android.content.Context
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

@Serializable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Guarda as notas num arquivo único criptografado (notes.bin) dentro da área
 * privada do app. O salt e um "verificador" ficam em SharedPreferences:
 * o verificador é um texto fixo cifrado com a chave — se decifrar, a senha está certa.
 */
class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("seguranca", Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "notes.bin")
    private val json = Json { ignoreUnknownKeys = true }

    val hasPassword: Boolean get() = prefs.contains(KEY_SALT)

    /** Cria a senha pela primeira vez. Retorna a chave já desbloqueada. */
    fun createPassword(password: CharArray): SecretKey {
        val salt = Crypto.newSalt()
        val key = Crypto.deriveKey(password, salt)
        prefs.edit()
            .putString(KEY_SALT, salt.b64())
            .putString(KEY_VERIFIER, Crypto.encrypt(key, VERIFIER_TEXT).b64())
            .putInt(KEY_FAILS, 0)
            .apply()
        save(key, emptyList())
        return key
    }

    /** Retorna a chave se a senha estiver certa, ou null. */
    fun unlock(password: CharArray): SecretKey? {
        val salt = prefs.getString(KEY_SALT, null)?.unb64() ?: return null
        val verifier = prefs.getString(KEY_VERIFIER, null)?.unb64() ?: return null
        val key = Crypto.deriveKey(password, salt)
        return try {
            if (Crypto.decrypt(key, verifier).contentEquals(VERIFIER_TEXT)) {
                prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
                key
            } else null
        } catch (e: Exception) {
            registerFailure()
            null
        }
    }

    /** Tempo (ms) que ainda falta de bloqueio por tentativas erradas. */
    fun lockoutRemainingMs(): Long =
        (prefs.getLong(KEY_LOCK_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    private fun registerFailure() {
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILS, fails)
        // A partir da 5ª tentativa errada: espera crescente (30s, 60s, 120s... até 15 min)
        if (fails >= 5) {
            val waitSec = (30L shl (fails - 5).coerceAtMost(5)).coerceAtMost(900)
            editor.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + waitSec * 1000)
        }
        editor.apply()
    }

    fun load(key: SecretKey): List<Note> {
        if (!file.exists()) return emptyList()
        val plain = Crypto.decrypt(key, file.readBytes())
        return json.decodeFromString(plain.decodeToString())
    }

    fun save(key: SecretKey, notes: List<Note>) {
        val data = Crypto.encrypt(key, json.encodeToString(notes).encodeToByteArray())
        // Grava num temporário e renomeia, para não corromper se o app fechar no meio
        val tmp = File(file.parentFile, "notes.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(file)) {
            file.writeBytes(data)
            tmp.delete()
        }
    }

    /** Troca a senha: recifra todas as notas com a nova chave. */
    fun changePassword(oldKey: SecretKey, newPassword: CharArray): SecretKey {
        val notes = load(oldKey)
        val newKey = createPassword(newPassword)
        save(newKey, notes)
        return newKey
    }

    /** Apaga tudo (usado em "Esqueci a senha"). */
    fun wipe() {
        file.delete()
        prefs.edit().clear().apply()
    }

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        private const val KEY_SALT = "salt"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private val VERIFIER_TEXT = "notas-seguras-ok".encodeToByteArray()
    }
}
