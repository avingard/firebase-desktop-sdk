package com.avingard.firestore

import com.avingard.firestore.Order.TypeOrder.*
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeCase.*
import java.lang.Boolean
import java.util.*
import kotlin.Comparable
import kotlin.Comparator
import kotlin.Double
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.experimental.and


internal object Order : Comparator<Value> {
    enum class TypeOrder : Comparable<TypeOrder> {
        NULL,
        BOOLEAN,
        NUMBER,
        TIMESTAMP,
        STRING,
        BLOB,
        REF,
        GEO_POINT,
        ARRAY,
        OBJECT;

        companion object {
            fun fromValue(value: Value): TypeOrder {
                return when(value.valueTypeCase) {
                    NULL_VALUE -> NULL
                    BOOLEAN_VALUE -> BOOLEAN
                    INTEGER_VALUE -> NUMBER
                    DOUBLE_VALUE -> NUMBER
                    TIMESTAMP_VALUE -> TIMESTAMP
                    STRING_VALUE -> STRING
                    BYTES_VALUE -> BLOB
                    REFERENCE_VALUE -> REF
                    GEO_POINT_VALUE -> GEO_POINT
                    ARRAY_VALUE -> ARRAY
                    MAP_VALUE -> OBJECT
                    else -> {
                        throw IllegalArgumentException("Could not detect value type for $value")
                    }
                }
            }
        }
    }

    override fun compare(left: Value, right: Value): Int {
        val leftType = TypeOrder.fromValue(left)
        val rightType = TypeOrder.fromValue(right)
        val typeComparison = leftType.compareTo(rightType)

        if (typeComparison != 0) {
            return typeComparison
        }

        return when(leftType) {
            NULL -> 0
            BOOLEAN -> left.booleanValue.compareTo(right.booleanValue)
            NUMBER -> compareNumbers(left, right)
            TIMESTAMP -> compareTimestamps(left, right)
            STRING -> compareStrings(left, right)
            BLOB -> compareBlobs(left, right)
            REF -> compareResourcePaths(left, right)
            GEO_POINT -> compareGeoPoints(left, right)
            ARRAY -> compareArrays(left.arrayValue.valuesList, right.arrayValue.valuesList)
            OBJECT -> compareObjects(left, right)
        }
    }

    private fun compareStrings(left: Value, right: Value): Int {
        return left.stringValue.compareTo(right.stringValue)
    }

    private fun compareBlobs(left: Value, right: Value): Int {
        val leftBytes = left.bytesValue
        val rightBytes = right.bytesValue
        val size: Int = leftBytes.size().coerceAtMost(rightBytes.size())
        for (i in 0 until size) {
            // Make sure the bytes are unsigned
            val thisByte = (leftBytes.byteAt(i) and 0xff.toByte()).toInt()
            val otherByte = (rightBytes.byteAt(i) and 0xff.toByte()).toInt()
            if (thisByte < otherByte) {
                return -1
            } else if (thisByte > otherByte) {
                return 1
            }
            // Byte values are equal, continue with comparison
        }
        return leftBytes.size().compareTo(rightBytes.size())
    }

    private fun compareTimestamps(left: Value, right: Value): Int {
        val cmp = left.timestampValue.seconds.compareTo(right.timestampValue.seconds)
        return if (cmp != 0) {
            cmp
        } else {
            left.timestampValue.nanos.compareTo(right.timestampValue.nanos)
        }
    }

    private fun compareGeoPoints(left: Value, right: Value): Int {
        val cmp = left.geoPointValue.latitude.compareTo(right.geoPointValue.latitude)
        return if (cmp != 0) {
            cmp
        } else {
            left.geoPointValue.longitude.compareTo(right.geoPointValue.longitude)
        }
    }

    private fun compareResourcePaths(left: Value, right: Value): Int {
        val leftPath = ResourcePath(left.referenceValue)
        val rightPath = ResourcePath(right.referenceValue)
        return leftPath.compareTo(rightPath)
    }

    private fun compareArrays(left: List<Value>, right: List<Value>): Int {
        val minLength: Int = left.size.coerceAtMost(right.size)
        for (i in 0 until minLength) {
            val cmp = compare(left[i], right[i])
            if (cmp != 0) {
                return cmp
            }
        }
        return left.size.compareTo(right.size)
    }

    private fun compareObjects(left: Value, right: Value): Int {
        // This requires iterating over the keys in the object in order and doing a
        // deep comparison.
        val leftMap = TreeMap(left.mapValue.fieldsMap)
        val rightMap = TreeMap(right.mapValue.fieldsMap)
        val leftIterator = leftMap.entries.iterator()
        val rightIterator = rightMap.entries.iterator()
        while (leftIterator.hasNext() && rightIterator.hasNext()) {
            val leftEntry = leftIterator.next()
            val rightEntry = rightIterator.next()
            val keyCompare = leftEntry.key.compareTo(rightEntry.key)
            if (keyCompare != 0) {
                return keyCompare
            }
            val valueCompare = compare(leftEntry.value, rightEntry.value)
            if (valueCompare != 0) {
                return valueCompare
            }
        }

        // Only equal if both iterators are exhausted.
        return Boolean.compare(leftIterator.hasNext(), rightIterator.hasNext())
    }

    private fun compareNumbers(left: Value, right: Value): Int {
        return if (left.valueTypeCase == DOUBLE_VALUE) {
            if (right.valueTypeCase == DOUBLE_VALUE) {
                compareDoubles(left.doubleValue, right.doubleValue)
            } else {
                compareDoubles(left.doubleValue, right.integerValue.toDouble())
            }
        } else {
            if (right.valueTypeCase == INTEGER_VALUE) {
                left.integerValue.compareTo(right.integerValue)
            } else {
                compareDoubles(left.integerValue.toDouble(), right.doubleValue)
            }
        }
    }

    private fun compareDoubles(left: Double, right: Double): Int {
        // Firestore orders NaNs before all other numbers and treats -0.0, 0.0 and +0.0 as equal.

        if (java.lang.Double.isNaN(left)) {
            return if (java.lang.Double.isNaN(right)) 0 else -1
        }
        if (java.lang.Double.isNaN(right)) {
            return 1
        }
        val l = if (left == -0.0) 0.0 else left
        val r = if (right == -0.0) 0.0 else right
        return l.compareTo(r)
7    }
}