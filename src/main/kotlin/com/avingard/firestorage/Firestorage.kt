package com.avingard.firestorage

import com.avingard.firebase.FirebaseApp
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class Firestorage(firebaseApp: FirebaseApp) {
    private val httpClient = firebaseApp.httpClient
    private val firebaseOptions = firebaseApp.firestoreOptions
    private val baseUrl = "https://firebasestorage.googleapis.com/v0"

    suspend fun getResource(resourcePath: String): ByteArray {
        return withContext(Dispatchers.IO) {
            val storageBucket = firebaseOptions.storageBucket
            if (storageBucket.isBlank()) {
                throw RuntimeException("Storage bucket is not set!")
            }

            val token = FirebaseApp.auth.getCurrentUser()?.getIdToken()
            val response = httpClient.get("$baseUrl/b/$storageBucket/o/${encodeUrl(resourcePath)}?alt=media") {
                headers {
                    if (token != null) {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
            response.readBytes()
        }
    }

    suspend fun getResourceAsChannel(resourcePath: String): ByteReadChannel {
        val storageBucket = firebaseOptions.storageBucket
        if (storageBucket.isBlank()) {
            throw RuntimeException("Storage bucket is not set!")
        }

        val token = FirebaseApp.auth.getCurrentUser()?.getIdToken()
        val response = httpClient.get("$baseUrl/b/$storageBucket/o/${encodeUrl(resourcePath)}?alt=media") {
            headers {
                if (token != null) {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }
        return response.bodyAsChannel()
    }

    suspend fun saveResource(resourcePath: String, resource: ByteArray) {
        val storageBucket = firebaseOptions.storageBucket
        if (storageBucket.isBlank()) {
            throw RuntimeException("Storage bucket is not set!")
        }

        val token = FirebaseApp.auth.getCurrentUser()?.getIdToken()
        httpClient.submitFormWithBinaryData(
            url = "$baseUrl/b/$storageBucket/o/${encodeUrl(resourcePath)}",
            formData = formData {
                append("image", resource, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                })
            }
        ) {
            headers {
                if (token != null) {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }
    }

    companion object {
        val instance by lazy { FirebaseApp.firestorage }
    }
}

private fun encodeUrl(url: String): String {
    return URLEncoder.encode(url, Charsets.UTF_8)
}