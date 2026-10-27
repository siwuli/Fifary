package io.github.fifary.data

// 私密笔记：密码只在本机用来越过第一层，解出来的数据密钥只留在内存里，加锁即丢。
// 改密码 = 用新密码把同一把数据密钥重新包一次，笔记本身不动，所以是瞬间的。
object PrivateVault {

    var key: ByteArray? = null
        private set

    // 解密结果缓存：列表每帧都会取预览，不能每帧跑一次 AES
    private val cache = HashMap<String, Pair<Long, String>>()

    val enabled: Boolean get() = SettingsStore.secretWrapped != null
    val unlocked: Boolean get() = key != null

    fun setPassword(password: String): Boolean {
        if (password.length < 4) return false
        val dataKey = Secret.newDataKey()
        val salt = Secret.newSalt()
        SettingsStore.updateSecret(salt, Secret.wrap(Secret.deriveKek(password, salt), dataKey))
        key = dataKey
        cache.clear()
        return true
    }

    fun unlock(password: String): Boolean {
        val salt = SettingsStore.secretSalt ?: return false
        val wrapped = SettingsStore.secretWrapped ?: return false
        key = Secret.unwrap(Secret.deriveKek(password, salt), wrapped) ?: return false
        cache.clear()
        return true
    }

    fun lock() {
        key = null
        cache.clear()
    }

    fun changePassword(old: String, fresh: String): Boolean {
        if (fresh.length < 4) return false
        if (!unlock(old)) return false
        val dataKey = key ?: return false
        val salt = Secret.newSalt()
        SettingsStore.updateSecret(salt, Secret.wrap(Secret.deriveKek(fresh, salt), dataKey))
        return true
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
