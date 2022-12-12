package com.avingard

import com.google.firestore.v1.Document
import com.google.protobuf.Timestamp
import com.google.protobuf.timestamp
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
