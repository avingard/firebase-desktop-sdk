package com.avingard.firestore

data class ResourcePath(val path: String) : Comparable<ResourcePath> {
    private val segments = path.split("/").filterNot { it.isBlank() }

    internal fun append(childPath: String): ResourcePath {
        return ResourcePath("$path/$childPath")
    }

    fun getDatabase(): String {
        return segments.slice(0..3).joinToString("/")
    }

    fun getParent(): ResourcePath {
        return ResourcePath(segments.slice(0 until segments.lastIndex).joinToString("/"))
    }

    fun getChildId(): String {
        return segments.last()
    }

    override fun compareTo(other: ResourcePath): Int {
        //TODO: compare project id and database name first

        val length: Int = segments.size.coerceAtMost(other.segments.size)
        for (i in 0 until length) {
            val cmp: Int = segments[i].compareTo(other.segments[i])
            if (cmp != 0) {
                return cmp
            }
        }
        return segments.size.compareTo(other.segments.size)
    }
}