package com.avingard.firebase

import com.avingard.firebase.auth.FirebaseAuth
import com.avingard.firestorage.Firestorage
import com.avingard.firestore.Firestore
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class Firebase(
    val firebaseOptions: FirebaseOptions
) {
    internal val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                encodeDefaults = true
                isLenient = true
                allowSpecialFloatingPointValues = true
                allowStructuredMapKeys = true
                prettyPrint = true
                useArrayPolymorphism = false
                ignoreUnknownKeys = true
            })
        }
        engine {
            config {
                followRedirects(true)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: Firebase? = null

        val auth by lazy { FirebaseAuth(getInstance()) }
        val firestore by lazy { Firestore(getInstance()) }
        val firestorage by lazy { Firestorage(getInstance()) }

        fun initializeApp(firestoreOptions: FirebaseOptions): Firebase {
            return INSTANCE ?: synchronized(this) {
                Firebase(firestoreOptions).also {
                    INSTANCE = it
                }
            }
        }

        private fun getInstance(): Firebase {
            return INSTANCE ?: throw RuntimeException("FirebaseApp has not been initialized!")
        }
    }
}