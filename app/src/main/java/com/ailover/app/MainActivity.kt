package com.ailover.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ailover.app.navigation.AppNavGraph
import com.ailover.app.ui.theme.AILoverTheme

class MainActivity : ComponentActivity() {
    companion object {
        // 版本标记：每次修复后更新，用于确认用户安装的是哪个版本
        const val APP_VERSION = "v1.0.1-fix-crash-38e516f"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AILover", "App launched, version=$APP_VERSION")
        setContent {
            AILoverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
