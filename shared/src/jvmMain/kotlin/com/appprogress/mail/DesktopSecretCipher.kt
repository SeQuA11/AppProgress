package com.appprogress.mail

import com.appprogress.core.DatabaseFactory
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Windows: DPAPI — расшифровать сможет только эта учётная запись Windows на этом компьютере,
 * ключ хранит сама система. Другие ОС: AES-GCM с ключом в файле рядом с базой (слабее — до перехода на libsecret).
 */
class DesktopSecretCipher(private val keyFile: File = File(DatabaseFactory.defaultDataDir(), "secret.key")) : SecretCipher {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    override fun encrypt(plain: String): String {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        return if (isWindows) "dpapi:" + b64.encodeToString(Crypt32Util.cryptProtectData(bytes)) else "aes:" + aesEncrypt(bytes)
    }

    override fun decrypt(secret: String): String = when {
        secret.startsWith("dpapi:") -> String(Crypt32Util.cryptUnprotectData(unb64.decode(secret.removePrefix("dpapi:"))), Charsets.UTF_8)
        secret.startsWith("aes:") -> aesDecrypt(secret.removePrefix("aes:"))
        else -> error("Неизвестный формат секрета")
    }

    private fun key(): SecretKeySpec {
        if (!keyFile.exists()) {
            keyFile.parentFile?.mkdirs()
            keyFile.writeBytes(ByteArray(32).also { SecureRandom().nextBytes(it) })
            keyFile.setReadable(false, false)
            keyFile.setReadable(true, true)
        }
        return SecretKeySpec(keyFile.readBytes(), "AES")
    }

    private fun aesEncrypt(bytes: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return b64.encodeToString(iv + cipher.doFinal(bytes))
    }

    private fun aesDecrypt(text: String): String {
        val all = unb64.decode(text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOf(12))) }
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }
}

actual fun platformSecretCipher(): SecretCipher = DesktopSecretCipher()
