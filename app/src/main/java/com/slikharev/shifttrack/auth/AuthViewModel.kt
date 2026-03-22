package com.slikharev.shifttrack.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.data.local.AppDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appDataStore: AppDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * User is considered logged in if Firebase reports a current user (online)
     * OR we have cached credentials (allows offline re-entry after first login).
     */
    val isLoggedIn: StateFlow<Boolean> = authRepository.authStateFlow
        .map { firebaseUser ->
            firebaseUser != null || authRepository.hasCachedCredentials()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = authRepository.currentUser != null || authRepository.hasCachedCredentials(),
        )

    val onboardingComplete: StateFlow<Boolean> = appDataStore.onboardingComplete
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.signInWithGoogle(idToken)
            _uiState.value = result.fold(
                onSuccess = { user -> AuthUiState.Success(user.uid) },
                onFailure = { e -> AuthUiState.Error(e.message ?: "Sign-in failed") },
            )
        }
    }

    fun onSignInError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = AuthUiState.Idle
    }
}
