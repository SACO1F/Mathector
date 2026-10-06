package com.mathector.app

import android.os.Bundle
import android.os.Build
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathector.app.ui.MathectorApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if(Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            val model: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(application as MathectorApplication) as T
            })
            MathectorApp(model)
        }
    }
}
