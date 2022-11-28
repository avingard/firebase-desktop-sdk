package com.avingard.firestore

import com.avingard.LOG
import com.google.firestore.v1.*
import com.google.firestore.v1.FirestoreGrpcKt.FirestoreCoroutineStub
import com.google.firestore.v1.StructuredQuery.FieldFilter
import com.google.firestore.v1.StructuredQuery.CompositeFilter
import com.google.firestore.v1.StructuredQuery.Order
import com.google.firestore.v1.StructuredQuery.Direction.*
import com.google.firestore.v1.StructuredQueryKt.collectionSelector
import com.google.firestore.v1.StructuredQueryKt.compositeFilter
import com.google.firestore.v1.StructuredQueryKt.fieldFilter
import com.google.firestore.v1.StructuredQueryKt.fieldReference
import com.google.firestore.v1.StructuredQueryKt.filter
import com.google.firestore.v1.StructuredQueryKt.order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.time.Instant

data class QueryOptions(
    val where: CompositeFilter = compositeFilter {},
    val orderBys: List<Order> = emptyList()
)

open class Query(
    val firestore: Firestore,
    val path: ResourcePath,
    private val queryOptions: QueryOptions
) {
    internal val scope = firestore.scope
    internal val stub = firestore.firestoreClient.stub


    @OptIn(ExperimentalCoroutinesApi::class)
    fun snapshots(): Flow<QuerySnapshot> {
        val dispatcher = Dispatchers.IO.limitedParallelism(1)
        val watch = Watch(this, stub).apply {
            scope.launch(dispatcher) { listen() }
        }

        return watch.getSnapshotFlow()
    }

    suspend fun await(): QuerySnapshot {
        try {
            val queryResponse = stub.runQuery(
                request = toProto()
            )

            val list = queryResponse.toList()
            val snapshots = list.filter { it.hasDocument() }.map {
                DocumentSnapshot.fromDocument(
                    firestore = firestore,
                    readTime = Instant.now(),
                    document = it.document
                )
            }


            return QuerySnapshot(documents = snapshots, readTime = Instant.now(), documentChanges = emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    private fun toProto() = runQueryRequest {
        parent = path.getParent().path
        structuredQuery = buildQuery()
    }

    internal fun buildQuery() = structuredQuery {
        val orderBys = queryOptions.orderBys
        val whereBuilder = queryOptions.where.toBuilder()

        if (whereBuilder.filtersList.isNotEmpty()) {
            whereBuilder.op = CompositeFilter.Operator.AND
            where = filter {
                compositeFilter = whereBuilder.build()
            }
        }
        from.add(
            collectionSelector {
                collectionId = path.getChildId()
            }
        )
        orderBy.addAll(orderBys)
    }

    internal fun comparator(): Comparator<DocumentSnapshot> {
        return Comparator { doc1, doc2 ->
            val orderBys = queryOptions.orderBys
            val lastDirection = if (orderBys.isEmpty()) ASCENDING else orderBys.last().direction
            val localOrderBys = orderBys.toMutableList()

            val order = order {
                field = fieldReference { fieldPath = "__name__" }
                direction = lastDirection
            }
            localOrderBys.add(order)

            for (orderBy in localOrderBys) {
                var comp: Int
                val path = orderBy.field.fieldPath

                comp = if (path == "__name__") {
                    doc1.reference.resourcePath.compareTo(doc2.reference.resourcePath)
                } else {
                    if (!(doc1.contains(path) && doc2.contains(path))) {
                        throw IllegalStateException("Can only compare fields that exist in the DocumentSnapshot. Please include the fields you are ordering on in your select() call.")
                    }

                    val v1 = doc1.extractField(path)!!
                    val v2 = doc2.extractField(path)!!

                    com.avingard.firestore.Order.compare(v1, v2)
                }

                if (comp != 0) {
                    val direction = if (orderBy.direction == ASCENDING) 1 else -1
                    return@Comparator direction * comp
                }
            }
            0
        }
    }

    fun where(field: String, equalTo: Any? = null): Query {
        val whereBuilder = queryOptions.where.toBuilder()

        whereBuilder.addFilters(
            filter {
                fieldFilter = fieldFilter {
                    this.op = FieldFilter.Operator.EQUAL
                    this.field = fieldReference { fieldPath = field }
                    this.value = equalTo.toValue()
                }
            }
        )
        val newQueryOptions = queryOptions.copy(where = whereBuilder.build())
        return Query(firestore, path, newQueryOptions)
    }

    fun where(
        field: String,
        equalTo: Any? = null,
        lessThan: Any? = null,
        lessThanOrEqual: Any? = null,
        greaterThan: Any? = null,
        greaterThanOrEqual: Any? = null,
        arrayContains: Any? = null,
        arrayContainsAny: List<Any>? = null
    ): Query {
        val whereBuilder = queryOptions.where.toBuilder()

        if (equalTo != null) {
            whereBuilder.addFilters(
                filter {
                    fieldFilter = fieldFilter {
                        this.op = FieldFilter.Operator.EQUAL
                        this.field = fieldReference { fieldPath = field }
                        this.value = equalTo.toValue()
                    }
                }
            )
        }
        if (lessThan != null) {
            whereBuilder.addFilters(
                filter {
                    fieldFilter = fieldFilter {
                        this.op = FieldFilter.Operator.LESS_THAN
                        this.field = fieldReference { fieldPath = field }
                        this.value = lessThan.toValue()
                    }
                }
            )
        }
        if (lessThanOrEqual != null) {
            whereBuilder.addFilters(
                filter {
                    fieldFilter = fieldFilter {
                        this.op = FieldFilter.Operator.LESS_THAN_OR_EQUAL
                        this.field = fieldReference { fieldPath = field }
                        this.value = lessThanOrEqual.toValue()
                    }
                }
            )
        }
        if (greaterThan != null) {
            whereBuilder.addFilters(
                filter {
                    fieldFilter = fieldFilter {
                        this.op = FieldFilter.Operator.GREATER_THAN
                        this.field = fieldReference { fieldPath = field }
                        this.value = greaterThan.toValue()
                    }
                }
            )
        }
        if (greaterThanOrEqual != null) {
            whereBuilder.addFilters(
                filter {
                    fieldFilter = fieldFilter {
                        this.op = FieldFilter.Operator.GREATER_THAN_OR_EQUAL
                        this.field = fieldReference { fieldPath = field }
                        this.value = greaterThanOrEqual.toValue()
                    }
                }
            )
        }
        if (arrayContains != null) {
            whereBuilder.addFilters(
                filter {
                    fieldFilter = fieldFilter {
                        this.op = FieldFilter.Operator.ARRAY_CONTAINS
                        this.field = fieldReference { fieldPath = field }
                        this.value = arrayContains.toValue()
                    }
                }
            )
        }
        if (arrayContainsAny != null) {
            whereBuilder.addFilters(
                filter {
                    fieldFilter = fieldFilter {
                        this.op = FieldFilter.Operator.ARRAY_CONTAINS_ANY
                        this.field = fieldReference { fieldPath = field }
                        this.value = value { arrayValue = arrayValue { values.addAll(arrayContainsAny.map { it.toValue() }) } }
                    }
                }
            )
        }
        val newQueryOptions = queryOptions.copy(where = whereBuilder.build())
        return Query(firestore, path, newQueryOptions)
    }

    fun ascendingOrderBy(field: String): Query {
        val orderBys = queryOptions.orderBys
        val orderBy = order {
            this.field = fieldReference { fieldPath = field }
            this.direction = ASCENDING
        }

        val appendedOrderBys = orderBys.plus(orderBy)
        val newQueryOptions = queryOptions.copy(orderBys = appendedOrderBys)
        return Query(firestore, path, newQueryOptions)
    }

    fun descendingOrderBy(field: String): Query {
        val orderBys = queryOptions.orderBys
        val orderBy = order {
            this.field = fieldReference { fieldPath = field }
            this.direction = DESCENDING
        }

        val appendedOrderBys = orderBys.plus(orderBy)
        val newQueryOptions = queryOptions.copy(orderBys = appendedOrderBys)
        return Query(firestore, path, newQueryOptions)
    }
}