package com.avingard

import com.google.firestore.v1.Document
import com.google.protobuf.Timestamp
import com.google.protobuf.timestamp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun Instant.toTimeStamp() = with(this) {
    timestamp { seconds = epochSecond }
}

fun Timestamp.toInstant(): Instant = with(this) {
    Instant.ofEpochSecond(seconds, nanos.toLong())
}

fun Instant.toLocalDateTime(): LocalDateTime {
    return atSystemZone().toLocalDateTime()
}

fun Instant.atSystemZone(): ZonedDateTime = atZone(ZoneId.systemDefault())

fun LocalDateTime.toTimeStamp(): Timestamp {
    val instant = this.atZone(ZoneId.systemDefault()).toInstant()
    return instant.toTimeStamp()
}

fun Document.getDocumentId(): String {
    return name.split("/").last()
}

suspend fun <T> StateFlow<T?>.firstNotNull(): T = first { it != null }!!