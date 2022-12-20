package com.avingard.firebase.auth

import com.avingard.firebase.*
import com.starxg.keytar.Keytar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FirebaseAuth() {
    private val currentUser = MutableStateFlow<FirebaseUser?>(null)

    suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        val data = Requests.makeEmailPasswordRequestCall(email, password)
        val tokens = Tokens.fromData(data)

        return FirebaseUser(
            id = data.localId,
            firebaseAuth = this
        ).apply {
            updateTokens(tokens)
            currentUser.update { this }

            saveRefreshToken(id, tokens)
        }
    }

    suspend fun getCurrentUser(): FirebaseUser? {
        val currentUser = currentUser.value
        if (currentUser != null) return currentUser

        return try {
            val refreshToken = getSavedRefreshToken() ?: return null
            signInWithRefreshToken(refreshToken)
        } catch (e: Exception) {
            LOG.debug("Failed token auth", e)
            return null
        }
    }

    fun getCurrentUserFlow(): StateFlow<FirebaseUser?> = currentUser

    internal suspend fun refreshTokens(tokens: Tokens): Tokens {
        val data = Requests.makeTokenRequestCall(tokens.refreshToken)
        return Tokens.fromData(data)
    }

    private suspend fun signInWithRefreshToken(refreshToken: String): FirebaseUser {
        val data = Requests.makeTokenRequestCall(refreshToken)
        val tokens = Tokens.fromData(data)

        return FirebaseUser(
            id = data.userId,
            firebaseAuth = this
        ).apply {
            updateTokens(tokens)
            currentUser.update { this }

            saveRefreshToken(id, tokens)
        }
    }

    private suspend fun saveRefreshToken(userId: String, tokens: Tokens) {
        val keytar = Keytar.getInstance()
        val preferences = LocalPreferences.instance

        preferences.setPreference("uid", userId)
        keytar.setAuthCredential(userId, tokens.refreshToken)
    }

    private suspend fun getSavedRefreshToken(): String? {
        val keytar = Keytar.getInstance()
        val preferences = LocalPreferences.instance
        val userId = preferences.getPreference("uid") ?: return null

        return keytar.getAuthCredential(userId)
    }

    private suspend fun clearSavedCredentials() {
        Keytar.getInstance().clearAuthCredentials()
        LocalPreferences.instance.clear()
    }

    companion object {
        internal const val SERVICE = "com.avingard.firebase"
        val instance by lazy { Firebase.auth }
    }
}