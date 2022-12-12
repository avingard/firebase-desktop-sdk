package com.avingard.firestorage

import com.avingard.LOG
import com.avingard.firebase.Firebase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class Firestorage(firebase: Firebase) {
    private val httpClient = firebase.httpClient
    private val firebaseOptions = firebase.firebaseOptions
    private val emulator = firebaseOptions.firestorageEmulator

    private val baseUrl = with(emulator) {
        if (useEmulator) {
            "http://$address:$port/v0"
        } else {
            "https://firebasestorage.googleapis.com/v0"
        }
    }

    private val contentTypeMap = mapOf(
        "png" to "image/png",
        "jpeg" to "image/jpeg"
    )

    suspend fun getResource(resourcePath: String): ByteArray {
        return withContext(Dispatchers.IO) {
            val storageBucket = firebaseOptions.storageBucket
            if (storageBucket.isBlank()) {
                throw RuntimeException("Storage bucket is not set!")
            }

            val token = Firebase.auth.getCurrentUser()?.getIdToken()
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

        val token = Firebase.auth.getCurrentUser()?.getIdToken()
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

        val token = Firebase.auth.getCurrentUser()?.getIdToken()
        val contentType = contentTypeMap[resourcePath.split(".")[1]]

        val response = httpClient.post("$baseUrl/b/$storageBucket/o/${encodeUrl(resourcePath)}") {
            setBody(resource)
            headers {
                if (contentType != null) {
                    append(HttpHeaders.ContentType, contentType)
                }

                if (token != null) {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }

        LOG.info("${response.status}: ${response.bodyAsText()}")
    }

    companion object {
        val instance by lazy { Firebase.firestorage }
    }
}

private fun encodeUrl(url: String): String {
    return URLEncoder.encode(url, Charsets.UTF_8)
}