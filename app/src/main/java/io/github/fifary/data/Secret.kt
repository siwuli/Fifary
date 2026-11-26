package io.github.fifary.data

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
    // b2 = 2 版格式（v1 的 blob 用老钥匙包的，解不开就别再试）
    private const val BIO_PREFIX = "b2"
    // shortcut: 这把钥匙只锁在系统 Keystore 里，不再要求「每次用都要系统认证」——
    // 那条路在部分机型上验过指纹仍报未认证（1.2.4/1.2.5 的 bug）。指纹现在是 app 层的门，
    // 要更硬的绑定（CryptoObject）得等确认目标机型支持后再加
    private const val BIO_ALIAS = "fifary.vault.bio3"
    private val BIO_OLD = listOf("fifary.vault.bio", "fifary.vault.bio2")

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

    // 指纹解锁：数据密钥再用一把 Keystore 密钥包一层，这把密钥不出系统，也拷不走。
    // 层内不再叠加「每次用都要系统认证」——见下面 BIO_ALIAS 的注释
    private fun bioKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(BIO_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec.build()) }
            .generateKey()
    }

    // 两头必须对称：这里加的是 32 字节原始密钥，解出来也得是原始密钥
    fun bioWrap(dataKey: ByteArray): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, bioKey()) }
        "$BIO_PREFIX:${encoder.encodeToString(cipher.iv)}:${encoder.encodeToString(cipher.doFinal(dataKey))}"
    }.getOrNull()

    // 解不开时把原因留着，界面上直接显示，省得靠猜
    var bioError: String? = null
        private set

    fun bioUnwrap(blob: String): ByteArray? = runCatching {
        val parts = blob.split(':')
        require(parts.size == 3 && parts[0] == BIO_PREFIX) { "格式 $parts" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, bioKey(), GCMParameterSpec(TAG_BITS, decoder.decode(parts[1])))
        }
        cipher.doFinal(decoder.decode(parts[2]))
    }.onFailure { bioError = "${it.javaClass.simpleName}: ${it.message}" }.getOrNull()

    // 这台设备上那把钥匙能不能原样来回：解不开时先看这个
    fun bioSelfCheck(): String = runCatching {
        val probe = newDataKey()
        val blob = bioWrap(probe) ?: return "wrap=null"
        val back = bioUnwrap(blob) ?: return "unwrap=null (${bioError})"
        if (back.contentEquals(probe)) "ok" else "mismatch"
    }.getOrElse { "${it.javaClass.simpleName}: ${it.message}" }

    // 老版本留下来的 blob（别的钥匙包的）直接当没有，别再让它报「指纹用不了了」
    fun bioLooksValid(blob: String): Boolean = blob.startsWith("$BIO_PREFIX:")

    fun bioForget() {
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { store ->
                (BIO_OLD + BIO_ALIAS).forEach { store.deleteEntry(it) }
            }
        }
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
