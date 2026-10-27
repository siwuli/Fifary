package io.github.fifary.data

// 私密笔记：密码只在本机验证，AES 密钥只留在内存里，加锁即丢。
// 改密码 = 用旧密码解出全部明文，再用新密码重新加密（存储层是密文，必须重写）。
object PrivateVault {

    var key: ByteArray? = null
        private set

    // 解密结果缓存：列表每帧都会取预览，不能每帧跑一次 AES
    private val cache = HashMap<String, Pair<Long, String>>()

    val enabled: Boolean get() = SettingsStore.secretVerifier != null
    val unlocked: Boolean get() = key != null

    fun setPassword(password: String): Boolean {
        if (password.length < 4) return false
        val salt = Secret.newSalt()
        val (fresh, verifier) = Secret.derive(password, salt)
        SettingsStore.updateSecret(salt, verifier)
        key = fresh
        cache.clear()
        return true
    }

    fun unlock(password: String): Boolean {
        val salt = SettingsStore.secretSalt ?: return false
        val verifier = SettingsStore.secretVerifier ?: return false
        key = Secret.unlock(password, salt, verifier)
        cache.clear()
        return key != null
    }

    fun lock() {
        key = null
        cache.clear()
    }

    // 改密码：先把全部密文解成明文，再统一按新密钥重新加密；解不开就整个放弃，不写半截
    fun changePassword(old: String, fresh: String): Boolean {
        if (!unlock(old)) return false
        val plains = LinkedHashMap<String, String>()
        for (note in NotesStore.all) {
            if (!note.secret) continue
            plains[note.path] = Secret.decrypt(key!!, note.body) ?: return false
        }
        if (!setPassword(fresh)) return false
        val newKey = key!!
        NotesStore.rewrite { note ->
            val plain = plains[note.path]
            if (note.secret && plain != null) note.copy(body = Secret.encrypt(newKey, plain)) else note
        }
        cache.clear()
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
