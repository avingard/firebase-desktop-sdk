package com.avingard.firestore

import com.avingard.firebase.FirebaseApp
import com.avingard.firebase.FirebaseOptions
import com.google.firestore.v1.FirestoreGrpcKt.FirestoreCoroutineStub
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob


internal class FirestoreClient(firebaseOptions: FirebaseOptions) {
    private var managedChannel: ManagedChannel = ManagedChannelBuilder
        .forAddress("firestore.googleapis.com", 443)
        .useTransportSecurity()
        .build()

    val stub: FirestoreCoroutineStub = FirestoreCoroutineStub(managedChannel)
        .withCallCredentials(FirestoreCallCredentials(firebaseOptions))
}

class Firestore(firebaseApp: FirebaseApp) {
    private val firebaseOptions = firebaseApp.firestoreOptions
    internal val firestoreClient = FirestoreClient(firebaseApp.firestoreOptions)
    internal val scope = CoroutineScope(SupervisorJob())

    private val basePath = ResourcePath("projects/${firebaseOptions.projectId}/databases/(default)/documents")

    fun collection(path: String): CollectionReference {
        return CollectionReference(this, this.basePath.append(path))
    }
}


