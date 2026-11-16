package com.janderson.borajuntos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janderson.borajuntos.navigation.BoraApp
import com.janderson.borajuntos.ui.screens.AuthViewModel
import com.janderson.borajuntos.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AuthViewModel::class.java))
                return AuthViewModel(container.authRepository) as T
            }
        }
        setContent { MyApplicationTheme { BoraApp(viewModel(factory = factory), container) } }
    }
}
