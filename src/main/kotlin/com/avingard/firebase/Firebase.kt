package com.avingard.firebase

import com.avingard.firebase.auth.FirebaseAuth
import com.avingard.firestorage.Firestorage
import com.avingard.firestore.Firestore
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class Firebase private constructor(
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

        val auth by lazy { FirebaseAuth() }
        val firestore by lazy { Firestore(instance) }
        val firestorage by lazy { Firestorage(instance) }

        internal val instance by lazy {
            INSTANCE ?: throw RuntimeException("FirebaseApp has not been initialized!")
        }

        fun initializeApp(firestoreOptions: FirebaseOptions): Firebase {
            return INSTANCE ?: synchronized(this) {
                Firebase(firestoreOptions).also {
                    INSTANCE = it
                }
            }
        }
    }
}