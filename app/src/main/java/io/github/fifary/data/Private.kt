package io.github.fifary.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// 解锁的三种输入法：底下还是同一个密码字符串，只是换块键盘（图形 = 点位的号码串）
enum class LockMode { TEXT, PIN, PATTERN }

// 私密笔记：密码只在本机用来越过第一层，解出来的数据密钥只留在内存里，加锁即丢。
// 改密码 = 用新密码把同一把数据密钥重新包一次，笔记本身不动，所以是瞬间的。
object PrivateVault {

    // Compose 状态：解锁 / 上锁时列表和编辑页要立刻换样子，不能等别的状态顺手带走一次重组
    var key by mutableStateOf<ByteArray?>(null)
        private set

    // 解密结果缓存：列表每帧都会取预览，不能每帧跑一次 AES
    private val cache = HashMap<String, Pair<Long, String>>()

    val enabled: Boolean get() = SettingsStore.secretWrapped != null
    val unlocked: Boolean get() = key != null
    val mode: LockMode get() = SettingsStore.secretMode
    val pinLen: Int get() = SettingsStore.secretPinLen
    val bioEnabled: Boolean get() = SettingsStore.secretBio != null

    fun setPassword(password: String, mode: LockMode = LockMode.TEXT): Boolean {
        if (!fits(password, mode)) return false
        val dataKey = Secret.newDataKey()
        write(dataKey, password, mode)
        key = dataKey
        cache.clear()
        // 换了一把数据密钥，之前指纹包住的那把就再也解不开笔记了
        forgetBiometric()
        return true
    }

    fun unlock(password: String): Boolean {
        val salt = SettingsStore.secretSalt ?: return false
        val wrapped = SettingsStore.secretWrapped ?: return false
        key = Secret.unwrap(Secret.deriveKek(password, salt), wrapped) ?: return false
        cache.clear()
        return true
    }

    // 指纹那一层解开后直接把数据密钥交进来
    fun adopt(dataKey: ByteArray) {
        key = dataKey
        cache.clear()
    }

    fun lock() {
        key = null
        cache.clear()
    }

    fun changePassword(old: String, fresh: String, mode: LockMode = LockMode.TEXT): Boolean {
        if (!fits(fresh, mode)) return false
        if (!unlock(old)) return false
        val dataKey = key ?: return false
        // 数据密钥没变，指纹那一层继续有效
        write(dataKey, fresh, mode)
        return true
    }

    fun forgetBiometric() {
        SettingsStore.updateSecretBio(null)
        Secret.bioForget()
    }

    private fun write(dataKey: ByteArray, password: String, mode: LockMode) {
        val salt = Secret.newSalt()
        val wrapped = Secret.wrap(Secret.deriveKek(password, salt), dataKey)
        SettingsStore.updateSecret(salt, wrapped, mode, password.length)
    }

    // 图形密码的号码串和 PIN 一样只有数字，靠模式区分够用；文本密码维持原来的 4 位下限
    private fun fits(secret: String, mode: LockMode): Boolean = when (mode) {
        LockMode.TEXT -> secret.length >= 4
        LockMode.PIN -> secret.length in 4..8 && secret.all { it in '0'..'9' }
        LockMode.PATTERN -> secret.length >= 4 && secret.all { it in '1'..'9' }
    }

    fun plain(note: Note): String {
        if (!note.secret) return note.body
        cache[note.path]?.let { (at, text) -> if (at == note.updatedAt) return text }
        val current = key ?: return ""
        val text = Secret.decrypt(current, note.body) ?: return ""
        cache[note.path] = note.updatedAt to text
        return text
    }

    fun forget(path: String) {
        cache.remove(path)
    }
}
