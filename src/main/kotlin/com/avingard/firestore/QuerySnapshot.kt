package com.avingard.firestore

import java.time.Instant

data class QuerySnapshot(
    val readTime: Instant,
    val documents: List<DocumentSnapshot>,
    val documentChanges: List<DocumentChange>
) {

    inline fun <reified T> toObjects(): List<T> = documents.mapNotNull { it.toObject() }

    companion object {
        internal fun withChanges(readTime: Instant, documentSet: DocumentSet, documentChanges: List<DocumentChange>): QuerySnapshot {
            return QuerySnapshot(
                readTime = readTime,
                documents = documentSet.toList(),
                documentChanges = documentChanges
            )
        }
    }
}