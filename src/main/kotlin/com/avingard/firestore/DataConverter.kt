package com.avingard.firestore

import com.avingard.LOG
import com.avingard.firestore.annotations.DocumentId
import com.avingard.firestore.annotations.IgnoreProperty
import com.avingard.toInstant
import com.avingard.toTimeStamp
import com.google.firestore.v1.Value
import com.google.firestore.v1.arrayValue
import com.google.firestore.v1.mapValue
import com.google.firestore.v1.value
import com.google.protobuf.ByteString
import com.google.protobuf.NullValue
import java.time.Instant
import java.time.LocalDateTime
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

internal fun Any?.toValue(): Value {
    val any = this
    return value {
        when (any) {
            null -> { nullValue = NullValue.NULL_VALUE }
            is String -> { stringValue = any }
            is Enum<*> -> { stringValue = any.name }
            is Number -> {
                when (any) {
                    is Int, is Long -> { integerValue = any.toLong() }
                    is Double, is Float -> { doubleValue = any.toDouble() }
                    else -> { throw RuntimeException("Numbers of type ${any::class.simpleName} are not supported, please use an int, long, float or double") }
                }
            }
            is Instant -> { timestampValue = any.toTimeStamp() }
            is LocalDateTime -> { timestampValue = any.toTimeStamp() }
            is Boolean -> { booleanValue = any }
            is ByteArray -> { bytesValue = ByteString.copyFrom(any) }
            is Collection<*> -> {
                if (any !is List<*>) throw RuntimeException("Serializing Collections is not supported, use Lists instead")
                arrayValue = arrayValue {
                    values.addAll(any.map { it.toValue() })
                }
            }
            is Map<*, *> -> {
                val hasOnlyStringKeys = any.all { (key, _) -> key is String }
                if (!hasOnlyStringKeys) throw RuntimeException("Maps with non-string keys are not supported")

                mapValue = mapValue {
                    fields.putAll(any.mapKeys { it as String}.mapValues { it.toValue() })
                }
            }
            else -> { mapValue = mapValue { fields.putAll(any.convertToStringValueMap()) } }
        }
    }
}


internal fun Value.toObject(): Any {
    return when(valueTypeCase) {
        Value.ValueTypeCase.ARRAY_VALUE -> arrayValue.valuesList.map { it.toObject() }
        Value.ValueTypeCase.BOOLEAN_VALUE -> booleanValue
        Value.ValueTypeCase.BYTES_VALUE -> bytesValue
        Value.ValueTypeCase.DOUBLE_VALUE -> doubleValue
        Value.ValueTypeCase.GEO_POINT_VALUE -> {}
        Value.ValueTypeCase.INTEGER_VALUE -> integerValue
        Value.ValueTypeCase.MAP_VALUE -> mapValue.fieldsMap.mapValues { it.value.toObject() }
        Value.ValueTypeCase.NULL_VALUE -> {}
        Value.ValueTypeCase.REFERENCE_VALUE -> referenceValue
        Value.ValueTypeCase.VALUETYPE_NOT_SET -> {}
        Value.ValueTypeCase.STRING_VALUE -> stringValue
        Value.ValueTypeCase.TIMESTAMP_VALUE -> timestampValue.toInstant()
        else -> {}
    }
}

private fun Any.convertToStringValueMap(): Map<String, Value> {
    val clazz = this::class
    val primaryConstructor = clazz.primaryConstructor
    val fieldName = primaryConstructor?.parameters?.find { it.hasAnnotation<DocumentId>() }?.name

    return clazz.declaredMemberProperties
        .map { it as KProperty1<Any, *> }
        .filterNot { (it.hasAnnotation<IgnoreProperty>() || it.name == fieldName) }
        .associateBy({ it.name }, { it.get(this).toValue() })
}