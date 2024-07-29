package com.automattic.android.tracks.crashlogging.performance

import android.util.Log
import com.automattic.android.tracks.crashlogging.performance.internal.SentryPerformanceMonitoringWrapper
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.NoOpSpan
import io.sentry.SpanStatus
import java.util.UUID

/**
 * This class has to be a Singleton. It holds state of active performance transactions.
 */
class PerformanceTransactionRepository internal constructor(private val sentryWrapper: SentryPerformanceMonitoringWrapper) {

    private val transactions: MutableMap<TransactionId, ITransaction> = mutableMapOf()
    private val spans: MutableMap<SpanId, ISpan> = mutableMapOf()

    fun startTransaction(name: String, operation: TransactionOperation): TransactionId {
        return sentryWrapper.startTransaction(name, operation.value, true).let {
            val transactionId = TransactionId(it.eventId.toString())
            transactions[transactionId] = it
            transactionId
        }
    }

    fun finishTransaction(transactionId: TransactionId, transactionStatus: TransactionStatus) {
        transactions[transactionId]?.finish(transactionStatus.toSentrySpanStatus())
    }

    fun startSpan(transactionId: TransactionId, spanName: String, operation: String): SpanId {
        val transaction = transactions[transactionId]
        val spanId = SpanId("$transactionId - ${UUID.randomUUID()}")

        return if (transaction != null) {
            val span = transaction.startChild(spanName, operation)
            spans[spanId] = span
            spanId
        } else {
            Log.e("PerformanceTransaction", "Transaction with id $transactionId not found")
            NoOpSpan.getInstance()
            SpanId("NoOpSpan")
        }
    }

    /**
     * @param spanHttpCode The HTTP status code of the span. This will be used to determine the span status, but doesn't mean that span is an HTTP span.
     *
     * @see io.sentry.SpanStatus
     */
    fun finishSpan(spanId: SpanId, spanHttpCode: Int, throwable: Throwable? = null) {
        val span = spans[spanId]
        if (span != null) {
            throwable?.let { span.throwable = throwable }
            span.status = SpanStatus.fromHttpStatusCode(spanHttpCode)
        }
        span?.finish()
    }
}
