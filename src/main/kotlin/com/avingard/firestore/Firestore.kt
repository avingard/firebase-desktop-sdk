package com.avingard.firestore

import com.avingard.firebase.Firebase
import com.avingard.firebase.FirebaseOptions
import com.google.firestore.v1.FirestoreGrpcKt.FirestoreCoroutineStub
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob


internal class FirestoreClient(firebaseOptions: FirebaseOptions) {
    private val emulator = firebaseOptions.firestoreEmulator


    private var managedChannel: ManagedChannel = with(emulator) {
        if (useEmulator) {
            ManagedChannelBuilder
                .forAddress(address, port)
                .usePlaintext()
                .build()
        } else {
            ManagedChannelBuilder
                .forAddress("firestore.googleapis.com", 443)
                .useTransportSecurity()
                .build()
        }
    }

    val stub: FirestoreCoroutineStub = FirestoreCoroutineStub(managedChannel)
        .withCallCredentials(FirestoreCallCredentials(firebaseOptions))
}

class Firestore(firebase: Firebase) {
    private val firebaseOptions = firebase.firebaseOptions
    internal val firestoreClient = FirestoreClient(firebase.firebaseOptions)
    internal val scope = CoroutineScope(SupervisorJob())

    private val basePath = ResourcePath("projects/${firebaseOptions.projectId}/databases/(default)/documents")

    fun collection(path: String): CollectionReference {
        return CollectionReference(this, this.basePath.append(path))
    }

    companion object {
        val instance by lazy { Firebase.firestore }
    }
}


