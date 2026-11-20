package tv.porch.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/** While an import writes programmes in batches, screens take at most one value per this long. */
const val IMPORT_PACE_MS = 500L

/**
 * Passes each value on at once, then waits [ms] before taking the next while [slow] is true.
 * The wait sits in the producer's emit, so a Room query upstream is not re-run per batch either.
 * Put a conflate() between a combine() and this so the wait never builds a backlog.
 */
fun <T> Flow<T>.pacedWhile(slow: () -> Boolean, ms: Long = IMPORT_PACE_MS): Flow<T> =
    transform { value ->
        emit(value)
        if (slow()) delay(ms)
    }
