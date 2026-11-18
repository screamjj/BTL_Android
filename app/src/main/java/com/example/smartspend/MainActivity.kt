package com.example.smartspend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartspend.data.local.InMemoryDatabase
import com.example.smartspend.ui.auth.LoginScreen
import com.example.smartspend.ui.auth.LoginViewModel
import com.example.smartspend.ui.main.MainScreen
import com.example.smartspend.ui.theme.SmartSpendTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Database giả trong bộ nhớ. Khi có Room, thay dòng này bằng repository thật.
        val repository = InMemoryDatabase.repository

        setContent {
            SmartSpendTheme {
                var loggedIn by rememberSaveable { mutableStateOf(false) }
                if (!loggedIn) {
                    val loginViewModel: LoginViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return LoginViewModel(repository) as T
                            }
                        }
                    )
                    LoginScreen(viewModel = loginViewModel, onLoginSuccess = { loggedIn = true })
                } else {
                    MainScreen(repository = repository, onLogout = { loggedIn = false })
                }
            }
        }
    }
}
