package com.avingard.firestore

import com.avingard.LOG
import com.avingard.firebase.FirebaseApp
import com.avingard.firebase.FirebaseOptions
import io.grpc.CallCredentials
import io.grpc.Metadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

internal class FirestoreCallCredentials(firebaseOptions: FirebaseOptions) : CallCredentials() {
    private val databaseUrl = "projects/${firebaseOptions.projectId}/databases/(default)"
    private val scope = CoroutineScope(SupervisorJob())
    private val auth = FirebaseApp.auth

    override fun applyRequestMetadata(requestInfo: RequestInfo?, executor: Executor?, metadataApplier: MetadataApplier?) {
        scope.launch {
            val metadata = Metadata()
            val currentUser = auth.getCurrentUser()

            if (currentUser != null) {
                LOG.debug("FirestoreCallCredentials: Auth user not null, setting auth headers.")
                metadata.put(AUTHORIZATION_HEADER, "Bearer ${currentUser.getIdToken()}")
            }

            metadataApplier?.apply(metadata.apply {
                put(RESOURCE_PREFIX_HEADER, databaseUrl)
                put(X_GOOG_REQUEST_PARAMS_HEADER, databaseUrl)
            })
        }
    }

    override fun thisUsesUnstableApi() {}

    companion object {
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
        private val RESOURCE_PREFIX_HEADER =
            Metadata.Key.of("google-cloud-resource-prefix", Metadata.ASCII_STRING_MARSHALLER)
        private val X_GOOG_REQUEST_PARAMS_HEADER =
            Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)
    }
}