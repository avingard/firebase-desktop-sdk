package com.avingard.firestore

import com.avingard.firebase.CurrentMillisClock
import com.avingard.firebase.LOG
import com.avingard.firebase.toInstant
import com.google.api.gax.retrying.ExponentialRetryAlgorithm
import com.google.api.gax.retrying.RetrySettings
import com.google.api.gax.retrying.TimedAttemptSettings
import com.google.api.gax.rpc.ApiException
import com.google.firestore.v1.Document
import com.google.firestore.v1.FirestoreGrpcKt.FirestoreCoroutineStub
import com.google.firestore.v1.ListenRequest
import com.google.firestore.v1.ListenResponse.ResponseTypeCase.*
import com.google.firestore.v1.TargetChange.TargetChangeType.*
import com.google.firestore.v1.TargetKt.queryTarget
import com.google.firestore.v1.listenRequest
import com.google.firestore.v1.target
import com.google.protobuf.ByteString
import io.grpc.Status
import io.grpc.Status.Code
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import org.threeten.bp.Duration
import java.time.Instant
import java.util.UUID

private const val WATCH_TARGET_ID = 0x1

internal class Watch(
    private val query: Query,
    private val stub: FirestoreCoroutineStub,
    private val comparator: Comparator<DocumentSnapshot> = query.comparator()
) {
    private val WATCH_ID = UUID.randomUUID()

    private val retrySettings = RetrySettings.newBuilder()
        .setInitialRetryDelay(Duration.ofMillis(1000L))
        .setMaxRetryDelay(Duration.ofMillis(32_000L))
        .setRetryDelayMultiplier(2.0)
        .setTotalTimeout(Duration.ofMillis(50_000L))
        .setInitialRpcTimeout(Duration.ofMillis(50_000L))
        .setRpcTimeoutMultiplier(1.0)
        .setMaxRpcTimeout(Duration.ofMillis(50_000L))
        .build()

    private val requests = MutableStateFlow<ListenRequest?>(null)
    private val changeMap = mutableMapOf<ResourcePath, Document?>()
    private var documentSet = DocumentSet.emptySet(comparator)
    private var hasPushed: Boolean = false
    private val snapshotFlow = MutableStateFlow<QuerySnapshot?>(null)
    private var current: Boolean = false
    private var currentResumeToken: ByteString? = null
    private val backoff = ExponentialRetryAlgorithm(retrySettings, CurrentMillisClock())
    private var nextAttempt: TimedAttemptSettings? = null

    fun getSnapshotFlow(): Flow<QuerySnapshot> = snapshotFlow.filterNotNull()

    suspend fun listen() {
        val path = query.path
        val parent = path.getParent().path
        val structuredQuery = query.buildQuery()
        val listenRequest = listenRequest {
            database = path.getDatabase()
            addTarget = target {
                this.query = queryTarget {
                    this.parent = parent
                    this.structuredQuery = structuredQuery
                }
                this.targetId = WATCH_TARGET_ID
                if (currentResumeToken != null) {
                    resumeToken = currentResumeToken!!
                }
            }
        }

        current = false
        requests.update { listenRequest }

        if (nextAttempt == null) {
            val attempt = backoff.createFirstAttempt()
            delay(attempt.randomizedRetryDelay.toMillis())
        } else {
            delay(nextAttempt?.randomizedRetryDelay?.toMillis() ?: 150)
        }

        try {
            stub.listen(
                requests = requests.filterNotNull()
            ).collect { listenResponse ->
                when(listenResponse.responseTypeCase) {
                    TARGET_CHANGE -> {
                        val change = listenResponse.targetChange
                        val noTargetIds = change.targetIdsCount == 0

                        when(change.targetChangeType) {
                            NO_CHANGE -> {
                                if (noTargetIds && change.hasReadTime() && current) {
                                    pushSnapshot(change.readTime.toInstant(), change.resumeToken)
                                }
                            }
                            ADD -> {
                                if (WATCH_TARGET_ID != change.getTargetIds(0)) {
                                    throw RuntimeException("Target ID must be 0x01")
                                }
                            }
                            REMOVE -> {
                                throw RuntimeException("Backend ended Listen stream: ${change.cause.message}")
                            }
                            CURRENT -> {
                                current = true
                            }
                            RESET -> {
                                resetDocs()
                            }
                            else -> {
                                throw RuntimeException("Encountered invalid target change type: ${change.targetChangeType}")
                            }
                        }

                        if (change.resumeToken != null && affectsTarget(change.targetIdsList, WATCH_TARGET_ID)) {
                            nextAttempt = null
                        }
                    }
                    DOCUMENT_CHANGE -> {
                        val targetIds = listenResponse.documentChange.targetIdsList
                        val removedTargetIds = listenResponse.documentChange.removedTargetIdsList
                        val changed = targetIds.contains(WATCH_TARGET_ID)
                        val removed = removedTargetIds.contains(WATCH_TARGET_ID)

                        val document = listenResponse.documentChange.document
                        val name = ResourcePath(document.name)

                        if (changed) {
                            changeMap[name] = document
                        } else if (removed) {
                            changeMap[name] = null
                        }
                    }
                    DOCUMENT_DELETE -> {
                        changeMap[ResourcePath(listenResponse.documentDelete.document)] = null
                    }
                    DOCUMENT_REMOVE -> {
                        changeMap[ResourcePath(listenResponse.documentRemove.document)] = null
                    }
                    FILTER -> {
                        if (listenResponse.filter.count != currentSize()) {
                            resetDocs()
                            //this will restart listening
                            throw StatusException(Status.fromCode(Code.UNKNOWN))
                        }
                    }
                    else -> {
                        throw RuntimeException("Encountered invalid listen response type")
                    }
                }
            }
            if (nextAttempt != null) {
                nextAttempt = backoff.createNextAttempt(nextAttempt)
            }
            maybeReopenStream(StatusException(Status.fromCode(Code.UNKNOWN)))
        } catch (e: Exception) {
            e.printStackTrace()
            if (nextAttempt != null) {
                nextAttempt = backoff.createNextAttempt(nextAttempt)
            }
            maybeReopenStream(e)
        }
    }

    private fun pushSnapshot(readTime: Instant, nextResumeToken: ByteString) {
        val changes = computeSnapshot(readTime)

        if (!hasPushed || changes.isNotEmpty()) {
            val snapshot = QuerySnapshot.withChanges(readTime, documentSet, changes)
            snapshotFlow.update { snapshot }
            hasPushed = true
        }
        changeMap.clear()
        currentResumeToken = nextResumeToken
    }

    private fun computeSnapshot(readTime: Instant): List<DocumentChange> {
        val appliedChanges = mutableListOf<DocumentChange>()
        val changeSet = extractChanges(readTime)

        changeSet.deletes.sortedWith(comparator)
        for (delete in changeSet.deletes) {
            appliedChanges.add(deleteDoc(delete))
        }

        changeSet.adds.sortedWith(comparator)
        for (add in changeSet.adds) {
            appliedChanges.add(addDoc(add))
        }

        changeSet.updates.sortedWith(comparator)
        for (update in changeSet.updates) {
            val change = modifyDoc(update)
            if (change != null) {
                appliedChanges.add(change)
            }
        }
        return appliedChanges
    }

    private fun deleteDoc(oldDocument: DocumentSnapshot): DocumentChange {
        val resourcePath = oldDocument.reference.resourcePath
        val oldIndex = documentSet.indexOf(resourcePath)
        documentSet = documentSet.remove(resourcePath)

        return DocumentChange(
            document = oldDocument,
            type = DocumentChange.Type.REMOVED,
            oldIndex = oldIndex,
            newIndex = -1
        )
    }

    private fun addDoc(newDocument: DocumentSnapshot): DocumentChange {
        val resourcePath = newDocument.reference.resourcePath
        documentSet = documentSet.add(newDocument)
        val newIndex = documentSet.indexOf(resourcePath)

        return DocumentChange(
            document = newDocument,
            type = DocumentChange.Type.ADDED,
            oldIndex = -1,
            newIndex = newIndex
        )
    }

    private fun modifyDoc(newDocument: DocumentSnapshot): DocumentChange? {
        val resourcePath = newDocument.reference.resourcePath
        val oldDocument = documentSet.getDocument(resourcePath) ?: return null
        if (oldDocument.updateTime == newDocument.updateTime) return null

        val oldIndex = documentSet.indexOf(resourcePath)
        documentSet = documentSet.remove(resourcePath)
        documentSet = documentSet.add(newDocument)
        val newIndex = documentSet.indexOf(resourcePath)

        return DocumentChange(
            document = newDocument,
            type = DocumentChange.Type.MODIFIED,
            oldIndex = oldIndex,
            newIndex = newIndex
        )
    }

    private fun resetDocs() {
        changeMap.clear()
        currentResumeToken = null

        for (snapshot in documentSet) {
            changeMap[snapshot.reference.resourcePath] = null
        }
        current = false
    }

    private fun currentSize(): Int {
        val changeSet = extractChanges(Instant.now())
        return documentSet.size() + changeSet.adds.size - changeSet.deletes.size
    }

    private fun extractChanges(readTime: Instant): ChangeSet {
        val changeSet = ChangeSet()

        for (change in changeMap.entries) {
            if (change.value == null) {
                if (documentSet.contains(change.key)) {
                    changeSet.deletes.add(documentSet.getDocument(change.key)!!)
                }
                continue
            }
            val snapshot = DocumentSnapshot.fromDocument(
                firestore = query.firestore,
                readTime = readTime,
                document = change.value!!
            )


            if (documentSet.contains(change.key)) {
                changeSet.updates.add(snapshot)
            } else {
                changeSet.adds.add(snapshot)
            }
        }
        return changeSet
    }

    private suspend fun maybeReopenStream(throwable: Throwable?) {
        LOG.debug("Watch-$WATCH_ID: Stream ended.")
        if (nextAttempt != null) {
            nextAttempt = backoff.createNextAttempt(nextAttempt)
        }
        if (!isPermanentError(throwable)) {
            LOG.debug("Watch-$WATCH_ID: Reopening stream.")
            if (isResourceExhaustedError(throwable)) {
                nextAttempt = backoff.createNextAttempt(nextAttempt)
            }
            changeMap.clear()
            listen()
        }
    }

    private fun isPermanentError(throwable: Throwable?): Boolean {
        val status = getStatus(throwable) ?: return true

        return when(status.code) {
            Code.CANCELLED, Code.UNKNOWN,
            Code.DEADLINE_EXCEEDED, Code.RESOURCE_EXHAUSTED,
            Code.INTERNAL, Code.UNAVAILABLE, Code.UNAUTHENTICATED -> { false }
            else -> { true }
        }
    }

    private fun isResourceExhaustedError(throwable: Throwable?): Boolean {
        val status = getStatus(throwable)
        return status != null && status.code == Code.RESOURCE_EXHAUSTED
    }

    private fun getStatus(throwable: Throwable?): Status? {
        return if (throwable is StatusRuntimeException) {
            throwable.status
        } else if (throwable is StatusException) {
            throwable.status
        } else if (throwable is ApiException && throwable.statusCode.transportCode is Code) {
            val code = throwable.statusCode.transportCode as Code
            code.toStatus()
        } else {
            null
        }
    }

    private fun affectsTarget(targetIds: List<Int>?, currentId: Int): Boolean {
        return targetIds.isNullOrEmpty() || targetIds.contains(currentId)
    }

    private class ChangeSet(
        val deletes: MutableList<DocumentSnapshot> = mutableListOf(),
        val adds: MutableList<DocumentSnapshot> = mutableListOf(),
        val updates: MutableList<DocumentSnapshot> = mutableListOf()
    )
}