package com.avingard.firestore

import com.avingard.generateAutoId
import com.avingard.getDocumentId
import com.google.firestore.v1.*
import kotlinx.coroutines.CoroutineScope

class CollectionReference(
    firestore: Firestore,
    path: ResourcePath
): Query(firestore, path, QueryOptions()) {

    fun document(): DocumentReference {
        return DocumentReference(firestore, path.append(generateAutoId()))
    }

    fun document(childPath: String): DocumentReference {
        return DocumentReference(firestore, path.append(childPath))
    }

    suspend fun add(data: Any?): DocumentReference {
        val response = stub.createDocument(
            request = createDocumentRequest {
                parent = path.getParent().path
                collectionId = path.getChildId()
                document = document {
                    fields.putAll(data.toValue().mapValue.fieldsMap)
                }
            }
        )
        return DocumentReference(firestore, path.append(response.getDocumentId()))
    }
}