package com.avingard.firestore

data class DocumentChange(
    val type: Type,
    val document: DocumentSnapshot,
    val oldIndex: Int,
    val newIndex: Int
) {
    enum class Type {
        ADDED, MODIFIED, REMOVED
    }
}