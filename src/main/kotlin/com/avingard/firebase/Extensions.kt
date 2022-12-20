package com.avingard.firebase

import com.avingard.firebase.auth.FirebaseAuth
import com.google.firestore.v1.Document
import com.google.protobuf.Timestamp
import com.google.protobuf.timestamp
import com.starxg.keytar.Keytar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal fun Instant.toTimeStamp() = with(this) { timestamp { seconds = epochSecond } }

internal fun Timestamp.toInstant(): Instant = with(this) { Instant.ofEpochSecond(seconds, nanos.toLong()) }

internal fun Instant.toLocalDateTime(): LocalDateTime = atSystemZone().toLocalDateTime()

internal fun Instant.atSystemZone(): ZonedDateTime = atZone(ZoneId.systemDefault())

internal fun LocalDateTime.toTimeStamp(): Timestamp = atZone(ZoneId.systemDefault()).toInstant().toTimeStamp()

internal fun Document.getDocumentId(): String = name.split("/").last()

internal val Any.className: String get() = javaClass.name

internal suspend fun Keytar.setAuthCredential(key: String, value: String) {
    withContext(Dispatchers.IO) {
        try {
            setPassword(FirebaseAuth.SERVICE, key, value)
        } catch (e: Exception) {
            LOG.error("Failed to save $key credential", e)
        }
    }
}

internal suspend fun Keytar.getAuthCredential(key: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            getPassword(FirebaseAuth.SERVICE, key)
        } catch (e: Exception) {
            LOG.error("Failed to load $key credential", e)
            null
        }
    }
}

internal suspend fun Keytar.clearAuthCredentials() {
    withContext(Dispatchers.IO) {
        try {
            val credentials = getCredentials(FirebaseAuth.SERVICE)
            credentials.forEach { (key, _) ->
                deletePassword(FirebaseAuth.SERVICE, key)
            }
        } catch (e: Exception) {
            LOG.error("Failed to clear credentials from auth store", e)
        }
    }
}