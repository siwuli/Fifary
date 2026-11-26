package io.github.fifary.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// 信封加密：笔记用一把随机数据密钥加密，数据密钥再用密码派生的密钥包起来存。
// 改密码只是重新包一次那 32 字节，笔记不用解、不用重写。
object Secret {

    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32
    private const val PREFIX = "f2"
    private const val BIO_PREFIX = "b"
    private const val BIO_ALIAS = "fifary.vault.bio"

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun newSalt(): String =
        ByteArray(SALT_BYTES).also { random.nextBytes(it) }.let(encoder::encodeToString)

    fun newDataKey(): ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

    fun deriveKek(password: String, saltBase64: String): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(
                PBEKeySpec(password.toCharArray(), decoder.decode(saltBase64), ITERATIONS, 256)
            )
            .encoded

    // 数据密钥存成密文：密码不对时 GCM 校验就失败，等于顺带验了密码
    fun wrap(kek: ByteArray, dataKey: ByteArray): String = encrypt(kek, encoder.encodeToString(dataKey))

    fun unwrap(kek: ByteArray, wrapped: String): ByteArray? =
        decrypt(kek, wrapped)?.let { runCatching { decoder.decode(it) }.getOrNull() }

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

    // 指纹解锁：数据密钥再用一把「每次用都要系统鉴权」的 Keystore 密钥包一层。
    // 这把密钥永远不出系统，拷走 app 数据也解不开这一层；删掉指纹就退回密码
    private fun bioKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(BIO_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            // -1 = 每次用都要重新认证（30 之前只有这个写法）
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(-1)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec.build()) }
            .generateKey()
    }

    // 认证前建好 cipher、认证通过后才 doFinal：这中间的授权由系统盯着
    fun bioEncrypt(): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, bioKey()) }

    fun bioDecrypt(blob: String): Cipher? = runCatching {
        val parts = blob.split(':')
        require(parts.size == 3 && parts[0] == BIO_PREFIX)
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, bioKey(), GCMParameterSpec(TAG_BITS, decoder.decode(parts[1])))
        }
    }.getOrNull()

    fun bioWrap(cipher: Cipher, dataKey: ByteArray): String =
        "$BIO_PREFIX:${encoder.encodeToString(cipher.iv)}:${encoder.encodeToString(cipher.doFinal(dataKey))}"

    fun bioUnwrap(cipher: Cipher, blob: String): ByteArray? = runCatching {
        decoder.decode(cipher.doFinal(decoder.decode(blob.split(':')[2])))
    }.getOrNull()

    fun bioForget() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(BIO_ALIAS) }
    }

    // 自检：换一把密钥必须解不开（GCM 校验会失败），换密码必须还能解开数据密钥
    fun selfCheck(): Boolean {
        val key = newDataKey()
        val wrong = newDataKey()
        val blob = encrypt(key, "私密内容")
        if (decrypt(key, blob) != "私密内容" || decrypt(wrong, blob) != null) return false

        val salt = newSalt()
        val wrapped = wrap(deriveKek("1234", salt), key)
        val newSalt = newSalt()
        val rewrapped = wrap(deriveKek("5678", newSalt), unwrap(deriveKek("1234", salt), wrapped)!!)
        return unwrap(deriveKek("5678", newSalt), rewrapped)!!.contentEquals(key) &&
            unwrap(deriveKek("1234", salt), rewrapped) == null
    }
}
