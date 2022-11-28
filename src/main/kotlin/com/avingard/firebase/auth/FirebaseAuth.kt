package com.avingard.firebase.auth

import com.avingard.firebase.FirebaseApp
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import java.time.Instant

class FirebaseAuth(firebaseApp: FirebaseApp) {
    private val httpClient = firebaseApp.httpClient
    private val apiKey = firebaseApp.firestoreOptions.apiKey

    private val currentUser = MutableStateFlow<FirebaseUser?>(null)

    suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        val response = httpClient.post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey") {
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
            updateTokens(tokensFromData(data))
            currentUser.update { this }
        }
    }

    // tokens state - { updated: Boolean, tokens: Tokens }

    // getToken: tokens state . first { it.updated }

    //

    suspend fun signInWithRefreshToken(refreshToken: String): FirebaseUser {
        val data = exchangeRefreshTokenForIdToken(refreshToken)

        return FirebaseUser(
            id = data.userId,
            firebaseAuth = this
        ).apply {
            updateTokens(tokensFromData(data))
            currentUser.update { this }
        }
    }

    fun getCurrentUser(): FirebaseUser? {
        return currentUser.value
    }

    fun getCurrentUserFlow(): StateFlow<FirebaseUser?> = currentUser

    internal suspend fun refreshTokens(tokens: Tokens): Tokens {
        val data = exchangeRefreshTokenForIdToken(tokens.refreshToken)
        return tokensFromData(data)
    }

    private suspend fun exchangeRefreshTokenForIdToken(refreshToken: String): TokenExchangeResponseData {
        val response = httpClient.post("https://securetoken.googleapis.com/v1/token?key=$apiKey") {
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
            .setSkipAllValidators()
            .setSkipSignatureVerification()
            .build()

        return jwtConsumer.processToClaims(idToken).claimsMap
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
        val displayName: String
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
}