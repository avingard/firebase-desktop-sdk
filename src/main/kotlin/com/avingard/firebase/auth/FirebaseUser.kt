package com.avingard.firebase.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant


class FirebaseUser(
    val id: String,
    private val firebaseAuth: FirebaseAuth
) {
    private val mutex = Mutex()
    private val currentTokens = MutableStateFlow(Tokens())

    suspend fun getIdToken() = mutex.withLock {
        val tokens = currentTokens.value

        if (tokens.expiresAt < Instant.now()) {
            val refreshedTokens = firebaseAuth.refreshTokens(tokens)
            updateTokens(refreshedTokens)
            refreshedTokens.idToken
        } else {
            tokens.idToken
        }
    }

    fun getRefreshToken(): String {
        return currentTokens.value.refreshToken
    }

    fun getRefreshTokenFlow(): Flow<String> {
        return currentTokens.map { it.refreshToken }
    }

    fun claims(): Map<String, Any> {
        return currentTokens.value.claimsMap
    }

    fun <T> claim(name: String): T? {
        val value = currentTokens.value.claimsMap[name] ?: return null
        return value as T
    }

    internal fun updateTokens(tokens: Tokens) {
        currentTokens.update { tokens }
    }
}
