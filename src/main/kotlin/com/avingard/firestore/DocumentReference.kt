package com.avingard.firestore

import com.google.firestore.v1.*
import com.google.firestore.v1.FirestoreGrpcKt.FirestoreCoroutineStub
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineScope
import java.time.Instant

class DocumentReference(
    internal val firestore: Firestore,
    val resourcePath: ResourcePath,
) {
    private val stub = firestore.firestoreClient.stub

    fun collection(collectionPath: String): CollectionReference {
        return CollectionReference(firestore, resourcePath.append(collectionPath))
    }

    suspend fun await(): DocumentSnapshot {
        val document = stub.getDocument(
            request = getDocumentRequest {
                name = resourcePath.path
            }
        )

        return DocumentSnapshot.fromDocument(
            firestore = firestore,
            document = document,
            readTime = Instant.now()
        )
    }

    suspend fun update(field: String, any: Any?) {
        stub.updateDocument(
            request = updateDocumentRequest {
                document = document {
                    name = resourcePath.path
                    fields.put(field, any.toValue())
                }
                updateMask = documentMask {
                    fieldPaths.add(field)
                }
            }
        )
    }

    suspend fun update(fieldMap: Map<String, Any?>) {
        stub.updateDocument(
            request = updateDocumentRequest {
                document = document {
                    name = resourcePath.path
                    fields.putAll(fieldMap.mapValues { (_, any ) -> any.toValue() })
                }
                updateMask = documentMask {
                    fieldPaths.addAll(fieldMap.keys)
                }
            }
        )
    }

    suspend fun set(any: Any?) {
        stub.updateDocument(
            request = updateDocumentRequest {
                document = document {
                    name = resourcePath.path
                    fields.putAll(any.toValue().mapValue.fieldsMap)
                }
            }
        )
    }

    suspend fun delete() {
        stub.deleteDocument(
            request = deleteDocumentRequest {
                name = resourcePath.path
            }
        )
    }

    fun getPath(): String {
        return resourcePath.toString()
    }

    fun getId(): String {
        return resourcePath.getChildId()
    }

    override fun toString(): String {
        val path = resourcePath.path.split("/(default)/documents")[1]
        return "DocumentReference(path=$path)"
    }
}