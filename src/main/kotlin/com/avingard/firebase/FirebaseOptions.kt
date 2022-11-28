package com.avingard.firebase

class FirebaseOptionsBuilder {
    var apiKey: String = ""
    var projectId: String = ""
    var storageBucket: String = ""

    fun build(): FirebaseOptions {
        return FirebaseOptions(
            apiKey = apiKey,
            projectId = projectId,
            storageBucket = storageBucket
        )
    }
}

data class FirebaseOptions(
    val apiKey: String,
    val projectId: String,
    val storageBucket: String,
)

fun firebaseOptions(block: FirebaseOptionsBuilder.() -> Unit): FirebaseOptions {
    val builder = FirebaseOptionsBuilder()
    return builder.apply(block).build()
}