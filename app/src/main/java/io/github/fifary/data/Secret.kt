package io.github.fifary.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// 私密内容的加密：PBKDF2-SHA256 派 512 位，前 32 字节当 AES-GCM 密钥（不落盘），
// 后 32 字节当校验值存起来——存的是校验值，不是密钥。
object Secret {

    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32
    private const val PREFIX = "f1"

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun newSalt(): String =
        ByteArray(SALT_BYTES).also { random.nextBytes(it) }.let(encoder::encodeToString)

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 512))
            .encoded

    // 密钥和校验值来自同一次派生，避免解锁跑两遍 PBKDF2
    fun derive(password: String, saltBase64: String): Pair<ByteArray, String> {
        val derived = pbkdf2(password, decoder.decode(saltBase64))
        val key = derived.copyOfRange(0, KEY_BYTES)
        val verifier = encoder.encodeToString(derived.copyOfRange(KEY_BYTES, derived.size))
        return key to verifier
    }

    fun unlock(password: String, saltBase64: String, verifier: String): ByteArray? {
        val (key, check) = derive(password, saltBase64)
        return if (check == verifier) key else null
    }

    fun encrypt(key: ByteArray, plain: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "$PREFIX:${encoder.encodeToString(iv)}:${encoder.encodeToString(cipherText)}"
    }

    fun decrypt(key: ByteArray, blob: String): String? = try {
        val parts = blob.split(':')
        if (parts.size != 3 || parts[0] != PREFIX) {
            null
        } else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, decoder.decode(parts[1])),
            )
            String(cipher.doFinal(decoder.decode(parts[2])), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    // 自检：换一把密钥必须解不开（GCM 校验会失败）
    fun selfCheck(): Boolean {
        val key = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
        val wrong = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
        val blob = encrypt(key, "私密内容")
        return decrypt(key, blob) == "私密内容" && decrypt(wrong, blob) == null
    }
}
