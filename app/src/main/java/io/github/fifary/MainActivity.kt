package io.github.fifary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fifary.data.Secret
import io.github.fifary.data.SettingsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        SettingsStore.load(this)
        // 加密自检：换密钥必须解不开，跑不通就直接崩，别让坏密钥悄悄上线
        check(Secret.selfCheck()) { "AES-GCM 自检失败" }
        setContent { FifaryApp() }
    }
}
