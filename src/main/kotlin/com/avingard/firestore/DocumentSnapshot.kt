package com.avingard.firestore

import com.avingard.getDocumentId
import com.avingard.toInstant
import com.avingard.toLocalDateTime
import com.google.firestore.v1.Document
import com.google.firestore.v1.FirestoreGrpcKt.FirestoreCoroutineStub
import com.google.firestore.v1.Value
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure



data class DocumentSnapshot (
    val id: String,
    val data: Map<String, Any>,
    val reference: DocumentReference,
    val readTime: Instant,
    val updateTime: Instant,
    private val fields: Map<String, Value>
) {
    inline operator fun <reified T> get(field: String): T {
        val value = data[field]
        if (value is T) {
            return data[field] as T
        }
        throw RuntimeException("Couldn't cast $value to ${T::class}")
    }

    inline fun <reified T : Any> toObject(): T? {
       return if (exists())  {
           CustomClassMapper.convertToCustomClass(data, T::class, reference)
       } else {
           null
       }
    }

    fun contains(fieldPath: String): Boolean {
        return fields[fieldPath] != null
    }

    fun exists(): Boolean {
        return fields.isNotEmpty()
    }

    internal fun extractField(fieldPath: String): Value? {
        return fields[fieldPath]
    }

    companion object {
        internal fun fromDocument(firestore: Firestore, readTime: Instant, document: Document): DocumentSnapshot {
            return DocumentSnapshot(
                id = document.getDocumentId(),
                reference = DocumentReference(firestore, ResourcePath(document.name)),
                readTime = readTime,
                data = document.fieldsMap.mapValues { (_, value) -> value.toObject() },
                updateTime = document.updateTime.toInstant(),
                fields = document.fieldsMap
            )
        }
    }
}