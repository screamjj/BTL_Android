package com.example.smartspend.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartspend.data.local.entity.UserEntity
import com.example.smartspend.data.repository.SmartSpendRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
)

class LoginViewModel(
    private val repository: SmartSpendRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun toggleMode(isRegister: Boolean) {
        _uiState.update { it.copy(isRegisterMode = isRegister, errorMessage = null) }
    }

    fun login() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập đầy đủ email và mật khẩu.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = repository.getUserByEmail(email)
                if ((user != null) && (user.password == password)) {
                    _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                } else if ((user == null) && ((email == "demo@smartspend.vn") && (password == "password123"))) {
                    _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Email hoặc mật khẩu không chính xác.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Đã xảy ra lỗi.") }
            }
        }
    }

    fun register() {
        val name = _uiState.value.name.trim()
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng điền đầy đủ thông tin đăng ký.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val existing = repository.getUserByEmail(email)
                if (existing != null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Email này đã được đăng ký.") }
                } else {
                    repository.registerUser(UserEntity(email = email, password = password, name = name))
                    _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Đã xảy ra lỗi đăng ký.") }
            }
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            delay(800L)
            _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
        }
    }
}
