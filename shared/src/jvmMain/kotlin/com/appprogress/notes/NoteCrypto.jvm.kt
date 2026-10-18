package com.appprogress.notes

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ключ получаем из пароля через PBKDF2 (200 000 повторов — подбор пароля перебором становится дорогим),
 * шифруем AES-GCM: он заодно проверяет целостность, поэтому неверный пароль надёжно распознаётся.
 */
actual object NoteCrypto {
    private const val PREFIX = "enc1:"
    private const val ITERATIONS = 200_000
    private val random = SecureRandom()

    actual fun encrypt(text: String, password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        return PREFIX + Base64.getEncoder().encodeToString(salt + iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    actual fun decrypt(secret: String, password: String): String? = runCatching {
        val all = Base64.getDecoder().decode(secret.removePrefix(PREFIX))
        val salt = all.copyOfRange(0, 16)
        val iv = all.copyOfRange(16, 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(all.copyOfRange(28, all.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }
}
