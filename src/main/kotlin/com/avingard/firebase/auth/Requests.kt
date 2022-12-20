package com.avingard.firebase.auth

import com.avingard.firebase.Firebase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object Requests {

    suspend fun makeEmailPasswordRequestCall(email: String, password: String): RegularAuthResponseData {
        val firebase = Firebase.instance
        val firebaseOptions = firebase.firebaseOptions
        val httpClient = firebase.httpClient

        val apiKey = firebaseOptions.apiKey
        val requestPrefix = with(firebaseOptions.firebaseAuthEmulator) {
            if (useEmulator) "http://$address:$port/" else "https://"
        }

        val response = httpClient.post("${requestPrefix}identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(RegularAuthRequestData(email, password))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorResponse = response.body<ErrorResponse>()
            throw RuntimeException(errorResponse.error.message)
        }

        return response.body()
    }

    suspend fun makeTokenRequestCall(refreshToken: String): TokenExchangeResponseData {
        val firebase = Firebase.instance
        val firebaseOptions = firebase.firebaseOptions
        val httpClient = firebase.httpClient

        val apiKey = firebaseOptions.apiKey
        val requestPrefix = with(firebaseOptions.firebaseAuthEmulator) {
            if (useEmulator) "http://$address:$port/" else "https://"
        }

        val response = httpClient.post("${requestPrefix}securetoken.googleapis.com/v1/token?key=$apiKey") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("grant_type=refresh_token&refresh_token=$refreshToken")
        }

        if (response.status != HttpStatusCode.OK) {
            val errorResponse = response.body<ErrorResponse>()
            throw RuntimeException(errorResponse.error.message)
        }

        return response.body()
    }

    @Serializable
    data class RegularAuthRequestData(
        val email: String,
        val password: String,
        val returnSecureToken: Boolean = true
    )

    @Serializable
    data class RegularAuthResponseData(
        val email: String,
        val idToken: String,
        val refreshToken: String,
        val expiresIn: String,
        val localId: String,
        val registered: Boolean,
        val displayName: String = ""
    )

    @Serializable
    data class TokenExchangeResponseData(
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
    data class ErrorResponse(val error: Error)

    @Serializable
    data class Error(
        val code: Int,
        val message: String
    )
}