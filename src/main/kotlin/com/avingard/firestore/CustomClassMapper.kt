package com.avingard.firestore

import com.avingard.firestore.annotations.DocumentId
import com.avingard.firebase.toLocalDateTime
import java.time.Instant
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

object CustomClassMapper {

    fun <T : Any> convertToCustomClass(data: Map<String, Any>, valueType: KClass<T>, docRef: DocumentReference): T? {
        return try {
            val constructor = valueType.primaryConstructor
                ?: throw RuntimeException("Couldn't find primary constructor for ${valueType.simpleName}")
            val paramMap = constructor.parameters.associateBy({it}, { mapDataToParameter(data, it, docRef) })

            constructor.callBy(paramMap)
        } catch (e: Exception) {
            null
        }
    }

    private fun mapDataToParameter(data: Map<String, Any>, parameter: KParameter, docRef: DocumentReference): Any? {
        if (parameter.hasAnnotation<DocumentId>()) {
            return docRef.getId()
        }

        return deserialize(data[parameter.name], parameter.type, docRef)
    }

    private fun deserialize(data: Any?, targetType: KType, docRef: DocumentReference): Any? {
        val targetClass = targetType.jvmErasure.java

        return when(data) {
            is String -> {
                if (Enum::class.java.isAssignableFrom(targetClass)) {
                    val enumValues = targetClass.enumConstants as Array<Enum<*>>
                    enumValues.first { it.name == data }
                } else if (DocumentReference::class.java.isAssignableFrom(targetClass)) {
                    DocumentReference(
                        firestore = docRef.firestore,
                        resourcePath = ResourcePath(data),
                    )
                } else {
                    data
                }
            }

            is Long -> {
                if (Int::class.java.isAssignableFrom(targetClass)) {
                    data.toInt()
                } else {
                    data
                }
            }

            is Double -> {
                if (Float::class.java.isAssignableFrom(targetClass)) {
                    data.toFloat()
                } else {
                    data
                }
            }

            is Instant -> {
                if (LocalDateTime::class.java.isAssignableFrom(targetClass)) {
                    data.toLocalDateTime()
                } else {
                    data
                }
            }

            else -> data
        }
    }
}