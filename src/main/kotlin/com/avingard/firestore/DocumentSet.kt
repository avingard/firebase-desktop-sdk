package com.avingard.firestore


internal class DocumentSet(
    private val keyIndex: Map<ResourcePath, DocumentSnapshot>,
    private val sortedSet: Set<DocumentSnapshot>,
    private val comparator: Comparator<DocumentSnapshot>
): Iterable<DocumentSnapshot> {

    fun size(): Int = keyIndex.size

    fun isEmpty(): Boolean = keyIndex.isEmpty()

    fun contains(key: ResourcePath): Boolean {
        return keyIndex.containsKey(key)
    }

    fun getDocument(key: ResourcePath): DocumentSnapshot? = keyIndex[key]

    fun indexOf(key: ResourcePath): Int {
        val document = keyIndex[key] ?: - 1
        return sortedSet.indexOf(document)
    }

    fun add(document: DocumentSnapshot): DocumentSet {
        val removed = remove(document.reference.resourcePath)
        val newKeyIndex = removed.keyIndex.toMutableMap().apply { put(document.reference.resourcePath, document) }
        val newSortedSet = removed.sortedSet.toMutableSet().apply { add(document) }

        return DocumentSet(newKeyIndex, newSortedSet.toSortedSet(comparator), comparator)
    }

    fun remove(key: ResourcePath): DocumentSet {
        val document = keyIndex[key] ?: return this
        val newKeyIndex = keyIndex.toMutableMap().apply { remove(key) }
        val newSortedSet = sortedSet.toMutableSet().apply { remove(document) }

        return DocumentSet(newKeyIndex, newSortedSet.toSortedSet(comparator), comparator)
    }

    fun toList(): List<DocumentSnapshot> {
        val documents = mutableListOf<DocumentSnapshot>()
        for (document in this) {
            documents.add(document)
        }
        return documents
    }

    override fun iterator(): Iterator<DocumentSnapshot> {
        return sortedSet.iterator()
    }

    companion object {
        private val EMPTY_DOCUMENT_MAP = emptyMap<ResourcePath, DocumentSnapshot>()
        fun emptySet(comparator: Comparator<DocumentSnapshot>): DocumentSet {
            return DocumentSet(EMPTY_DOCUMENT_MAP, emptySet(), comparator)
        }
    }
}