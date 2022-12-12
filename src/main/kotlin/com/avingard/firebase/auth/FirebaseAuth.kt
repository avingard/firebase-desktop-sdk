package com.avingard.firebase.auth

import com.avingard.LOG
import com.avingard.className
import com.avingard.firebase.Firebase
import com.starxg.keytar.Keytar
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import java.time.Instant
import java.util.prefs.Preferences

class FirebaseAuth(firebase: Firebase) {
    private val httpClient = firebase.httpClient
    private val apiKey = firebase.firebaseOptions.apiKey
    private val emulator = firebase.firebaseOptions.firebaseAuthEmulator

    private val keytar = Keytar.getInstance()
    private val preferences = Preferences.userNodeForPackage(javaClass)

    private val requestRegex = with(emulator) {
        if (useEmulator) "http://$address:$port/" else "https://"
    }

    private val currentUser = MutableStateFlow<FirebaseUser?>(null)

    suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        val response = httpClient.post("${requestRegex}identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(RegularAuthRequestData(email, password))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorResponse = response.body<ErrorResponse>()
            throw RuntimeException(errorResponse.error.message)
        }

        val data = response.body<RegularAuthResponseData>()

        return FirebaseUser(
            id = data.localId,
            firebaseAuth = this
        ).apply {
            val tokens = tokensFromData(data)
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
            signInWithRefreshToken(refreshToken).also { newUser ->
                this.currentUser.update { newUser }
            }
        } catch (e: Exception) {
            LOG.error("Failed auto login", e)
            return null
        }
    }

    fun getCurrentUserFlow(): StateFlow<FirebaseUser?> = currentUser

    internal suspend fun refreshTokens(tokens: Tokens): Tokens {
        val data = exchangeRefreshTokenForIdToken(tokens.refreshToken)
        return tokensFromData(data)
    }

    private suspend fun signInWithRefreshToken(refreshToken: String): FirebaseUser {
        val data = exchangeRefreshTokenForIdToken(refreshToken)

        return FirebaseUser(
            id = data.userId,
            firebaseAuth = this
        ).apply {
            val tokens = tokensFromData(data)
            updateTokens(tokens)
            currentUser.update { this }
            saveRefreshToken(id, tokens)
        }
    }

    private suspend fun exchangeRefreshTokenForIdToken(refreshToken: String): TokenExchangeResponseData {
        val response = httpClient.post("${requestRegex}securetoken.googleapis.com/v1/token?key=$apiKey") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("grant_type=refresh_token&refresh_token=$refreshToken")
        }

        if (response.status != HttpStatusCode.OK) {
            val errorResponse = response.body<ErrorResponse>()
            throw RuntimeException(errorResponse.error.message)
        }

        return response.body()
    }

    private fun tokensFromData(data: RegularAuthResponseData) = Tokens(
        idToken = data.idToken,
        refreshToken = data.refreshToken,
        expiresAt = Instant.now().plusSeconds(data.expiresIn.toLong()),
        claimsMap = extractUserClaims(data.idToken)
    )

    private fun tokensFromData(data: TokenExchangeResponseData): Tokens {
        return Tokens(
            idToken = data.idToken,
            refreshToken = data.refreshToken,
            expiresAt = Instant.now().plusSeconds(data.expiresIn.toLong()),
            claimsMap = extractUserClaims(data.idToken)
        )
    }

    private fun extractUserClaims(idToken: String): Map<String, Any> {
        val jwtConsumer = JwtConsumerBuilder()
            .setDisableRequireSignature()
            .setSkipAllValidators()
            .setSkipSignatureVerification()
            .build()

        return jwtConsumer.processToClaims(idToken).claimsMap
    }

    private suspend fun saveRefreshToken(userId: String, tokens: Tokens) {
        try {
            withContext(Dispatchers.IO) {
                preferences.put("user_id", userId)
                keytar.setPassword(className, userId, tokens.refreshToken)
            }
        } catch (e: Exception) {
            LOG.error("FirebaseAuth: Failed to save refresh token", e)
        }
    }

    private suspend fun getSavedRefreshToken(): String? {
        return withContext(Dispatchers.IO) {
            val userId = preferences.get("user_id", null) ?: return@withContext null
            keytar.getPassword(className, userId)
        }
    }

    private suspend fun clearSavedCredentials() {
        withContext(Dispatchers.IO) {
            preferences.clear()
            val credentials = keytar.getCredentials(className)
            credentials.forEach { (name, _) ->
                keytar.deletePassword(className, name)
            }
        }
    }

    @Serializable
    private data class RegularAuthRequestData(
        val email: String,
        val password: String,
        val returnSecureToken: Boolean = true
    )

    @Serializable
    private data class RegularAuthResponseData(
        val email: String,
        val idToken: String,
        val refreshToken: String,
        val expiresIn: String,
        val localId: String,
        val registered: Boolean,
        val displayName: String = ""
    )

    @Serializable
    private data class TokenExchangeResponseData(
        @SerialName("expires_in")
        val expiresIn: String,
        @SerialName("token_type")
        val tokenType: String,
        @SerialName("refresh_token")
        val refreshToken: String,
        @SerialName("id_token")
        val idToken: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("project_id")
        val projectId: String
    )

    @Serializable
    private data class ErrorResponse(val error: Error)

    @Serializable
    private data class Error(
        val code: Int,
        val message: String
    )

    companion object {
        val instance by lazy { Firebase.auth }
    }
}