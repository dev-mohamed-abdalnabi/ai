package com.flowstudio.gitagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "last_crash.txt").writeText(e.stackTraceToString().take(3000))
            } catch (_: Exception) {
            }
            old?.uncaughtException(t, e)
        }
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot() } }
    }
}
